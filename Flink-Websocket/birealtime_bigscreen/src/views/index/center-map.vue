<script setup lang="ts">
import { ref, reactive, nextTick,onMounted } from "vue";
import { currentGET, GETNOBASE } from "@/api";
import { registerMap, getMap } from "echarts/core";
import { optionHandle, regionCodes } from "./center.map";
import BorderBox13 from "@/components/datav/border-box-13";
import type { MapdataType } from "./center.map";
const option = ref({});
const code = ref("china"); //china 代表中国 其他地市是行政编码
import Stomp from "stompjs";

const stompClient = ref(null); // STOMP客户端对象
const messages = ref([]); // 用于存储接收到的消息

withDefaults(
  defineProps<{
    // 结束数值
    title: number | string;
  }>(),
  {
    title: "地图",
  }
);

const dataSetHandle = async (regionCode: string, list: object[]) => {
  const geojson: any = await getGeojson(regionCode);
  let cityCenter: any = {};
  let mapData: MapdataType[] = [];
  //获取当前地图每块行政区中心点
  geojson.features.forEach((element: any) => {
    cityCenter[element.properties.name] =
      element.properties.centroid || element.properties.center;
  });
  //当前中心点如果有此条数据中心点则赋值x，y当然这个x,y也可以后端返回进行大点，前端省去多行代码
  list.forEach((item: any) => {
    if (cityCenter[item.name]) {
      mapData.push({
        name: item.name,
        value: cityCenter[item.name].concat(item.value),
      });
    }
  });
  await nextTick();

  option.value = optionHandle(regionCode, list, mapData);
};
let url = '/biRealtimeController/findDeviceAreaList'

import request from "../../utils/request";





const getDataSP = async (regionCode: string) => {
  request.get(url).then((res) => {
    console.log("设备分布", res);
    if (res.code==0) {
      dataSetHandle(res.data.regionCode, res.data.dataList);
    }
  });
};
let websocket_path = "ws://localhost:5656/live-msg"
let websocket



const initWebSocket = () => {
  websocket = new WebSocket(websocket_path);

  // WebSocket连接成功时触发
  websocket.onopen = (event) => {
    console.log("WebSocket连接已打开:", event);

    // 在连接成功后可以执行其他操作，如发送初始消息
    // websocket.send("Hello, WebSocket Server!");
  };

  // 收到WebSocket消息时触发
  websocket.onmessage = (event) => {
    console.log("WebSocket收到消息:", event.data);

    // 在这里处理WebSocket收到的消息，可以更新组件数据等操作
  };

  // WebSocket连接关闭时触发
  websocket.onclose = (event) => {
    console.log("WebSocket连接已关闭:", event);
  };

  // WebSocket发生错误时触发
  websocket.onerror = (event) => {
    console.error("WebSocket发生错误:", event);
  };


  stompClient.value = Stomp.over(websocket);

  // 连接到WebSocket服务器
  stompClient.value.connect({}, () => {
    console.log("STOMP连接已建立");

    // 订阅WebSocket主题
    const subscription = stompClient.value.subscribe(`/topic/live-msg-deviceArea`, (message) => {
      const messageData =message.body;
      console.log("STOMP收到消息:", messageData);

      try {
        const parsedData = JSON.parse(messageData); // 解析JSON字符串为对象
        console.log(parsedData.code);

        if (parsedData.code === 0) {
          dataSetHandle(parsedData.data.regionCode, parsedData.data.dataList);
        } else {
          console.error("服务器返回错误代码: ", parsedData.code);
        }
      } catch (error) {
        console.error("解析JSON时出错: ", error);
      }

      // 在这里处理STOMP收到的消息，可以更新组件数据等操作
      //messages.value.push(messageData);
    });

    // 可以在需要的时候取消订阅
    // subscription.unsubscribe();
  });
};







const getGeojson = (regionCode: string) => {
  return new Promise<boolean>(async (resolve) => {
    let mapjson = getMap(regionCode);
    if (mapjson) {
      mapjson = mapjson.geoJSON;
      resolve(mapjson);
    } else {
      mapjson = await GETNOBASE(`./map-geojson/${regionCode}.json`).then(
        (data) => data
      );
      code.value = regionCode;
      registerMap(regionCode, {
        geoJSON: mapjson as any,
        specialAreas: {},
      });
      resolve(mapjson);
    }
  });
};
onMounted(() => {
  console.log("-----Mounted---------");

  initWebSocket();
  // setInterval(() => {
  //   getDataSP(code.value);
  // }, 1500);
  // 初始化加载一次数据

});

//getData(code.value);

const mapClick = (params: any) => {
  console.log(params);
  let xzqData = regionCodes[params.name];
  if (xzqData) {
    getDataSP(xzqData.adcode);
  } else {
    window["$message"].warning("暂无下级地市");
  }
};
</script>

<template>
  <div class="centermap">
    <div class="maptitle">
      <div class="zuo"></div>
      <span class="titletext">{{ title }}</span>
      <div class="you"></div>
    </div>
    <div class="mapwrap">
      <BorderBox13>
        <div class="quanguo" @click="getData('china')" v-if="code !== 'china'">
          中国
        </div>
        <v-chart
          class="chart"
          :option="option"
          ref="centerMapRef"
          @click="mapClick"
          v-if="JSON.stringify(option) != '{}'"
        />
      </BorderBox13>
    </div>
  </div>
</template>

<style scoped lang="scss">
.centermap {
  margin-bottom: 30px;

  .maptitle {
    height: 60px;
    display: flex;
    justify-content: center;
    padding-top: 10px;
    box-sizing: border-box;

    .titletext {
      font-size: 28px;
      font-weight: 900;
      letter-spacing: 6px;
      background: linear-gradient(
        92deg,
        #0072ff 0%,
        #00eaff 48.8525390625%,
        #01aaff 100%
      );
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      margin: 0 10px;
    }

    .zuo,
    .you {
      background-size: 100% 100%;
      width: 29px;
      height: 20px;
      margin-top: 8px;
    }

    .zuo {
      background: url("@/assets/img/xiezuo.png") no-repeat;
    }

    .you {
      background: url("@/assets/img/xieyou.png") no-repeat;
    }
  }

  .mapwrap {
    height: 580px;
    width: 100%;
    // padding: 0 0 10px 0;
    box-sizing: border-box;
    position: relative;

    .quanguo {
      position: absolute;
      right: 20px;
        top: -46px;
      width: 80px;
      height: 28px;
      border: 1px solid #00eded;
      border-radius: 10px;
      color: #00f7f6;
      text-align: center;
      line-height: 26px;
      letter-spacing: 6px;
      cursor: pointer;
      box-shadow: 0 2px 4px rgba(0, 237, 237, 0.5),
        0 0 6px rgba(0, 237, 237, 0.4);
      z-index: 10;
    }
  }
}
</style>
