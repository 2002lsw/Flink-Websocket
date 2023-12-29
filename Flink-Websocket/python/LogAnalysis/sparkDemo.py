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

    os.environ['SPARK_HOME'] = 'D:\\App\\spark\\'
    os.environ['HADOOP_HOME']= 'D:\\APP\\Hadoop\\bin\\winuntil\\'

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


    #读取日志文件



    pass








