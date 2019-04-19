package com.sf.pis.akka.demo.pattern

import java.util.Date

import com.sf.pis.akka.demo.model.Capacity

case object LoadTick

case object Fetch

case object CapacityBroadcast

case object TestTo

case object Analyze

case object Info

final case class Touch(from:String)

final case class Hello(who: String)

final case class ToCapacity(to:List[Capacity])

final case class FromCapacity(from:List[Capacity])

final case class Query(fZone:String, fDate:Date, tZone:String, maxDt:Date, deepest:Int)

final case class Spider(query: Query, landDt:Date, toDest:Boolean, path:List[Flight])

final case class Flight(lineCode:String, cvyName:String,
                        loadZoneCodeBelong:String,takegoodsZoneCodeBelong:String,
                        sendTm:Date,arriveTm:Date)

final case class ZoneInitACK(curZC:String)

final case class Slow(spider: Spider)