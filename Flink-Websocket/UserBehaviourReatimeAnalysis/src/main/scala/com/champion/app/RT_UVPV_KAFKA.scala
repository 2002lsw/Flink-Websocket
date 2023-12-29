package com.champion.app

import java.sql.{Connection, Date, DriverManager, PreparedStatement}
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import java.util.Properties

import com.champion.utils.MyKafkaUtil
import com.champion.utils.common.{DeviceAreaHisCnt, _}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.api.common.serialization.{SerializationSchema, SimpleStringSchema}
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.scala.function.{ProcessAllWindowFunction, ProcessWindowFunction}
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.{ContinuousProcessingTimeTrigger, Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}
import org.apache.flink.util.Collector

//import scala.concurrent.duration.Duration

// This version is data direct to Kafka for websocket Version


object RT_UVPV_KAFKA {



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

  class CustomWindowFunction extends ProcessAllWindowFunction[NginxLogRecord, WindowResult, TimeWindow] {


    override def process( context: Context, elements: Iterable[NginxLogRecord], out: Collector[WindowResult]): Unit = {
      val batchId = 74 // 批次ID，根据实际情况进行设置
      val endWindow = context.window.getEnd
      val window = s"[${context.window.getStart}, ${context.window.getEnd}]"


      val count = elements.size



      out.collect(WindowResult(batchId, formatTimestamp(endWindow), window, count))

      //val currentWatermark = context.timerService().currentWatermark()

       }

    private def formatTimestamp(timestamp: Long): String = {
      // 根据需要的时间格式进行适当的格式化
      // 示例中使用的是默认的格式，您可以根据实际情况进行调整
      val format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      val instant = java.time.Instant.ofEpochMilli(timestamp)
      val localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
      localDateTime.format(format)
    }
  }


  class CountAggregator extends AggregateFunction[NginxLogRecord, (String, Long), (String, Long)] {
    override def createAccumulator(): (String, Long) = (null, 0L)

    override def add(value: NginxLogRecord, accumulator: (String, Long)): (String, Long) = {
      val count = accumulator._2 + 1
      (value.host, count)
    }

    override def getResult(accumulator: (String, Long)): (String, Long) = accumulator

    override def merge(a: (String, Long), b: (String, Long)): (String, Long) = {
      val count = a._2 + b._2
      (a._1, count)
    }
  }

  class CustomTrigger(interval: Long) extends Trigger[NginxLogRecord, TimeWindow] {

    override def onElement(element: NginxLogRecord, timestamp: Long, window: TimeWindow, ctx: Trigger.TriggerContext): TriggerResult = {
      // 设置下一个触发时间
      val currentTimestamp = ctx.getCurrentProcessingTime
      val nextTriggerTimestamp = (currentTimestamp / interval + 1) * interval
      ctx.registerProcessingTimeTimer(nextTriggerTimestamp)
      TriggerResult.FIRE
    }

    override def onProcessingTime(time: Long, window: TimeWindow, ctx: Trigger.TriggerContext): TriggerResult = {
      // 触发计算，但不清除窗口数据
      TriggerResult.PURGE
    }

    override def onEventTime(time: Long, window: TimeWindow, ctx: Trigger.TriggerContext): TriggerResult = {
      TriggerResult.CONTINUE
    }

    override def clear(window: TimeWindow, ctx: Trigger.TriggerContext): Unit = {
      ctx.deleteProcessingTimeTimer(window.maxTimestamp())
    }
  }

  class MySQLSink[T](table_name:String) extends SinkFunction[T] {
    // 定义MySQL连接信息
    val url = "jdbc:mysql://10.195.5.11:3306/biprod_realtime?useSSL=false&useUnicode=true&characterEncoding=UTF-8"
    val username = "root"
    val password = "WN4TNLov#g"

