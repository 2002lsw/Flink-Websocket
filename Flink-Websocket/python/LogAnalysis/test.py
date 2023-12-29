# -*- coding: utf-8 -*-
from pyspark.sql import SparkSession
from pyspark.sql.functions import from_json, window

from pyspark import SparkContext
import logging
# 导入系统模块
import os,sys


if __name__ == '__main__':

    # 构建spark框架的基础环境

    os.environ['SPARK_HOME'] = 'G:\\App\\spark-2.4.7\\'
    os.environ['HADOOP_HOME']= 'G:\\APP\\Hadoop\\winuntil\\'

    # 构建spark上下文环境，sparkSession
    spark = SparkSession.builder.config("spark.jars.packages", "org.apache.spark:spark-sql-kafka-0-10_2.11:2.3.3") \
        .appName("pyspark_structured_streaming_kafka") \
        .getOrCreate()

    df=spark.readStream \
        .format("kafka") \
        .option("kafka.bootstrap.servers", "localhost:9092") \
        .option("partition.assignment.strategy", "range") \
        .option("subscribe", "logpv") \
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

    from pyspark.sql.functions import dayofmonth,hour,minute
    from pyspark.sql.functions import col, sum,count,from_unixtime


    # select data only inclue hosts and timestamp
    raw_date_hosts = logs_df.select(logs_df.time,logs_df.host)

    windowedCounts = raw_date_hosts \
        .withWatermark("time", "10 seconds") \
        .groupBy(window(col("time"), "10 minutes", "5 minutes")) \
        .agg(count("host").alias("count"))


    # from pyspark.sql.functions import regexp_extract
    # split_df = base_df.select(
    #     regexp_extract('value', r'^([^\s]+\s)', 1).alias("host"),
    #     regexp_extract('value', r'^.*\[(\d\d/\w{3}/\d{4}:\d{2}:\d{2}:\d{2} \+\d{4})]', 1).alias('timestamp'),
    #     regexp_extract('value', r'^.*"\w+\s+([^\s]+)\s+HTTP.*"', 1).alias('path'),
    #     regexp_extract('value', r'^.*"\s+([^\s]+)', 1).cast('integer').alias('status'),
    #     regexp_extract('value', r'^.*\s+(\d+)$', 1).cast('integer').alias('content_size')
    # )
    # split_df.printSchema()


    query = windowedCounts.writeStream.outputMode('complete').format('console').start()

    query.awaitTermination()