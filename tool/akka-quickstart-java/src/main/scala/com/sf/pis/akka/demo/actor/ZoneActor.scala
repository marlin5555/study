package com.sf.pis.akka.demo.actor

import java.util.Date
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.dispatch.{RequiresMessageQueue, UnboundedMessageQueueSemantics}
import akka.util.Timeout
import com.sf.pis.akka.demo.constant.{Constant, Util}
import com.sf.pis.akka.demo.mailbox.MyPrioMailbox
import com.sf.pis.akka.demo.model.Capacity
import com.sf.pis.akka.demo.pattern._
import com.sf.pis.akka.demo.util.LRU

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object ZoneActor {
  def props(zc:String): Props = Props(new ZoneActor(zc))
}

class ZoneActor(zc:String) extends Actor with ActorLogging {
  var toCapacity:List[Capacity] = _
  var fromCapacity:List[Capacity] = _
  val that = this
  var queryActor:ActorRef = _
  val qHistory:LRU[Query,LRU[String,(Int,Long)]]=new LRU[Query,LRU[String,(Int,Long)]](100)

  val q2Arrive:mutable.Map[Query,Date] = mutable.Map.empty

  implicit val timeout = Timeout(FiniteDuration(5, TimeUnit.SECONDS))
  this.context.actorSelection(Constant.pathQuery).resolveOne().onComplete {
    case Success(actorRef) => queryActor = actorRef
    case Failure(e) => e.printStackTrace()
  }

  val zc2actor:mutable.Map[String, ActorRef] = mutable.Map.empty

  def mergeCapacity(ls:List[Capacity]): List[Capacity] ={
    val flight2workDay:mutable.Map[String,String] = mutable.Map.empty
    ls.foreach(c=>{
      val k = c.getCvyName+"^"+c.getLoadZoneCodeBelong+"^"+c.getTakegoodsZoneCodeBelong+"^"+c.getPlanSendTm+"^"+c.getPlanArriveTm
      if(flight2workDay.contains(k)){

        flight2workDay(k) = (flight2workDay(k)+c.getWorkDays).distinct
      }else{
        flight2workDay(k) = c.getWorkDays
      }
    })
    ls.foreach(c=>{
      val k = c.getCvyName+"^"+c.getLoadZoneCodeBelong+"^"+c.getTakegoodsZoneCodeBelong+"^"+c.getPlanSendTm+"^"+c.getPlanArriveTm
      c.setWorkDays(flight2workDay(k))
    })
    ls.distinct
  }

  def processOneDest(query:Query, f:Flight, path:List[Flight]): Unit ={
    val nextZC = f.takegoodsZoneCodeBelong
    if(zc2actor.isDefinedAt(nextZC)) zc2actor(nextZC) ! Spider(query,f.arriveTm,toDest = false,path.:+(f))
    else {
      log.error(s"query:$query, f:$f, zc2actor:$zc2actor")
    }
  }

