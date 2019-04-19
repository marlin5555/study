package com.sf.pis.akka.demo.main

import java.util.Date
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.sf.pis.akka.demo.actor.{BatchRootActor, LoadBatchActor, QueryActor}
import com.sf.pis.akka.demo.constant.Constant
import com.sf.pis.akka.demo.pattern._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigFactory._
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn
import scala.util.{Failure, Success}

object Main extends App {
  val config = ConfigFactory.load("myapp")
  println(config)
  val system: ActorSystem = ActorSystem(Constant.rootItem,config)

  val loadIndex:String = "pis_naga_transport"
  val cluster:String = "10.202.116.33:9200,10.202.116.34:9200"
  val batchs:ActorRef = system.actorOf(BatchRootActor.props, Constant.batchItem)

  val query:ActorRef = system.actorOf(QueryActor.props,Constant.queryItem)

  val load:ActorRef = system.actorOf(LoadBatchActor.props(loadIndex, cluster),Constant.loadItem)

  println("init ok")

  load ! LoadTick

  Thread.sleep(12000)

  do{
    val line:String = StdIn.readLine()
    if(line=="exit") {
      system.terminate()
      System.exit(1)
    } else if(line == "fetch"){
      query ! Fetch
    } else if(line.startsWith("fetch")){
      implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
      system.actorSelection(Constant.pathBatch+Constant.Separator+line.substring(5).trim).resolveOne().onComplete {
        case Success(actorRef) => actorRef ! Fetch
        case Failure(e) => e.printStackTrace()
      }
    } else if(line == "test"){
      load!TestTo
    } else if(line == "a"){
      query!Analyze
    }else{
      val data = line.split(" ")
      if(data.length >= 4){
        var dt = new DateTime()
        if(data.length>4) dt = dt.withHourOfDay(data(4).toInt)
        query!Query(data(0), dt.toDate, data(1),dt.plusHours(Integer.valueOf(data(2))).toDate, Integer.valueOf(data(3)))
      } else{
        println("standard input: <FROM> <TO> <TIME> <DEEP>")
      }
    }
  }while(true)
}
