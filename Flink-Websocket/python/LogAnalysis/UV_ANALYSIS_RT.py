# 导入模块 pyspark

from pyspark.sql import SQLContext, Row # 用这个工具箱进行sql分析

# -*- coding: utf-8 -*-
from pyspark.sql import SparkSession
from pyspark.sql.functions import from_json, window, lit
from pyspark.sql.types import StructType, StringType

# 导入系统模块
import os
#import pymysql
import time
from datetime import datetime # 对日期进行处理
#import exrex


if __name__ == '__main__':
    # 设置SPARK_HOME环境变量, 非常重要, 如果没有设置的话,SparkApplication运行不了
    # 设置开始时间

    os.environ['SPARK_HOME'] = 'D:\\App\\spark\\'
    os.environ['HADOOP_HOME']= 'D:\\App\\hadoop\\winuntil\\'
    # Create SparkConf
    spark = SparkSession \
        .builder \
        .config("spark.jars.packages", "org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.7") \
        .appName("pyspark_structured_streaming_kafka") \
        .getOrCreate()

    """
            在使用pyspark编程运行程序时,依赖于py4j模块，为什么？？？？
           由于在运行的时候，SparkContext Python API创建的对象 通过 py4j(python for java)转换
       为JavaSparkContext，然后调度Spark Application.
       """
    # 设置日志级别

    df = spark.readStream \
        .format("kafka") \
        .option("kafka.bootstrap.servers", "localhost:9092") \
        .option("partition.assignment.strategy", "range")\
        .option("subscribe",'LOG_PV_UV') \
        .load()

    base_df = df.selectExpr("CAST(value AS STRING)")
    from pyspark.sql.functions import regexp_extract
    split_df = base_df.select(
        regexp_extract('value', r'^([^\s]+\s)', 1).alias("host"),
        regexp_extract('value', r'^.*\[(\d\d/\w{3}/\d{4}:\d{2}:\d{2}:\d{2} \+\d{4})]', 1).alias('timestamp'),
        regexp_extract('value', r'^.*"\w+\s+([^\s]+)\s+HTTP.*"', 1).alias('path'),
        regexp_extract('value', r'^.*"\s+([^\s]+)', 1).cast('integer').alias('status'),
        regexp_extract('value', r'^.*\s+(\d+)$', 1).cast('integer').alias('content_size')
    )
    print(split_df)
    #split_df.show(20,truncate=False)


    def parse_clf_time(s):
        # 定义一个字典
        month_map = {
            'Jan': 1, 'Feb': 2, 'Mar': 3, 'Apr': 4, 'May': 5, 'Jun': 6,
            'Jul': 7, 'Aug': 8, 'Sep': 9, 'Oct': 10, 'Nov': 11, 'Dec': 12
        }
        return "{0:04d}-{1:02d}-{2:02d} {3:02d}:{4:02d}:{5:02d}".format(
            int(s[7:11]),
            month_map[s[3:6]],
            int(s[0:2]),
            int(s[12:14]),
            int(s[15:17]),
            int(s[18:20]))


    from pyspark.sql.functions import udf
    u_parse_time = udf(parse_clf_time)

    #进行数据转换
    logs_df = split_df.select(
        '*',
        u_parse_time(split_df['timestamp']).cast('timestamp').alias('time')
    ).drop('timestamp')
    # # 打印

    from pyspark.sql.functions import dayofmonth,hour,minute
    from pyspark.sql.functions import col, sum,count,from_unixtime

    hour_to_host_pair_df = logs_df.select(logs_df.host, hour(logs_df.time).alias('hour'))
    hour_group_host_df = hour_to_host_pair_df.distinct()
    hour_host_df = hour_group_host_df.groupBy('hour').count()

    # select data only inclue hosts and timestamp
    raw_date_hosts = logs_df.select(logs_df.time,logs_df.host)

    stat_hosts404 = logs_df.filter('status=404').select(logs_df.time,logs_df.host)


    # 定义连接参数
    mysql_host = "192.168.1.182"
    mysql_port = "3306"
    mysql_user = "root"
    mysql_password = "conqure2013"
    mysql_database = "biprod_realtime"

    # 定义输出模式为 append 或 update
    output_mode = "append"

    windowedCounts = raw_date_hosts \
    .withWatermark("time", "5 minutes")\
        .groupBy(window(col("time"), "5 minutes", "5 minutes")) \
        .agg(count("host").alias("count"))

    windowedCounts404 = stat_hosts404 \
        .withWatermark("time", "5 minutes") \
        .groupBy(window(col("time"), "5 minutes", "5 minutes")) \
        .agg(count("host").alias("count"))


    # processedDF = windowedCounts.select(
    #     col("window.start").alias("start"),
    #     col("window.end").alias("end"),
    #     col("count")
    # ).withColumn("start_string", from_unixtime(col("start")).cast("string")).withColumn("end_string", from_unixtime(col("end")).cast("string"))

    midWindowCount = windowedCounts.withColumn("endwindow",col("window").getField("end"))

    finalWindowCount = midWindowCount.select(col("endwindow"),col("window").cast("string").alias("window"), col("count"))

    midWindowCount404 = windowedCounts404.withColumn("endwindow",col("window").getField("end"))

    finalWindowCount404 = midWindowCount404.select(col("endwindow"),col("window").cast("string").alias("window"), col("count"))




    # 定义写入 MySQL 的操作
    def write_to_mysql(batch_df, batch_id,table_name):
        batch_df_id = batch_df.withColumn("batch_id",lit(batch_id))
        batch_df_id.write.format("jdbc").option("url", f"jdbc:mysql://{mysql_host}:{mysql_port}/{mysql_database}?useSSL=false").option("dbtable", table_name).option("user", mysql_user).option("password", mysql_password).mode(output_mode).save()

    # 启动流式查询
    query = finalWindowCount.writeStream.outputMode('complete').foreachBatch(lambda batch_df, batch_id: write_to_mysql(batch_df, batch_id, "client_access_log_window_level_count")).start()
    query404 = finalWindowCount404.writeStream.outputMode('complete').foreachBatch(lambda batch_df, batch_id: write_to_mysql(batch_df, batch_id, "client_access_log_404err_window_level_count")).start()


    #query = windowedCounts.writeStream.outputMode('complete').format('console').start()

    query.awaitTermination()
    query404.awaitTermination()




















