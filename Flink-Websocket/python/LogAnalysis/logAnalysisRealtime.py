# -*- coding: utf-8 -*-
from pyspark.sql import SparkSession
from pyspark.sql.functions import from_json, window
import pandas as pd
from pyspark import SparkContext
import logging
# 导入系统模块
import os,sys




if __name__ == '__main__':

# 构建spark框架的基础环境

    os.environ['SPARK_HOME'] = 'D:\\App\\spark'
    os.environ['HADOOP_HOME']= 'D:\\APP\\Hadoop\\winuntil\\'

# 构建spark上下文环境，sparkSession
    spark = SparkSession.builder.config("spark.jars.packages", "org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.7") \
    .appName("pyspark_structured_streaming_kafka") \
    .getOrCreate()

    df=spark.readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("partition.assignment.strategy", "range") \
    .option("subscribe", "LOG_PV_UV") \
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

    split_df.printSchema()

    def parse_clf_time(s):
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


    # 通过实时输入的数据流进行窗口触发计算

    from pyspark.sql.functions import dayofmonth,hour,minute
    from pyspark.sql.functions import col, sum,count,from_unixtime


    # select data only inclue hosts and timestamp
    raw_date_hosts = logs_df.select(logs_df.time,logs_df.host)

    stat_hosts404 = logs_df.filter('status=404').select(logs_df.time,logs_df.host)


    # 定义连接参数
    mysql_host = "localhost"
    mysql_port = "3306"
    mysql_user = "root"
    mysql_password = "conqure2013"
    mysql_database = "biprod_realtime"

    # 定义输出模式为 append 或 update
    output_mode = "append"

    windowedCounts = raw_date_hosts \
        .withWatermark("time", "5 minutes") \
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
        batch_df_id.write.format("jdbc").option("url", f"jdbc:mysql://{mysql_host}:{mysql_port}/{mysql_database}").option("dbtable", table_name).option("user", mysql_user).option("password", mysql_password).mode(output_mode).save()

    # 启动流式查询
    query = finalWindowCount.writeStream.outputMode('complete').foreachBatch(lambda batch_df, batch_id: write_to_mysql(batch_df, batch_id, "client_access_log_window_level_count")).start()
    query404 = finalWindowCount404.writeStream.outputMode('complete').foreachBatch(lambda batch_df, batch_id: write_to_mysql(batch_df, batch_id, "client_access_log_404err_window_level_count")).start()


    #query = windowedCounts.writeStream.outputMode('complete').format('console').start()

    query.awaitTermination()
    query404.awaitTermination()


