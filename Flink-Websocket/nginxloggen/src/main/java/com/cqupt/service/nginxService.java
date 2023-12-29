package com.cqupt.service;

import com.cqupt.utils.RandomRegionGenerator;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.cqupt.utils.MyPropertiesUtil;

@Service

public class nginxService {

    RandomRegionGenerator RandomRegion = new RandomRegionGenerator();
    //MyPropertiesUtil properties = new MyPropertiesUtil();
    Properties props =MyPropertiesUtil.load("config.properties");
    String path = props.getProperty("log.dir");



    private static final String[] IP_ADDRESSES = {
            "58.121.135.55", "40.73.112.226", "uplherc.upl.com", "uplherc.upl.cn", "192.168.0.5",

    };

    private static final String[] REQUEST_METHODS = {
            "GET", "POST", "PUT", "DELETE"
    };

    private static final int[] STATUS_CODE = {
            200, 404, 501,403,503,400,401
    };

    private static final String[] DEVICE_EVENT = {
            "online", "offline", "warn"
    };

    private static final String[] USER_EVENT = {
            "reg", "follow", "order","other"
    };


    private static final String[] URL_PATHS = {
            "/", "/about", "/contact", "/products", "/services","/nba","/data/service/upload"
    };

    private static final int MIN_RESPONSE_TIME = 100;
    private static final int MAX_RESPONSE_TIME = 5000;

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:87.0) Gecko/20100101 Firefox/87.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Version/14.0.3 Safari/537.36"
    };

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");


    public void gen_log_nginx(String fileName,int eventCount,String postman){

        String fileDir = path;
        int[] numlogs = {
                8500, 4500, 3750, 5500, 6430, 7210
        };



            try (FileWriter writer = new FileWriter(fileDir + fileName+genTimeString())) {

                Random rand = new Random();
                int logCount = eventCount;
                for (int i = 0; i < logCount; i++) {
                    String ipAddress = randomIpAddress(rand);

                    String timestamp = generateTimestamp();
                    String requestMethod = getRandomElement(REQUEST_METHODS);
                    String urlPath = getRandomElement(URL_PATHS);
                    int statusCode = getRandomElement(STATUS_CODE);
                    int responseTime = generateResponseTime();
                    String userAgent = getRandomElement(USER_AGENTS);
                    String mid = "mid_" + getRandInt(1, 500000);
                    String uid = "user_" + getRandInt(1, 300000);
                    String user_event = getRandomElement(USER_EVENT);
                    String device_status=getRandomElement(DEVICE_EVENT);
                    List<String> region_info = RandomRegion.generateRandomRegions();
                    String provice=region_info.get(0);
                    String city = region_info.get(1);



                    String logEntry = String.format( "%s - - [%s] \"%s %s HTTP/1.1\" %d %d %s %s %s %s %s %s\n",
                            ipAddress, timestamp, requestMethod, urlPath,statusCode, responseTime,mid,
                            uid,user_event,device_status,provice,city);

                    writer.write(logEntry);
                }
                System.out.println("time:"+genTimeString()+" ----- user:"+postman+" 提交请求,日志生成完成！");
            } catch (IOException e) {
                System.out.println("生成日志时发生错误：" + e.getMessage());
            }


    }



    private static String getRandomElement(String[] array) {
        Random rand = new Random();
        int index = rand.nextInt(array.length);
        return array[index];
    }

    public static String randomIpAddress(Random random) {
        // 生成四个随机数作为 IP 地址的每个部分
        int part1 = random.nextInt(256);
        int part2 = random.nextInt(256);
        int part3 = random.nextInt(256);
        int part4 = random.nextInt(256);

        // 构建 IP 地址字符串
        StringBuilder ipAddress = new StringBuilder();
        ipAddress.append(part1).append('.').append(part2).append('.').append(part3).append('.').append(part4);

        return ipAddress.toString();
    }

    private static int getRandomElement(int[] array) {
        Random rand = new Random();
        int index = rand.nextInt(array.length);
        return array[index];
    }

    public static   int getRandInt(int fromNum,int toNum){
        return   fromNum+ new Random().nextInt(toNum-fromNum+1);
    }

    private static String genTimeString(){
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssZ", Locale.ENGLISH);
        String timestamp = dateFormat.format(date);




        return timestamp;



    }

    private static String generateTimestamp() {
        Date now = new Date();
        Random random =new Random();

        long minTimestamp = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1); // 1 minute ago
        long maxTimestamp = System.currentTimeMillis(); // current timestamp
        long randomTimestamp = minTimestamp + random.nextInt((int) (maxTimestamp - minTimestamp));

        Date date = new Date(randomTimestamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);
        String timestamp = dateFormat.format(date);

        // SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);
        // String timestamp = dateFormat.format(new Date());


        return timestamp;
    }

    private static int generateResponseTime() {
        Random rand = new Random();
        return rand.nextInt(MAX_RESPONSE_TIME - MIN_RESPONSE_TIME + 1) + MIN_RESPONSE_TIME;
    }



}
