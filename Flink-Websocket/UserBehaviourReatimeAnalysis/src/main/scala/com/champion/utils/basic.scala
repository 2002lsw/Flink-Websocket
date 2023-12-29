package com.champion.utils

import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter

class basic {


  def parseClfTime(s: String): String = {
    val monthMap = Map(
      "Jan" -> 1, "Feb" -> 2, "Mar" -> 3, "Apr" -> 4, "May" -> 5, "Jun" -> 6,
      "Jul" -> 7, "Aug" -> 8, "Sep" -> 9, "Oct" -> 10, "Nov" -> 11, "Dec" -> 12
    )



    val year = s.substring(7, 11).toInt
    val month = monthMap(s.substring(3, 6))
    val day = s.substring(0, 2).toInt
    val hour = s.substring(12, 14).toInt
    val minute = s.substring(15, 17).toInt
    val second = s.substring(18, 20).toInt



    f"$year%04d-$month%02d-$day%02d $hour%02d:$minute%02d:$second%02d"
  }
  def coverStringTimeToLong(time:String): Long ={
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val parse = LocalDateTime.parse(time, format)
    //getEpochSecond精确到秒，toEpochMilli精确到毫秒
    LocalDateTime.from(parse).atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
  }


}
