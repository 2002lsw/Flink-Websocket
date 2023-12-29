# 导入模块 pyspark
from pyspark import SparkConf, SparkContext
from pyspark.sql import SQLContext, Row # 用这个工具箱进行sql分析
# 导入系统模块
import os
#import pymysql
import time
from datetime import datetime # 对日期进行处理
#import exrex



if __name__ == '__main__':

    # 构建spark分析环境 以及Hadoop虚拟目录

    os.environ['SPARK_HOME'] = 'G:\\App\\spark-2.4.7\\'
    os.environ['HADOOP_HOME']= 'G:\\APP\\Hadoop\\winuntil\\'

    sparkConf = SparkConf() \
        .setAppName('PySpark Log Analyzer') \
        .setMaster('local[4]')
    """
            在使用pyspark编程运行程序时,依赖于py4j模块，为什么？？？？
           由于在运行的时候，SparkContext Python API创建的对象 通过 py4j(python for java)转换
       为JavaSparkContext，然后调度Spark Application.
       """
    # 设置日志级别
    sc = SparkContext(conf=sparkConf)
    #sc.setLogLevel('WARN')
    sqlContext = SQLContext(sparkContext=sc)


    # 记录程序开始执行的时间

    start_ = datetime.utcnow()
    print(start_)
    #读取日志文件


    # 定义日志文件的路径 本项目用的是本地文件路径
    log_file_path = "access_log_Aug2018"


    base_df =sqlContext.read.text(log_file_path)
    base_df.printSchema()
    base_df.show(10,truncate=False)

    pass



    #通过正则匹配相关的列，并将数据抽取出来

    from pyspark.sql.functions import regexp_extract

    split_df = base_df.select(
        regexp_extract('value', r'^([^\s]+\s)', 1).alias("host"),
        regexp_extract('value', r'^.*\[(\d\d/\w{3}/\d{4}:\d{2}:\d{2}:\d{2} -\d{4})]', 1).alias('timestamp'),
        regexp_extract('value', r'^.*"\w+\s+([^\s]+)\s+HTTP.*"', 1).alias('path'),
        regexp_extract('value', r'^.*"\s+([^\s]+)', 1).cast('integer').alias('status'),
        regexp_extract('value', r'^.*\s+(\d+)$', 1).cast('integer').alias('content_size')
    )
    split_df.printSchema()

    split_df.show(5,truncate=False)

    black_df = base_df.filter(base_df['value'].isNull())
    print("空行：" + str(black_df.count()))
    pass
    # # # 空行：0

    ## 查看每一行至少有一个空行的笔数：

    bad_rows_df = split_df.filter(
        split_df['host'].isNull() | \
        split_df['timestamp'].isNull() | \
        split_df['path'].isNull() | \
        split_df['status'].isNull() | \
        split_df['content_size'].isNull()
    )
    print("行数(至少包含一个null值)：\n")
    print(bad_rows_df.count())

    # =============================================================
    # 考虑：到底哪些字段的数据存在null值呢？？？
    # 统计每列有多少个null值

    from pyspark.sql.functions import col, sum

    #通过这个函数可以计算每一列的空字段的个数
    def count_null(column_name):
        return sum(col(column_name).isNull().cast('integer')).alias(column_name)


    # 通过计算得到空字段值的计数列表 【0，0，0，0，1578】
    exprs = []
    for col_name in split_df.columns:
        exprs.append(count_null(col_name))

    split_df.agg(*exprs).show()

    # 统计整行数据里，不以数字为结尾的行
    bad_content_size_df = base_df.filter(~ base_df['value'].rlike(r'\d+$'))
    print(bad_content_size_df.count())

    # 进行数据清洗，将最后一个字段值为 “-”这样的记录替换成0
    cleaned_df = split_df.na.fill({'content_size': 0})
    cleaned_df.agg(*exprs).show()
    print("清洗数据Count：\n")
    print(cleaned_df.count())

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
    logs_df = cleaned_df.select(
        '*',
        u_parse_time(cleaned_df['timestamp']).cast('timestamp').alias('time')
    ).drop('timestamp')
    # # 打印
    logs_df.show(truncate=False)

    content_size_summary_df = logs_df.describe(['content_size'])
    print(content_size_summary_df.show(truncate=False))

    from pyspark.sql import functions as sql_function

    content_size_state = logs_df.agg(
        sql_function.avg(logs_df['content_size']),
        sql_function.max(logs_df['content_size']),
        sql_function.min(logs_df['content_size'])
    ).first()

    print ('Count Size Avg:{0:,.2f}; Min: {2:,.0f}; Max: {1:,.0f}'.format(*content_size_state))



    # # 分组， 统计，排序
    status_to_count_df = logs_df.groupBy('status').count().sort('status')
    status_to_count_length = status_to_count_df.count()
    # # 打印信息
    print('Fount %d response code' % status_to_count_length)
    # # 展示结果
    status_to_count_df.show()


    status_to_count_df.write.format("jdbc") \
        .option("url", "jdbc:mysql://213q7821q7.51mypc.cn:5038/biprod") \
        .option("dbtable", "clent_access_status_count") \
        .option("user", "root") \
        .option("password", "SM2689@AZURE") \
        .mode("append") \
        .save()


    host_sum_df = logs_df.groupBy('host').count().sort('count', ascending=False)
    host_sum_df.show(100,False)

    host_more_than_10_df = host_sum_df \
        .filter(host_sum_df['count'] > 3000) \
        .select(host_sum_df['host'])
    host_more_than_10_df.show(100,False)



    paths_df = (logs_df
                .groupBy('path')
                .count()
                .sort('count', descending=True)
                )
    paths_df.show(100,False)

    paths_counts = (paths_df
                    .select('path', 'count')
                    # .map(lambda r: (r[0], r[1]))
                    .collect()
                    )
    for uri_count in paths_counts:
        print (uri_count)



    not_200_df = logs_df.filter('status<>200')
    # # -2, 分组统计排序
    logs_sum_df = (not_200_df
                   .groupBy('path')
                   .count()
                   .sort('count', ascending=False)
                   )
    print('Top ten Failed URIs：\n')
    print(logs_sum_df.show(10, False))

    print(logs_df.select('host').distinct().count())

    # # 方式二：使用dropDuplicates进行去重
    unique_host_count = logs_df.dropDuplicates(['host']).count()
    print('Unique hosts: {0}'.format(unique_host_count))
    pass
    from pyspark.sql.functions import dayofmonth
    day_to_host_pair_df = logs_df.select(logs_df.host, dayofmonth(logs_df.time).alias('day'))
    day_group_host_df = day_to_host_pair_df.distinct()
    daily_host_df = day_group_host_df.groupBy('day').count().cache()
    print('Unique hosts per day: \n')
    daily_host_df.show(30, False)
    pass

    # ####################### -8, 日均host访问量 ################################
    """
        统计每天 各个host平均访问量
            -1, 统计出每天总访问量
                将结果存储在total_req_pre_day_df中
            -2, 统计每天的host访问数
                第七个需求已经完成
            -3, 每天的总访问量 / 每天的总host = 日均的host访问量
    
    """

    # -1, 统计出每天的总访问量, host day count
    total_req_pre_day_df = logs_df \
        .select(logs_df.host, dayofmonth(logs_df.time).alias('day')) \
        .groupBy('day') \
        .count()
    total_req_pre_day_df.registerTempTable('total_req_pre_day_df')
    daily_host_df.registerTempTable('daily_host_df')


    # daily_host_df.write.format("jdbc") \
    #     .option("url", "jdbc:mysql://localhost:3306/biprod") \
    #     .option("dbtable", "client_access_log_daily_level_count") \
    #     .option("user", "root") \
    #     .option("password", "conqure2013") \
    #     .mode("overwrite") \
    #     .save()

    # -4, 编写SQL进行关联分析
    sql = 'SELECT ' \
          't1.day AS day, t1.count / t2.count AS avg_host_pv ' \
          'FROM ' \
          'total_req_pre_day_df t1, daily_host_df t2 ' \
          'WHERE t1.day = t2.day'
    avg_daily_req_host_df = sqlContext.sql(sql)

    print(avg_daily_req_host_df.show(30))

    # data into mysql table
    # avg_daily_req_host_df.write.format("jdbc") \
    #     .option("url", "jdbc:mysql://localhost:3306/biprod") \
    #     .option("dbtable", "client_access_log_daily_level_avg_count") \
    #     .option("user", "root") \
    #     .option("password", "conqure2013") \
    #     .mode("overwrite") \
    #     .save()


    not_found_df = logs_df.filter('status=404')
    not_found_df.cache()
    print(
        'Found {0} 404 URLs: '.format(not_found_df.count()))
    # Found 10845 404 URLs:

    # -2, 看看有哪些 URIs, 返回的HTTP 404, 此处需要考虑去重
    not_found_paths_df = not_found_df.select('path')
    # 去重
    unique_not_found_paths_df = not_found_paths_df.distinct()

    print("404 urls\n")
    print(unique_not_found_paths_df.show(n=40, truncate=False))
    path = 'path'
    # -3, 统计返回HTTP 404状态最多的20个URIs，按照path进行分组，进行count统计，降序排序
    # 分组统计排序
    top_20_not_found_df = (not_found_paths_df
                           .groupBy('path')
                           .count()
                           .sort('count', ascending=False)
                           )
    # 打印
    print('Top Twenty 404 URIs:\n')
    print(top_20_not_found_df.show(n=20, truncate=False))
    pass

    # -4, 统计收到HTTP 404状态的最多的25个hosts
    # 分组\统计\排序
    hosts_404_count_df = (not_found_df
                          .select('host')
                          .groupBy('host')
                          .count()
                          .sort('count', ascending=False)
                          )
    # 显示
    print('Top 25 hosts that generated errors: \n')
    print(hosts_404_count_df.show(n=25, truncate=False))

    from pyspark.sql.functions import dayofmonth

    errors_by_date_df = (not_found_df
                         .select(dayofmonth('time').alias('day'))
                         .groupBy('day')
                         .count()
                         )
    #
    print("404 Error by day:\n")
    print(errors_by_date_df.show(n=30, truncate=False))

    # errors_by_date_df.write.format("jdbc") \
    #     .option("url", "jdbc:mysql://localhost:3306/biprod") \
    #     .option("dbtable", "clint_access_log_404err_daily_level_count") \
    #     .option("user", "root") \
    #     .option("password", "conqure2013") \
    #     .mode("overwrite") \
    #     .save()

    # -6, 统计哪5天出现HTTP 404次数最多
    top_error_date_df = errors_by_date_df.sort('count', ascending=False)
    print('Top Five Dates for 404 Requests:\n')
    print(top_error_date_df.show(n=5, truncate=False))

    from pyspark.sql.functions import hour

    hour_records_sorted_df = (not_found_df
                              .select(hour('time').alias('hour'))
                              .groupBy('hour')
                              .count()
                              .sort('count', ascending=False)
                              )
    print('Top hours for 404 Requests:\n')
    print(hour_records_sorted_df.show(n=24, truncate=False))
    print(hour_records_sorted_df.count())


    # hour_records_sorted_df.write.format("jdbc") \
    #     .option("url", "jdbc:mysql://localhost:3306/biprod") \
    #     .option("dbtable", "clint_access_log_404err_hourly_level_count") \
    #     .option("user", "root") \
    #     .option("password", "conqure2013") \
    #     .mode("overwrite") \
    #     .save()


    end_ = datetime.utcnow()
    print(end_)
    c = (end_ - start_)
    print("本次大数据分析执行的时间为：\n")
    print(c.microseconds)
    print("微秒")