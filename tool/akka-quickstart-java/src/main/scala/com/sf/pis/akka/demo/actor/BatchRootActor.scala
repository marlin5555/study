package com.sf.pis.akka.demo.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.sf.pis.akka.demo.model.Capacity
import com.sf.pis.akka.demo.pattern.{FromCapacity, Hello, ToCapacity, ZoneInitACK}

object BatchRootActor {
  def props: Props = Props[BatchRootActor]
}

class BatchRootActor extends Actor with ActorLogging{
  var i = 0
  var loader:ActorRef = _
  override def receive: Receive = {
    case (zc:String,to:Option[List[Capacity]],from:Option[List[Capacity]]) =>{
      val zoneActor:ActorRef = this.context.actorOf(ZoneActor.props(zc).withDispatcher("prio-dispatcher"), zc)

      zoneActor ! Hello(s"$i")
      if(to.isDefined) zoneActor ! ToCapacity(to.get)
      if(from.isDefined) zoneActor ! FromCapacity(from.get)

      i = i+1
      if(loader == null) loader = sender()
    }
    case ack:ZoneInitACK=>{
      loader!ack
    }
  }
}
