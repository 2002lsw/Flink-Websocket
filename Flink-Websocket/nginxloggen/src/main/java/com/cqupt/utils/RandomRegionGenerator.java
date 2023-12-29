package com.cqupt.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

class Region {
    int id;
    int postcode;
    int parent_id;
    String name;
    int level;
    List<Region> children;

    public Region(int id, int postcode,int parent_id, String name, int level) {
        this.id = id;
        this.parent_id = parent_id;
        this.name = name;
        this.level = level;
        this.postcode=postcode;
        this.children = new ArrayList<>();
    }
}

public class RandomRegionGenerator {

       // MyPropertiesUtil properties = new MyPropertiesUtil();
        Properties props =MyPropertiesUtil.load("config.properties");
        String path = props.getProperty("file.dist");

        List<String> dataLines = readDataFromFile(path);
        public List<Region> regions = buildRegionHierarchy(dataLines);




        // 随机生成省市数据
         //generateRandomRegions(regions, 5); // 生成 5 个随机省市

        // 打印生成的随机数据
//        for (Region region : regions) {
//            printRegion(region, 0);
//        }


    public static List<Region> buildRegionHierarchy(List<String> data) {
        Map<Integer, Region> regionMap = new HashMap<>();
        List<Region> rootRegions = new ArrayList<>();

        for (int i = 1; i < data.size(); i++) {
            String line = data.get(i);

            String[] parts = line.split(",");
            int id = Integer.parseInt(parts[0]);
            int code = Integer.parseInt(parts[1]);
            int parent_id = Integer.parseInt(parts[2]);
            String name = parts[3];
            int level = Integer.parseInt(parts[4]);

            Region region = new Region(id,code,parent_id,name,level);
            regionMap.put(id, region);

            if (level == 1) {
                rootRegions.add(region);
            }
        }

        for (Region level1Region : rootRegions) {
            int code = level1Region.postcode;
            for (Region level2Region : regionMap.values()) {
                if (level2Region.level == 2 && level2Region.parent_id == code) {
                    level1Region.children.add(level2Region);
                }
            }
        }
        return rootRegions;
    }

    public  List<String> generateRandomRegions() {
        Random random = new Random();
        List<String> datalist = new ArrayList<>();

            Region randomProvince = regions.get(random.nextInt(regions.size()));
            Region randomCity = randomProvince.children.get(random.nextInt(randomProvince.children.size()));
            System.out.println("随机生成的省市数据：" + randomProvince.name + " - " + randomCity.name);
        datalist.add(randomProvince.name);
        datalist.add(randomCity.name);
            return datalist;

    }



    public static List<String> readDataFromFile(String filePath) {
        List<String> dataLines = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                dataLines.add(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dataLines;
    }
}
