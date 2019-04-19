package com.sf.pis.akka.demo.actor

import java.io._
import java.util
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.alibaba.fastjson.{JSON, JSONObject}
import com.sf.pis.akka.demo.constant.Constant
import com.sf.pis.akka.demo.model.Capacity
import com.sf.pis.akka.demo.pattern.{CapacityBroadcast, LoadTick, TestTo, ZoneInitACK}
import com.sf.pis.akka.demo.{ElasticsearchService, EsRestClient, Load, LoadTransport}
import org.elasticsearch.action.search._
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.search.Scroll
import org.joda.time.DateTime

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.io.{Codec, Source}
import scala.util.parsing.json.JSONObject
import scala.util.{Failure, Success}

object LoadBatchActor {
  def props(path:String, cluster:String): Props = Props(new LoadBatchActor(path,cluster))
}
class LoadBatchActor(index:String, cluster:String) extends Actor with ActorLogging{

  val that = this
  var batches:ActorRef = _

  var zcSet:mutable.Set[String] = mutable.Set.empty
  var zcSet1:mutable.Set[String] = mutable.Set.empty

  implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
  this.context.actorSelection(Constant.pathBatch).resolveOne().onComplete {
    case Success(actorRef) => batches = actorRef
    case Failure(e) => e.printStackTrace()
  }

  /**
    * 使用scroll方法读取数据
    *
    * @param restClient
    * @param request
    * @param load
    * @param scroll
    * @param batchMap
    */
  private def loadDataCore[T](restClient: RestHighLevelClient, request: SearchRequest, load: Load[T], scroll: Scroll, batchMap: util.List[T]): Unit = {
    try {
      var response = restClient.search(request)
      var searchHits = response.getHits.getHits
      var scrollId = response.getScrollId
      load.load(searchHits, batchMap)
      while ( searchHits != null && searchHits.length > 0 ) {
        val scrollRequest = new SearchScrollRequest(scrollId)
        scrollRequest.scroll(scroll)
        response = restClient.searchScroll(scrollRequest)
        scrollId = response.getScrollId
        searchHits = response.getHits.getHits
        load.load(searchHits, batchMap)
      }
      val clearScrollRequest = new ClearScrollRequest
      clearScrollRequest.addScrollId(scrollId)
      val clearScrollResponse = restClient.clearScroll(clearScrollRequest)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }

  private def zipper[T1,T2](map1: Map[String, T1], map2: Map[String, T2])= {
    for(key <- map1.keys ++ map2.keys) yield (key, map1.get(key), map2.get(key))
  }

  private def group(list:util.ArrayList[Capacity])={
    val a = list.toArray(new Array[Capacity](list.size())).toList.distinct
    val sendTo = a.groupBy(i=>i.getLoadZoneCodeBelong)
    val arriveFrom = a.groupBy(i=>i.getTakegoodsZoneCodeBelong)
    zipper(sendTo,arriveFrom)
  }

  private def loadData(restClient: RestHighLevelClient, index:String)={
    log.info("start to read batch data form es ===========")
    val field = "versiondt"
    val dateTime = new DateTime
//    val time = dateTime.withTimeAtStartOfDay.getMillis
    val time = 1553702400000L
    val capacities = new util.ArrayList[Capacity]

    val service = new ElasticsearchService
    val scroll = new Scroll(TimeValue.timeValueMinutes(5L))
    val transportRequest = service.searchScroll(index, index, service.buildPlane(field, time), scroll)
    loadDataCore(restClient, transportRequest, new LoadTransport, scroll, capacities)
    log.info(s"=============counter = ${LoadTransport.count}")
    log.info(index + " load completed!!!!")
    log.info("load_batch_data_size batchMap:" + capacities.size)
    log.info("load_batch_data from es complete!!!")
    log.info("start update the batch_data======")
    log.info("update the batch_data complete!!!!!")
    group(capacities)
  }
  private def loadFromEs() = {
    val client = new EsRestClient(cluster)
    loadData(client.getRestClient,index)
  }

  private def saveToResources(data:Iterable[(String,Option[List[Capacity]],Option[List[Capacity]])]): Unit ={
    val f = new File("E:\\sf-helper\\akka-quickstart-java\\src\\main\\resources\\datas.byte")
    val bytes:String = data.foldLeft("")((a,b)=>a+"\n"+b)
    val bos = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),"utf-8"))
    bos.write(bytes.toCharArray)
    bos.close() // You may end up with 0 bytes file if not calling close.
  }

  private def loadFromResources(f:File) ={
    println("loadFromResources")
    val p = Pattern.compile("\\(([\\w]*)\\,(.*)\\)$")
    Source.fromFile(f).getLines().foreach(
      line=>{
        if(!line.isEmpty){
//          println(line)
          val m = p.matcher(line)
          m.find()
          val zc = m.group(1)
          val ls = m.group(2)
          println(m.groupCount())
          println("zc = "+zc)
          println("ls = "+ls)
        }
      }
      )

//    for( i<- Source.fromFile(f)(Codec("ISO-8859-1")).getLines()){
//      println(i)
//    }
  }

  override def receive: Receive = {
    case LoadTick =>{
      var datas:Iterable[(String,Option[List[Capacity]],Option[List[Capacity]])] = null
      val url = that.getClass.getResource("/datas.byte")
      if(url!=null){
        val f = new File(url.getFile)
        loadFromResources(f)
      }else{
        log.info("load tick, path = "+index)
        datas = loadFromEs()
        saveToResources(datas)
      }
//      zcSet.++=(datas.map(e=>e._1))
//      log.info(s"zcSet = $zcSet")
//      datas.foreach(e=>{
//        val zone = e._1
//        if(e._2.isEmpty) log.info(zone+", send to is null")
//        if(e._3.isEmpty) log.info(zone+", arrive from is null")
//        batches ! e
//      })
    }
    case ack:ZoneInitACK=>{
      log.info("ack. zcSet.size = "+zcSet.size)
      zcSet.remove(ack.curZC)
      zcSet1.+=(ack.curZC)
      if(zcSet.isEmpty){
        zcSet1.foreach(zc=>{
          this.context.actorSelection(Constant.pathBatch+Constant.Separator+zc).resolveOne().onComplete {
            case Success(actorRef) =>
              log.info("fetchAllZone, current = "+actorRef)
              actorRef!CapacityBroadcast
            case Failure(e) => e.printStackTrace()
          }
        })
      }
    }
    case TestTo=>{
      zcSet1.foreach(zc=>{
        this.context.actorSelection(Constant.pathBatch+Constant.Separator+zc).resolveOne().onComplete {
          case Success(actorRef) =>
            actorRef!TestTo
          case Failure(e) => e.printStackTrace()
        }
      })
    }
  }
}
