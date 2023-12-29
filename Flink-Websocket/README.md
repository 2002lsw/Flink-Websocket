# 项目实战——可视化数据实时分析

## 1.项目分析

1. 配置Watchdog：根据选择的Watchdog框架，配置监控数据源和定义触发条件。确保Watchdog能够捕获请求日志并执行相应操作。 
2. 编写记录日志的代码：在Watchdog的特定事件或回调函数中，编写代码来记录请求日志。
3. 配置Zookeeper：使用Zookeeper作为服务发现和协调工具，确保Kafka能够进行服务注册和发现。
4. 使用Kafka生产者API：在记录请求日志后，使用Kafka的生产者API将日志消息发送到Kafka主题。
   - 导入所需的Kafka生产者库。
   - 创建一个Kafka生产者实例，并配置所需的属性，如Kafka服务器地址和序列化器。
   - 在记录请求日志的代码中，使用Kafka生产者发送日志消息到指定的Kafka主题。
5. 配置Flink：在Flink中，设置一个消费者来读取Kafka主题中的日志消息，并进行处理。
   - 配置Flink环境和连接到Kafka集群。
   - 创建一个Kafka消费者，并从指定的Kafka主题中消费日志消息。
   - 定义Flink任务来处理接收到的日志消息，例如聚合、过滤或转换操作。
6. 编写Spring Boot后端代码：在Spring Boot应用程序中，需要编写WebSocket处理器来接收来自Flink的结果数据，并通过WebSocket将它们发送给前端。
   - 创建一个WebSocket处理器类，并实现相应的处理方法。
   - 在处理方法中，接收来自Flink的结果数据，并将其转发到与前端建立的WebSocket连接。
7. 前端实现：在Vue + ECharts的前端应用程序中，使用WebSocket与后端建立双向通信，并使用ECharts将接收到的实时数据进行可视化展示。

整体架构如下：

```
日志--Watchdog--(Kafka)-->Flink--后端springboot--(WebSocket)--> 前端(Vue + ECharts)
                   |
                Zookeeper
```

## 2.环境搭建

### 2.1 Kafka集群部署

#### 2.1.1集群规划

配置3台虚拟机，下面是3台电脑的配置

| 主机名 | ip地址        | broker.id | myid |
| ------ | ------------- | --------- | ---- |
| HA181  | 192.168.1.181 | 1         | 1    |
| HA182  | 192.168.1.182 | 2         | 2    |
| HA189  | 192.168.1.189 | 9         | 9    |

#### 2.1.2 Kafka jar包下载

安装包下载链接  http://kafka.apache.org/downloads.html 

下载所需要的版本，该设计使用的是kafka_2.12-2.4.1

将下载的kafka安装包通过XShell，FinalShell等Liunx连接工具，将安装包放入一个指定目录即可，演示文档通过放在将安装包放在/home/semir/packages，后面的工具都是如此

####  #解压

  cd到指定目录

```
 cd /home/semir/packages 
```

解压到/home/semir/software下，可以在指定目录下看见kafka 

```
tar -zxvf kafka_2.12-2.4.1.tgz -C /home/semir/software   
```

 修改配置

```
 cd /home/semir/software/kafka_2.12-2.4.1/config
 vim  server.properties
```

#### **2.1.4 **修改配置文件

| #broker的全局唯一编号，不能重复                              | broker.id=0                         |
| ------------------------------------------------------------ | ----------------------------------- |
| #删除topic功能使能                                           | delete.topic.enable=true            |
| #自动创建topic，false：生产者发送信息到已存在topic才没有报错 | auto.create.topics.enable = false   |
| #处理网络请求的线程数量                                      | num.network.threads=3               |
| #用来处理磁盘IO的现成数量                                    | num.io.threads=8                    |
| #发送套接字的缓冲区大小                                      | socket.send.buffer.bytes=102400     |
| #接收套接字的缓冲区大小                                      | socket.receive.buffer.bytes=102400  |
| #请求套接字的缓冲区大小                                      | socket.request.max.bytes=104857600  |
| #kafka运行日志存放的路径                                     | log.dirs=/opt/module/kafka/logs     |
| #topic在当前broker上的分区个数                               | num.partitions=3                    |
| #用来恢复和清理data下数据的线程数量                          | num.recovery.threads.per.data.dir=1 |
| *#配置连接 Zookeeper 集群地址*                               | zookeeper.connect=（）              |

- 如果kafaka启动时加载的配置文件中server.properties没有配置delete.topic.enable=true，那么此时的删除并不是真正的删除，而是把topic标记为：marked for deletion

- 其几两台服务器相同操作，broker.id在集群中要唯一

  配置文件如下，因为是集群配置，broke.id不一样即可,下面其余配置看对应需求选择修改

  ```
  broker.id=1
  num.network.threads=3
  num.io.threads=8
  socket.send.buffer.bytes=102400
  socket.receive.buffer.bytes=102400
  socket.request.max.bytes=104857600
  
  log.dirs=/data/tmp/kafka/kafka-logs
  
  num.partitions=3
  
  num.recovery.threads.per.data.dir=1
  offsets.topic.replication.factor=1
  transaction.state.log.replication.factor=1
  transaction.state.log.min.isr=1
  log.retention.hours=168
  log.segment.bytes=1073741824
  log.retention.check.interval.ms=300000
  
  zookeeper.connect=HA181:2181,HA182:2181,HA189:2181
  
  zookeeper.connection.timeout.ms=6000
  group.initial.rebalance.delay.ms=0
   
  ```

2.1.5安装ZooKeeper

首先，进入Zookeeper官网（https://zookeeper.apache.org/），下载你所需要的ZooKeeper版本，本设计采用的是3.6.3版本

####  #解压

  cd到指定目录

