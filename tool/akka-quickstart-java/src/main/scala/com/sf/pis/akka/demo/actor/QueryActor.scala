package com.sf.pis.akka.demo.actor

import java.util.{Calendar, Date}
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.sf.pis.akka.demo.constant.Constant
import com.sf.pis.akka.demo.pattern._
import org.joda.time.DateTime

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object QueryActor {
  def props: Props = Props[QueryActor]
}

class QueryActor extends Actor with ActorLogging{
  val result:mutable.Map[Query,Tuple2[Long,List[Spider]]] = mutable.Map.empty
  val zc2actor:mutable.Map[String, ActorRef] = mutable.Map.empty
  override def receive: Receive = {
    case Query(fZone:String,fDate:Date,tZone:String,maxDt,deepest:Int)=>
      log.info(s"query: from = $fZone, to = $tZone ")
      implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
      this.context.actorSelection(Constant.pathBatch+Constant.Separator+fZone).resolveOne().onComplete {
        case Success(actorRef) => actorRef ! Spider(Query(fZone,fDate,tZone,maxDt,deepest),fDate,toDest = false,List.empty)
        case Failure(e) => e.printStackTrace()
      }
    case s:Spider=>{

      if(!result.contains(s.query)) result.put(s.query,(System.currentTimeMillis(),List.empty))
      result.put(s.query, (System.currentTimeMillis(),result(s.query)._2.+:(s)))
      log.info(s"length=${s.path.size},cost=${(s.landDt.getTime-s.query.fDate.getTime)/1000/60/60},$s")
//      if(result.nonEmpty && result(s.query)._2.nonEmpty && result(s.query)._2.size == 100){
//        log.info("" + result(s.query)._2.foldLeft("")((a,b)=>a+"\ncost = "+(b.landDt.getTime-b.query.fDate.getTime)/1000/60/60 +", size = "+ b.path.size+", path = "+b.path))
//      }
      s.path.foreach(f=>{
        zc2actor(f.takegoodsZoneCodeBelong) ! Slow(s)
      })
    }
    case Fetch=>{
      result.foreach(e=>{
        log.info(s"query = ${e._1}, cost = ${(e._2._1-e._1.fDate.getTime)}, value.size = ${e._2._2.size}")
//        log.info("value = " + e._2.sortBy(s=>s.landDt).fold("")((a,b)=> a+"\n"+b))
      })

//      result.clear()
    }

    case Analyze=>
      result.foreach(e=>
        e._2._2.map(s=>(s.path.size,s))
          .groupBy(_._1)
          .toList
          .sortBy(_._1)
          .map(e=>(e._1,e._2.minBy(_._2.landDt)._2))
          .foreach(e=>
        log.info(s"length=${e._1},cost=${(e._2.landDt.getTime-e._2.query.fDate.getTime)/1000/60/60}, spider=${e._2}"))
      )
    case Touch(zc)=>
      zc2actor.put(zc,sender())
  }
}