    override def invoke(value: T, context: SinkFunction.Context): Unit = {
      var connection: Connection = null
      var preparedStatement: PreparedStatement = null



      try {
        // 建立MySQL连接
        connection = DriverManager.getConnection(url, username, password)
        if (table_name=="t_device") {
          val deviceStatus = value.asInstanceOf[DeviceStatusCurrCnt]

          // 执行插入操作
          val insertQuery = "INSERT INTO t_device (batch_id,endwindow,ttl_count,online_count,offline_count,warn_count) VALUES (?,?,?,?,?,?)"
          preparedStatement = connection.prepareStatement(insertQuery)
          preparedStatement.setInt(1, deviceStatus.batchId)
          preparedStatement.setString(2, deviceStatus.endWindow)
          preparedStatement.setLong(3, deviceStatus.online_count + deviceStatus.warn_count + deviceStatus.offline_count)
          preparedStatement.setLong(4, deviceStatus.online_count)
          preparedStatement.setLong(5, deviceStatus.offline_count)
          preparedStatement.setLong(6, deviceStatus.warn_count)
          preparedStatement.executeUpdate()
        }
        else if (table_name=="t_user") {

          val userStatus = value.asInstanceOf[UserStatusCurrCnt]

          // 执行插入操作
          val insertQuery = "INSERT INTO t_user (batch_id,endwindow,ttl_count,follow_count,reg_count,order_count,common_count) VALUES (?,?,?,?,?,?,?)"
          preparedStatement = connection.prepareStatement(insertQuery)
          preparedStatement.setInt(1, userStatus.batchId)
          preparedStatement.setString(2, userStatus.endWindow)
          preparedStatement.setLong(3, userStatus.follow_count + userStatus.order_count + userStatus.reg_count+userStatus.other_count)
          preparedStatement.setLong(4, userStatus.follow_count)
          preparedStatement.setLong(5, userStatus.reg_count)
          preparedStatement.setLong(6, userStatus.order_count)
          preparedStatement.setLong(7, userStatus.other_count)
          preparedStatement.executeUpdate()
        }
        else if (table_name=="t_device_area"){
          val devicearea = value.asInstanceOf[DeviceAreaHisCnt]

          // 执行插入操作
          val insertQuery = "INSERT INTO t_device_area (batch_id,endwindow,area,device_count) VALUES (?,?,?,?)"
          preparedStatement = connection.prepareStatement(insertQuery)
          preparedStatement.setInt(1, devicearea.batchId)
          preparedStatement.setString(2, devicearea.endWindow)
          preparedStatement.setString(3, devicearea.area)
          preparedStatement.setLong(4, devicearea.device_count)

          preparedStatement.executeUpdate()


        }

        else if (table_name=="t_order_count"){
          val ordercount = value.asInstanceOf[OrderWindowCount]

          // 执行插入操作
          val insertQuery = "INSERT INTO t_order_count (batch_id,endwindow,window,count,cancel_count) VALUES (?,?,?,?,?)"
          preparedStatement = connection.prepareStatement(insertQuery)
          preparedStatement.setInt(1, ordercount.batchId)
          preparedStatement.setString(2, ordercount.endWindow)
          preparedStatement.setString(3, ordercount.window)
          preparedStatement.setLong(4, ordercount.order_count)
          preparedStatement.setLong(5, ordercount.cancel_count)

          preparedStatement.executeUpdate()


        }
        else if (table_name=="t_order_city"){
          val ordercount = value.asInstanceOf[DeviceCityHisCnt]

          // 执行插入操作
          val insertQuery = "INSERT INTO t_order_city (batch_id,process_ts,city,order_count) VALUES (?,?,?,?)"
          preparedStatement = connection.prepareStatement(insertQuery)
          preparedStatement.setInt(1, ordercount.batchId)
          preparedStatement.setString(2, ordercount.endWindow)
          preparedStatement.setString(3, ordercount.city)
          preparedStatement.setLong(4, ordercount.device_count)

          preparedStatement.executeUpdate()


        }

        else if (table_name=="t_order_ttl"){
          val orderttlcount = value.asInstanceOf[OrderTtlWindowCount]

          // 执行插入操作
          val insertQuery = "INSERT INTO t_order_ttl (batch_id,endwindow,window,follow_count,order_count,order_prop) VALUES (?,?,?,?,?,?)"
          preparedStatement = connection.prepareStatement(insertQuery)
          preparedStatement.setInt(1, orderttlcount.batchId)
          preparedStatement.setString(2, orderttlcount.endWindow)
          preparedStatement.setString(3, orderttlcount.window)
          preparedStatement.setLong(4, orderttlcount.follow_count)
          preparedStatement.setLong(5, orderttlcount.order_count)
          preparedStatement.setInt(6, orderttlcount.order_prop)

          preparedStatement.executeUpdate()


        }


      } catch {
        case e: Exception => e.printStackTrace()
      } finally {
        // 关闭资源
        if (preparedStatement != null) {
          preparedStatement.close()
        }
        if (connection != null) {
          connection.close()
        }
      }
    }
  }

