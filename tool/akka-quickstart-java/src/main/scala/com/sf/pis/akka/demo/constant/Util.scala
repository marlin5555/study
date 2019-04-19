package com.sf.pis.akka.demo.constant

import java.util.{Calendar, Date}

import com.sf.pis.akka.demo.model.Capacity
import com.sf.pis.akka.demo.pattern.Flight
import org.joda.time.DateTime

object Util {



  def findSendDay(workDay:String, planSendTm:String, dt:DateTime): DateTime ={
    var tempDt = dt
    while(true){
      val hhmm = tempDt.getHourOfDay + "" + tempDt.getMinuteOfHour
      val day = tempDt.getDayOfWeek + ""

      if(hhmm <= planSendTm && workDay.contains(day)){
        return tempDt
      }else tempDt = tempDt.plusDays(1)
        .withHourOfDay(0)
        .withMinuteOfHour(0)
    }
    dt
  }

  def getAdjustTm(day:DateTime, planTm:String , crossDay:Int = 0):Date= {
    day.plusDays(crossDay)
      .withHourOfDay(Integer.valueOf(planTm.substring(0, 2)))
      .withMinuteOfHour(Integer.valueOf(planTm.substring(2, 4)))
      .toDate
  }


  def getFlight(capacity: Capacity, t:Date, airportMinTime:Int = 2): Flight ={
    val dt = new DateTime(t.getTime).plusHours(airportMinTime)
    val day = findSendDay(capacity.getWorkDays,capacity.getPlanSendTm,dt)
    Flight(capacity.getLineCode,capacity.getCvyName,
      capacity.getLoadZoneCodeBelong,capacity.getTakegoodsZoneCodeBelong,
      getAdjustTm(day,capacity.getPlanSendTm),
      getAdjustTm(day,capacity.getPlanArriveTm, Integer.valueOf(capacity.getCrossDay)))
  }
}
