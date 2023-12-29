package com.cqupt.utils;


import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.*;
import java.util.Properties;

public class NginxLogProducer {

    Properties props =MyPropertiesUtil.load("config.properties");


    private  final String KAFKA_BOOTSTRAP_SERVERS = props.getProperty("kafka.bootstrap-server");
    private  final String KAFKA_TOPIC = props.getProperty("kafka.topics");
    //private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";


    public void sendLogsToKafka(String path) {
        // 设置Kafka生产者配置
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG,"1048576000");
        BufferedInputStream buff = null;
        // 创建Kafka生产者
        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);


        try {
            InputStreamReader isr = new InputStreamReader(new FileInputStream(path));
            BufferedReader br = new BufferedReader(isr);

            String line ;
            try {
                //从字节数组byte[] 里面读取数据
                while ((line = br.readLine()) != null){
                    //line = br.readLine() 这个方法是从文件读取每行数据

                    System.out.println("============发送的数据============ "+line);
                    ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_TOPIC, line+"\r\n");
                    producer.send(record);



                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            producer.close();
            if (null != buff ){
                try {
                    buff.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