  def main(args: Array[String]): Unit = {




    val env = StreamExecutionEnvironment.getExecutionEnvironment

    // val sub_topic =args(0)

    val sub_topic="userlogtest"

    val kafkaProps = new Properties()
   // kafkaProps.setProperty("bootstrap.servers", "10.16.105.252:9092,10.16.44.134:9092,10.16.11.161:9092")
    kafkaProps.setProperty("bootstrap.servers", "192.168.1.181:9092,192.168.1.182:9092,192.168.1.189:9092")
    //kafkaProps.setProperty("group.id", "flink-consumer-group")
    kafkaProps.setProperty("auto.offset.reset", "latest")

    val kafkaConsumer = new FlinkKafkaConsumer[String](sub_topic, new SimpleStringSchema(), kafkaProps)
  try{
    val originkafkaStream = env.addSource(kafkaConsumer).map(
      line =>{

        val ttl_pattern ="^([^\\s]+)\\s- - \\[(\\d\\d/\\w{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2} [\\+\\-]\\d{4})] \"\\w+\\s+([^\\s]+)\\s+HTTP.*\" \\d{3} \\d{1,}".r
        val ttl_matcher = ttl_pattern.findFirstMatchIn(line)
        val flag=ttl_matcher.isEmpty

if(!flag) {
  val hostPattern = "^([^\\s]+)\\s".r

  val hostMatcher = hostPattern.findFirstMatchIn(line)
  val host = hostMatcher.map(_.group(1)).getOrElse("")
  val tsPattern = "^.*\\[(\\d\\d/\\w{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2} [\\+\\-]\\d{4})]".r
  val tsMatcher = tsPattern.findFirstMatchIn(line)
  val timestamp = tsMatcher.map(_.group(1)).getOrElse("")

  val ts = parseClfTime(timestamp)


  val timestamp2 = coverStringTimeToLong(ts)


  val pathPattern = "^.*\"\\w+\\s+([^\\s]+)\\s+HTTP.*\"".r
  val pathMatcher = pathPattern.findFirstMatchIn(line)
  val path = pathMatcher.map(_.group(1)).getOrElse("")


  val statusPattern = "^.*\"\\s+([^\\s]+)".r
  val statusMatcher = statusPattern.findFirstMatchIn(line)
  val status = statusMatcher.map(_.group(1)).getOrElse("")

  val contentSizePattern = "^.*\\s+(\\d+)".r
  val contentSizeMatcher = contentSizePattern.findFirstMatchIn(line)
  val contentSize = contentSizeMatcher.map(_.group(1)).getOrElse("")


  val deviceidPattern = "^.*(mid_\\d{1,})".r
  val deviceidMatcher = deviceidPattern.findFirstMatchIn(line)
  val deviceid = deviceidMatcher.map(_.group(1)).getOrElse("")


  val useridPattern = "^.*(user_\\d{1,})".r
  val useridMatcher = useridPattern.findFirstMatchIn(line)
  val userid = useridMatcher.map(_.group(1)).getOrElse("")





  val usereventPattern = "^.*(order|follow|reg)".r
  val usereventMatcher = usereventPattern.findFirstMatchIn(line)
  val userevent = usereventMatcher.map(_.group(1)).getOrElse("")

  val deviceStatusPattern = "^.*(online|offline|warn)".r
  val deviceStatusMatcher = deviceStatusPattern.findFirstMatchIn(line)
  val deviceStatus = deviceStatusMatcher.map(_.group(1)).getOrElse("")

  val provincePattern = "([^\\x00-\\xff]{1,})".r
  val provinceMatcher = provincePattern.findFirstMatchIn(line)
  val province = provinceMatcher.map(_.group(1)).getOrElse("")


  val cityPattern = "([^\\x00-\\xff]+)$".r
  val cityMatcher = cityPattern.findFirstMatchIn(line)
  val city = cityMatcher.map(_.group(1)).getOrElse("")

  UserLogRecord(host, ts, path, status.toInt, contentSize.toInt, timestamp2,userid,deviceid,userevent,deviceStatus,province,city)

  //NginxLogRecord(host, ts, path, status.toInt, contentSize.toInt, timestamp2)
}
else {

  UserLogRecord("", "", "path", 0, 0,0,"","","","","","")

}

      }
    ).filter(_.status>0)

      .assignAscendingTimestamps(_.ts_all)


    //originkafkaStream.print()

    // 1. device status process

    val devicestatusStream = originkafkaStream.keyBy(_=>1)
      .window(TumblingEventTimeWindows.of(Time.days(1)))
      .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(15)))
        .process(new MyProcessWindowFunction())

