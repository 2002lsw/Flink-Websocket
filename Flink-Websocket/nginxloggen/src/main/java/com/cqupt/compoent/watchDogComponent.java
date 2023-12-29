package com.cqupt.compoent;


import com.cqupt.utils.MyPropertiesUtil;
import com.cqupt.utils.basicWatchDogUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Component

public class watchDogComponent implements  Runnable{
    Properties props = MyPropertiesUtil.load("config.properties");
    String path = props.getProperty("log.dir");



    @PostConstruct
    public void init(){
        //启动线程实例
        new Thread(this).start();
    }

    @Override
    public void run() {


        Path dir = Paths.get(path);


        try {
            new basicWatchDogUtils(dir).processEvents();
        } catch (IOException e) {
            e.printStackTrace();
        }



    }
}
