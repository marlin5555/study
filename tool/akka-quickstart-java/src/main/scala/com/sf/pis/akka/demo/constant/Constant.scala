package com.sf.pis.akka.demo.constant

import java.io.File

object Constant {
  val rootItem:String = "root"
  val Separator:String = "/"
  val pathRoot:String = "akka://"+rootItem+Separator+"user"

  val loadItem:String = "load"
  val pathLoad:String = pathRoot+Separator+loadItem

  val batchItem:String = "batch"
  val pathBatch:String = pathRoot+Separator+batchItem

  val queryItem:String = "query"
  val pathQuery:String = pathRoot+Separator+queryItem

  val pathAllZone:String = pathRoot+Separator+batchItem+Separator+"*"
}