  override def receive: Receive = {
    case Hello(x)=> {
      log.info(s"i got x = $x")
      sender()!ZoneInitACK(zc)
    }
    case ToCapacity(to) => toCapacity = mergeCapacity(to)
    case FromCapacity(from) => fromCapacity = mergeCapacity(from)
    case Spider(query:Query, landDt,toDest,path)=>
//      log.info(s"spider: query=$query, landDt=$landDt, toDest=$toDest, path=$path")
      if(query.tZone == zc){ // 如果目的地网点与当前网点是同一个网点
        val r = Spider(query:Query, landDt,toDest = true,path)
        if(queryActor!=null) queryActor ! r else log.error("queryActor is null, spider = " + r)
      }else{
        if(path.size >= query.deepest){
//          log.info("too deep, spider = " + path)
        }else if(q2Arrive.contains(query) && q2Arrive(query).before(landDt)){
//            log.info(s"arrive can to dest, q2arrive = $q2Arrive, landDt = $landDt, path = $path")
        }else{
          val remain = query.deepest - path.size

          if(path.nonEmpty){
            val l = path.last
            if(!qHistory.containsKey(query))qHistory.put(query,new LRU[String,(Int,Long)](10000))
            val lru = qHistory.get(query)
            val key =  "^" + (l.arriveTm.getTime/1000/60/60/24)
//            val key = l.lineCode + "^" + l.cvyName + "^" + l.arriveTm.getMonth + l.arriveTm.getDate
            if(!lru.containsKey(key)) lru.put(key, (remain,l.arriveTm.getTime))
            if(lru.get(key)._1 > remain && lru.get(key)._2 > l.arriveTm.getTime){
//              log.info(s"before visit, from = $key, lru.remain=${lru.get(key)}")
              // 如果lru中保存着本网点访被访问过，且经过更少跳数被访问到了，则本次访问不再产生消息广播
            }else{
              lru.put(key,(remain,l.arriveTm.getTime))
              if(toCapacity!=null){
                val reachNodes = path.map(f=>f.takegoodsZoneCodeBelong).toSet
                toCapacity.filter(c=> !reachNodes.contains(c.getTakegoodsZoneCodeBelong))// 过滤掉已经串联的网点【不走回头路】
                  .map(c=>Util.getFlight(c, landDt)) // 将capacity转换成可用的航班
                  .filter(f=>f.arriveTm.before(query.maxDt))// 过滤航班，确保航班到达时间不超过给定的阈值
                  .groupBy(f=>f.takegoodsZoneCodeBelong) // 按照目的地进行分组
                  .foreach(_._2.sortBy(f1 => f1.arriveTm) // 只串联最早到达目的地的航班
                  .slice(0, 1)
                  .foreach(f2 => processOneDest(query, f2, path)))
              }
            }
          }else {
            if(toCapacity!=null){
              val reachNodes = path.map(f=>f.takegoodsZoneCodeBelong).toSet
              toCapacity.filter(c=> !reachNodes.contains(c.getTakegoodsZoneCodeBelong))// 过滤掉已经串联的网点【不走回头路】
                .map(c=>Util.getFlight(c, landDt)) // 将capacity转换成可用的航班
                .filter(f=>f.arriveTm.before(query.maxDt))// 过滤航班，确保航班到达时间不超过给定的阈值
                .groupBy(f=>f.takegoodsZoneCodeBelong) // 按照目的地进行分组
                .foreach(_._2.sortBy(f1 => f1.arriveTm) // 只串联最早到达目的地的航班
                .slice(0, 1)
                .foreach(f2 => processOneDest(query, f2, path)))
            }
          }
        }
      }
    case Fetch=>
      log.info("toCapacity"+toCapacity.fold("")((a,b)=>a+"\n"+b))
      log.info("fromCapacity"+fromCapacity.fold("")((a,b)=>a+"\n"+b))
      log.info("zc2actor"+zc2actor.fold("")((a,b)=>a+"\n"+b))
    case CapacityBroadcast=>
      if(fromCapacity!=null)
        fromCapacity.map(c=>c.getLoadZoneCodeBelong).distinct.foreach(fromZC=>{
          this.context.actorSelection(Constant.pathBatch+Constant.Separator+fromZC).resolveOne().onComplete {
            case Success(actorRef) =>
              actorRef!Touch(zc)
            case Failure(e) => e.printStackTrace()
          }
        })

      this.context.actorSelection(Constant.pathQuery).resolveOne().onComplete {
        case Success(actorRef) =>
          actorRef!Touch(zc)
        case Failure(e) => e.printStackTrace()
      }
    case Touch(to:String)=>
      zc2actor(to) = sender()
      log.info(s"from=$to, actor=${zc2actor(to)}, actor size = ${zc2actor.size}")
    case TestTo=>
      if(toCapacity!=null)
        toCapacity.foreach(f=>{
          if(!zc2actor.isDefinedAt(f.getTakegoodsZoneCodeBelong)){
            log.error(s"f:$f,zc2actor:"+zc2actor.fold("")((a,b)=>a+"\n"+b))
          }
        })
    case Info=>{
      self.toString()
    }
    case Slow(s)=>
      s.path.foreach(f = f => {
        if (f.takegoodsZoneCodeBelong == zc) {
          if (q2Arrive.contains(s.query)) {
            if (q2Arrive(s.query).after(f.arriveTm)) q2Arrive(s.query) = f.arriveTm
          } else {
            q2Arrive(s.query) = f.arriveTm
          }
        }
      })
  }
}