    devicestatusStream.addSink(new FlinkKafkaProducer[String]("userlog-mid", new SimpleStringSchema(), kafkaProps))
// user status count
    val userstatusStream = originkafkaStream.keyBy(_=>1)
      .window(TumblingEventTimeWindows.of(Time.days(1)))
      .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(60)))
      .process(new MyUserStatusProcessWindowFunction())


    userstatusStream.addSink(new FlinkKafkaProducer[String]("userlog-mid", new SimpleStringSchema(), kafkaProps))

// device area by province
    val deviceAreaStream = originkafkaStream.keyBy(_=>1)
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
      .process(new MyDeviceAreaProcessWindowFunction())


    deviceAreaStream.addSink(new FlinkKafkaProducer[String]("userlog-mid", new SimpleStringSchema(), kafkaProps))


//device by city
    val deviceCityStream = originkafkaStream.keyBy(_=>1)
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
      .process(new MyDeviceCityProcessWindowFunction())

    deviceCityStream.addSink(new FlinkKafkaProducer[String]("userlog-mid", new SimpleStringSchema(), kafkaProps))







    env.execute("Kafka to MySQL")

  }
  catch
    {

      case e : Exception => e.printStackTrace
    }


  }




  class MyProcessWindowFunction extends ProcessWindowFunction[UserLogRecord, String, Int, TimeWindow] {
    private var deviceStatusState: ValueState[Set[String]] = _

    override def open(parameters: Configuration): Unit = {
      val deviceStatusStateDescriptor = new ValueStateDescriptor[Set[String]]("deviceStatusState", classOf[Set[String]])
      deviceStatusState = getRuntimeContext.getState(deviceStatusStateDescriptor)
    }
    override def process(key: Int, context: Context, elements: Iterable[UserLogRecord], out: Collector[String]): Unit = {
      val currentTime = System.currentTimeMillis()
      val (onlineCount, offlineCount, warnCount) = calculateStatusCounts(elements)


      val dateFormat = new SimpleDateFormat("mmss")
      val formattedTime = dateFormat.format(new Date(currentTime))

      val currentDeviceStatusState = Option(deviceStatusState.value()).getOrElse(Set())
      val deduplicatedElements = elements.filter(record => !currentDeviceStatusState.contains(record.mid_id))

      deduplicatedElements.foreach(record => deviceStatusState.update(currentDeviceStatusState + record.mid_id))

      val deviceStatusCurrCnt = DeviceStatusCurrCnt(1, formattedTime, onlineCount, offlineCount, warnCount)
      val ttl_count = onlineCount + offlineCount + warnCount

      val responseJson = s"""{
      "code": 0,
      "message": "查询成功",
      "data": {
           "endwindow": $formattedTime,
           "ttl_count":$ttl_count,

         "online_count": $onlineCount,
        "offline_count": $offlineCount,
        "warn_count": $warnCount
      }
    }"""

      val finalJson = s"""{"key": "device_count", "value": $responseJson}"""

      out.collect(finalJson)


    }

    def calculateStatusCounts(records: Iterable[UserLogRecord]): (Int, Int, Int) = {
      val counts = records.foldLeft((0, 0, 0)) {
        case ((onlineCount, offlineCount, warnCount), record) =>
          val deviceStatus = record.device_status
          deviceStatus match {
            case "online" => (onlineCount + 1, offlineCount, warnCount)
            case "offline" => (onlineCount, offlineCount + 1, warnCount)
            case "warn" => (onlineCount, offlineCount, warnCount + 1)
            case _ => (onlineCount, offlineCount, warnCount)
          }
      }
      counts
    }
  }

  class OrderCountWindowFunction extends ProcessAllWindowFunction[UserLogRecord, OrderWindowCount, TimeWindow] {


    override def process( context: Context, elements: Iterable[UserLogRecord], out: Collector[OrderWindowCount]): Unit = {
      val batchId = 74 // 批次ID，根据实际情况进行设置
      val endWindow = context.window.getEnd
      val window = s"[${context.window.getStart}, ${context.window.getEnd}]"
      val ts_mm = formatTimestamp(endWindow)

      val count = elements.size



      out.collect(OrderWindowCount(batchId, formatTimestamp(endWindow), window, count,0))

      //val currentWatermark = context.timerService().currentWatermark()

    }

    private def formatTimestamp(timestamp: Long): String = {
      // 根据需要的时间格式进行适当的格式化
      // 示例中使用的是默认的格式，您可以根据实际情况进行调整
      val format = java.time.format.DateTimeFormatter.ofPattern("HHmm")
      val instant = java.time.Instant.ofEpochMilli(timestamp)
      val localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
      localDateTime.format(format)
    }
  }


  class MyUserStatusProcessWindowFunction extends ProcessWindowFunction[UserLogRecord, String, Int, TimeWindow] {
    private var userStatusState: ValueState[Set[String]] = _

    override def open(parameters: Configuration): Unit = {
      val deviceStatusStateDescriptor = new ValueStateDescriptor[Set[String]]("userStatusState", classOf[Set[String]])
      userStatusState = getRuntimeContext.getState(deviceStatusStateDescriptor)
    }
    override def process(key: Int, context: Context, elements: Iterable[UserLogRecord], out: Collector[String]): Unit = {
      val currentTime = System.currentTimeMillis()
      val (follow_count,reg_count,order_count,other_count) = calculateStatusCounts(elements)




      val currentUserStatusState = Option(userStatusState.value()).getOrElse(Set())
      val deduplicatedElements = elements.filter(record => !currentUserStatusState.contains(record.mid_id))

      deduplicatedElements.foreach(record => userStatusState.update(currentUserStatusState + record.mid_id))

      val userStatusCurrCnt = UserStatusCurrCnt(1, currentTime.toString, follow_count,reg_count,order_count,other_count)
      val ttl_count = follow_count + reg_count + order_count + other_count

      val timewindow = currentTime.toString

      val responseJson = s"""{
      "code": 0,
      "message": "查询成功",
      "data": {
           "endwindow": $timewindow,
        "ttl_count": $ttl_count,
        "follow_count": $follow_count,
        "reg_count": $reg_count,
        "order_count": $order_count,
        "common_count": $other_count
      }
    }"""

      val finalJson = s"""{"key": "user_count", "value": $responseJson}"""

      out.collect(finalJson)



    }

    def calculateStatusCounts(records: Iterable[UserLogRecord]): (Int, Int, Int,Int) = {
      val counts = records.foldLeft((0, 0, 0,0)) {
        case ((follow_count,reg_count,order_count,other_count), record) =>
          val userStatus = record.user_event
          userStatus match {
            case "reg" => (follow_count, reg_count+1, order_count,other_count)
            case "order" => (follow_count, reg_count+1, order_count+1,other_count)
            case "follow" => (follow_count+1, reg_count+1, order_count,other_count)
            case "other" => (follow_count, reg_count+1, order_count,other_count+1)
            case _ => (follow_count,reg_count,order_count,other_count)
          }
      }
      counts
    }
  }


  class MyDeviceAreaProcessWindowFunction extends ProcessWindowFunction[UserLogRecord, String, Int, TimeWindow] {
    override def process(
                          key: Int,
                          context: Context,
                          elements: Iterable[UserLogRecord],
                          out: Collector[String]
                        ): Unit = {
      val currentTime = System.currentTimeMillis()
      val endWindow = formatTimestamp(currentTime)

      val provinceCounts = elements
        .groupBy(_.province)
        .map { case (province, records) =>
          val deviceCount = records.map(_.mid_id).toSet.size
          (province, deviceCount)
        }
        .toArray
        .sortWith(_._2 > _._2) // 按照"value"从高到低排序
        .take(11)
        .map { case (province, deviceCount) =>
          s"""{"name": "$province", "value": $deviceCount}""" // 修改字段名
        }

      val responseJson = s"""{
      "code": 0,
      "message": "查询成功",
      "data": {
          "regionCode": "china",
          "dataList": ${provinceCounts.mkString("[", ",", "]")}
      }
    }"""

      val finalJson = s"""{"key": "device_area", "value": $responseJson}"""

      out.collect(finalJson)
    }

    private def formatTimestamp(timestamp: Long): String = {
      val dateFormat = new SimpleDateFormat("mmss")
      dateFormat.format(new Date(timestamp))
    }
  }


  class MyDeviceCityProcessWindowFunction extends ProcessWindowFunction[UserLogRecord, String, Int, TimeWindow] {
    override def process(
                          key: Int,
                          context: Context,
                          elements: Iterable[UserLogRecord],
                          out: Collector[String]
                        ): Unit = {
      val currentTime = System.currentTimeMillis()



      val cityCounts = elements
        .groupBy(_.city)
        .map { case (city, records) =>
          val deviceCount = records.map(_.mid_id).toSet.size
          (city, deviceCount)
        }
        .toArray
        .sortWith(_._2 > _._2) // 按照"value"从高到低排序
        .take(8)
        .map { case (city, deviceCount) =>
          s"""{"name": "$city", "value": $deviceCount}""" // 修改字段名
        }

      val responseJson = s"""{
      "code": 0,
      "message": "查询成功",
      "data": ${cityCounts.mkString("[", ",", "]")}

    }"""

      val finalJson = s"""{"key": "device_city", "value": $responseJson}"""

      out.collect(finalJson)

     // cityCounts.foreach(out.collect)
    }

    private def formatTimestamp(timestamp: Long): String = {
      val dateFormat = new SimpleDateFormat("mmss")
      dateFormat.format(new Date(timestamp))
    }
  }


  class OrderTtlWindowCountProcess extends ProcessAllWindowFunction[UserLogRecord, OrderTtlWindowCount, TimeWindow] {


    override def process( context: Context, elements: Iterable[UserLogRecord], out: Collector[OrderTtlWindowCount]): Unit = {
      val batchId = 74 // 批次ID，根据实际情况进行设置
      val endWindow = context.window.getEnd
      val window = s"[${context.window.getStart}, ${context.window.getEnd}]"
      val ts_mm = formatTimestamp(endWindow)

      val count = elements.size
      val followCount = elements.count(_.user_event == "follow")
      val orderCount = elements.count(_.user_event == "order")
      val totalCount = elements.size
      val orderProp = if (totalCount > 0) Math.round(orderCount * 100.0 / totalCount).toInt else 0

      out.collect(OrderTtlWindowCount(batchId, formatTimestamp(endWindow), window, followCount, orderCount, orderProp))


      //val currentWatermark = context.timerService().currentWatermark()

    }

    private def formatTimestamp(timestamp: Long): String = {
      // 根据需要的时间格式进行适当的格式化
      // 示例中使用的是默认的格式，您可以根据实际情况进行调整
      val format = java.time.format.DateTimeFormatter.ofPattern("HHmm")
      val instant = java.time.Instant.ofEpochMilli(timestamp)
      val localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
      localDateTime.format(format)
    }
  }

}
