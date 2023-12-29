package com.champion.utils

object common {


  case class NginxLogRecord(host: String, time: String,path:String, status: Int,content_size:Int,ts_all:Long)
  case class WindowResult(batchId: Int, endWindow: String, window: String, count: Long)

  case class UserLogRecord(host: String, time: String,path:String, status: Int,content_size:Int,ts_all:Long,user_id:String,mid_id:String,user_event:String,device_status:String,province:String,city:String)

  case class DeviceStatusCurrCnt(batchId: Int, endWindow: String,online_count:Int,offline_count:Int,warn_count:Int)

  case class UserStatusCurrCnt(batchId: Int, endWindow: String,follow_count:Int,reg_count:Int,order_count:Int,other_count:Int)

  case class DeviceAreaHisCnt(batchId: Int, endWindow: String,area:String,device_count:Int)

  case class OrderWindowCount(batchId: Int, endWindow: String,window:String,order_count:Int,cancel_count:Int)

  case class DeviceCityHisCnt(batchId: Int, endWindow: String,city:String,device_count:Int)

  case class OrderTtlWindowCount(batchId: Int, endWindow: String,window:String,follow_count:Int,order_count:Int,order_prop:Int)

}