```
 cd /home/semir/packages 
```

解压到/home/semir/software下，可以在指定目录下看见kafka 

```
tar -zxvf zookeeper-3.4.5.tar.gz  -C /home/semir/software   
```

zookeeper有点不一样，先将配置文件模板复制一份

```
cp zoo_sample.cfg zoo.cfg
```

 修改配置

```
 cd /home/semir/software/zookeeper-3.4.5/conf/
 vim zoo.cfg
```

修改配置文件Zookeeper.properties

| # zookeeper时间配置中的基本单位（毫秒）                      | tickTime=2000                       |
| ------------------------------------------------------------ | ----------------------------------- |
| # 允许follower初始化连接到leader最大时长，它表示tickTime时间的倍数 即： | initLimit*tickTime initLimit=10     |
| # 运行follower与leader数据同步最大时长，它表示tickTime时间倍数 | syncLimit*tickTime syncLimit=5      |
| # zookeeper数据存储目录及日志保存目录（如果没有指明dataLogDir，则日志也保存在这个文件中） | dataDir=/tmp/zookeeper              |
| # 对客户端提供的端口号                                       | clientPort=2181                     |
| # 单个客户端于zookeeper最大并发连接数                        | maxClientCnxns=60                   |
| # 保存的数据快照数量，之外的将会被清除                       | autopurge.snapRetainCount=3         |
| # 自动出发清除任务时间间隔，以小时为单位。默认为0，表示不自动清除 | autopurge.purgeInterval=1           |
| #metricsProvider.httpPort=7000                               | #metricsProvider.exportJvmInfo=true |
| ## ttl settings                                              | extendedTypesEnabled=true           |

配置文件如下：将三台机器都按照这种方式配置即可

```
# The number of milliseconds of each tick
tickTime=2000
# The number of ticks that the initial 
# synchronization phase can take
initLimit=10
# The number of ticks that can pass between 
# sending a request and getting an acknowledgement
syncLimit=5
# the directory where the snapshot is stored.
# do not use /tmp for storage, /tmp here is just 
# example sakes.
dataDir=/data/zookeeper/tmp
# the port at which the clients will connect
clientPort=2181
#
# Be sure to read the maintenance section of the 
# administrator guide before turning on autopurge.
#
# http://zookeeper.apache.org/doc/current/zookeeperAdmin.html#sc_maintenance
#
# The number of snapshots to retain in dataDir
#autopurge.snapRetainCount=3
# Purge task interval in hours
# Set to "0" to disable auto purge feature
#autopurge.purgeInterval=1
server.1=0.0.0.0:2888:3888
server.2=192.168.1.182:2888:3888
server.3=192.168.1.189:2888:3888
```

启动命令,因为Kafka的启动依赖Zookeeper，我们先启动Zookeeper

```
cd /home/semir/software/zookeeper/bin/
sh zkServer.sh start
```

**使用jps查看或者sh zkServer.sh status启动是否成功**

三台Zookeeper启动成功后，启动Kafka，使用nohup将服务运行在后台，不会因为当前窗口的关闭而关闭服务，输出的日志文件将追加输出到nohup.out。

```
nohup kafka-server-start.sh /home/semir/software/kafka_2.12-2.4.1/config/server.properties & 
jps #查看Kafka是否启动
```

### 2.3 部署Flink

#### 2.3.1 Flink集群搭建

##### 1.集群规划

服务器: HA181(Master)

服务器: HA182(Slave)

服务器: HA183(Slave)

##### 2.Flink使用的是flink-1.12.0，安装包下载地址：

```
http://flink.apache.org/downloads.html
```

####  #解压

  cd到指定目录

```
 cd /home/semir/packages 
```

解压到/home/semir/software下，可以在指定目录下看见kafka 

```
tar -zxvf flink-1.12.0 -C /home/semir/software/  
```

 修改配置

```
 cd  /home/semir/software/flink-1.12.0/conf/
 vim flink-conf.yaml
```

集群配置如下：

```

jobmanager.rpc.address: 192.168.1.181

jobmanager.rpc.port: 6123

jobmanager.memory.process.size: 1600m

taskmanager.memory.process.size: 1728m

taskmanager.numberOfTaskSlots: 1

parallelism.default: 1

jobmanager.execution.failover-strategy: region
```

指定workers

```
vim workers 
```

 配置如下：

```
192.168.1.182
192.168.1.189
```

指定master

```
 vim masters 
```

 配置如下：

```
192.168.1.181
```

启动Flink集群

```text
bin/start-cluster.sh
```

**通过jps查看进程信息**

```text
--------------------- node1 ----------------
86583 Jps
85963 StandaloneSessionClusterEntrypoint
86446 TaskManagerRunner
--------------------- node2 ----------------
44099 Jps
43819 TaskManagerRunner
--------------------- node3 ----------------
29461 TaskManagerRunner
29678 Jps
```

浏览Flink Web UI界面

[http://192.168.1.181:8081]

![image-20231216210116546](https://gitee.com/lisuwenlike/ChampionWorkplace/raw/master/asset/image-20231216210116546.png)

##### 启动/停止flink集群

启动：./bin/start-cluster.sh

停止：./bin/stop-cluster.sh

##### Flink集群的重启或扩容

- 启动/停止jobmanager

```
启动：./bin/start-cluster.sh

停止：./bin/stop-cluster.sh
```

- 如果集群中的jobmanager进程挂了，执行下面命令启动

```
bin/jobmanager.sh start

bin/jobmanager.sh stop
```

- 添加新的taskmanager节点或者重启taskmanager节点

```
bin/taskmanager.sh start

bin/taskmanager.sh stop
```

#### 注：生成日期的时候注意时区问题，通过修改正则表达式，匹配+- 去覆盖多个时区。
