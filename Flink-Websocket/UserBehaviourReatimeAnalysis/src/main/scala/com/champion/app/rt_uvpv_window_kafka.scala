package com.champion.app

import java.sql.{Connection, Date, DriverManager, PreparedStatement}
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import java.util.Properties
import scala.collection.JavaConverters._

import com.champion.utils.common._
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.scala.function.{ProcessAllWindowFunction, ProcessWindowFunction}
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.{Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}
import org.apache.flink.util.Collector

//import scala.concurrent.duration.Duration

import java.time.Duration


object rt_uvpv_window_kafka {


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
    //kafkaProps.setProperty("bootstrap.servers", "10.16.105.252:9092,10.16.44.134:9092,10.16.11.161:9092")
    kafkaProps.setProperty("bootstrap.servers", "192.168.1.181:9092,192.168.1.182:9092,192.168.1.189:9092")
    //kafkaProps.setProperty("group.id", "flink-consumer-group-window")
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


    val orderCountStream = originkafkaStream
      .assignTimestampsAndWatermarks( WatermarkStrategy
        .forBoundedOutOfOrderness[UserLogRecord](Duration.ofSeconds(3)))
      .windowAll(TumblingEventTimeWindows.of(Time.minutes(1)))//.allowedLateness(Time.seconds(55))

      //.trigger(new CustomTrigger(Time.minutes(1).toMilliseconds))
      .process(new OrderCountWindowFunction)
    orderCountStream.print()
    //orderCountStream.addSink(new MySQLSink[OrderWindowCount]("t_order_count"))

    orderCountStream.addSink(new FlinkKafkaProducer[String]("userlog-mid", new SimpleStringSchema(), kafkaProps))


    val orderTtlStream = originkafkaStream
      .assignTimestampsAndWatermarks( WatermarkStrategy
        .forBoundedOutOfOrderness[UserLogRecord](Duration.ofSeconds(3)))
      .windowAll(TumblingEventTimeWindows.of(Time.minutes(1)))//.allowedLateness(Time.seconds(55))

      //.trigger(new CustomTrigger(Time.minutes(1).toMilliseconds))
      .process(new OrderTtlWindowCountProcess)
    orderTtlStream.print()
    //orderTtlStream.addSink(new MySQLSink[OrderTtlWindowCount]("t_order_ttl"))

    orderTtlStream.addSink(new FlinkKafkaProducer[String]("userlog-mid", new SimpleStringSchema(), kafkaProps))



    env.execute("Kafka to MySQL")

  }
  catch
    {

      case e : Exception => e.printStackTrace
    }


  }




  class MyProcessWindowFunction extends ProcessWindowFunction[UserLogRecord, DeviceStatusCurrCnt, Int, TimeWindow] {
    private var deviceStatusState: ValueState[Set[String]] = _

    override def open(parameters: Configuration): Unit = {
      val deviceStatusStateDescriptor = new ValueStateDescriptor[Set[String]]("deviceStatusState", classOf[Set[String]])
      deviceStatusState = getRuntimeContext.getState(deviceStatusStateDescriptor)
    }
    override def process(key: Int, context: Context, elements: Iterable[UserLogRecord], out: Collector[DeviceStatusCurrCnt]): Unit = {
      val currentTime = System.currentTimeMillis()
      val (onlineCount, offlineCount, warnCount) = calculateStatusCounts(elements)


      val dateFormat = new SimpleDateFormat("mmss")
      val formattedTime = dateFormat.format(new Date(currentTime))

      val currentDeviceStatusState = Option(deviceStatusState.value()).getOrElse(Set())
      val deduplicatedElements = elements.filter(record => !currentDeviceStatusState.contains(record.mid_id))

      deduplicatedElements.foreach(record => deviceStatusState.update(currentDeviceStatusState + record.mid_id))

      val deviceStatusCurrCnt = DeviceStatusCurrCnt(1, formattedTime, onlineCount, offlineCount, warnCount)
      out.collect(deviceStatusCurrCnt)
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

  class OrderCountWindowFunction extends ProcessAllWindowFunction[UserLogRecord, String, TimeWindow] {
    lazy val resultState: ListState[OrderWindowCount] =
      getRuntimeContext.getListState(new ListStateDescriptor[OrderWindowCount]("resultState", classOf[OrderWindowCount]))

    override def process( context: Context, elements: Iterable[UserLogRecord], out: Collector[String]): Unit = {
      val batchId = 74 // 批次ID，根据实际情况进行设置
      val endWindow = context.window.getEnd
      val window = s"[${context.window.getStart}, ${context.window.getEnd}]"
      val ts_mm = formatTimestamp(endWindow)

      val count = elements.size

      resultState.add(OrderWindowCount(batchId, ts_mm, window, count, 0))
      val previousResults = resultState.get().iterator().asScala.toList.takeRight(6)

      // 构造最终的JSON响应
      val responseJson = s"""{
      "code": 0,
      "message": "查询成功",
      "data": {
          "numList2": [${previousResults.map(_ => 0).mkString(",")}],
          "dateList": [${previousResults.map(_.endWindow).reverse.mkString(",")}],
          "numList": [${previousResults.map(_.order_count).reverse.mkString(",")}]
      }
    }"""

      val finalJson = s"""{
  "key": "order_window_count",
  "value": $responseJson
}"""

      out.collect(finalJson)



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


  class OrderTtlWindowCountProcess extends ProcessAllWindowFunction[UserLogRecord, String, TimeWindow] {
    lazy val resultState: ListState[OrderTtlWindowCount] =
      getRuntimeContext.getListState(new ListStateDescriptor[OrderTtlWindowCount]("resultState", classOf[OrderTtlWindowCount]))


    override def process( context: Context, elements: Iterable[UserLogRecord], out: Collector[String]): Unit = {
      val batchId = 74 // 批次ID，根据实际情况进行设置
      val endWindow = context.window.getEnd
      val window = s"[${context.window.getStart}, ${context.window.getEnd}]"
      val ts_mm = formatTimestamp(endWindow)

      val count = elements.size
      val followCount = elements.count(_.user_event == "follow")
      val orderCount = elements.count(_.user_event == "order")
      val totalCount = elements.size
      val orderProp = if (totalCount > 0) Math.round(orderCount * 100.0 / totalCount).toInt else 0

     // out.collect(OrderTtlWindowCount(batchId, ts_mm, window, followCount, orderCount, orderProp))


      resultState.add(OrderTtlWindowCount(batchId, ts_mm, window, followCount, orderCount, orderProp))
      val previousResults = resultState.get().iterator().asScala.toList.takeRight(6)

      // 构造最终的JSON响应
      val responseJson = s"""{
      "code": 0,
      "message": "查询成功",
      "data": {
          "barData": [${previousResults.map(_.order_count).reverse.mkString(",")}],
          "category": [${previousResults.map(_.endWindow).reverse.mkString(",")}],
          "rateData": [${previousResults.map(_.order_prop).reverse.mkString(",")}],
          "lineData": [${previousResults.map(_.follow_count).reverse.mkString(",")}]
      }
    }"""

      val finalJson = s"""{
  "key": "order_window_ttl_count",
  "value": $responseJson
}"""

      out.collect(finalJson)


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
