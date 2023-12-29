<script setup lang="ts">import {ref, reactive, onMounted} from "vue";
import { graphic } from "echarts/core";
import { currentGET } from "@/api";
import Stomp from "stompjs";

const stompClient = ref(null); // STOMP客户端对象
const messages = ref([]); // 用于存储接收到的消息

let colors = ["#0BFC7F", "#A0A0A0", "#F48C02", "#F4023C"];
const option = ref({});
const state = reactive({
  lockNum: 0,
  offlineNum: 0,
  onlineNum: 0,
  alarmNum: 0,
  totalNum: 0,
});
const echartsGraphic = (colors: string[]) => {
  return new graphic.LinearGradient(1, 0, 0, 0, [
    { offset: 0, color: colors[0] },
    { offset: 1, color: colors[1] },
  ]);
};
  let url = '/biRealtimeController/getUserInfo'

  import request from "../../utils/request"; //实现http请求，通过相关协议实现前后端的拉通
  const getData_fromSP = () => {
    request.get(url).then((res) => {
      console.log(res);
      if (res.code==0) {
        state.lockNum = res.data.follow_count;
        state.offlineNum = res.data.reg_count;
        state.onlineNum = res.data.order_count;
        state.totalNum = res.data.ttl_count;;
        state.alarmNum = res.data.alarmNum;
        setOption();
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
    const subscription = stompClient.value.subscribe(`/topic/live-msg-userCount`, (message) => {
      const messageData =message.body;
      console.log("STOMP收到消息:", messageData);

      try {
        const parsedData = JSON.parse(messageData); // 解析JSON字符串为对象
        console.log(parsedData.code);

        if (parsedData.code === 0) {


          state.lockNum = parsedData.data.follow_count;
          state.offlineNum = parsedData.data.reg_count;
          state.onlineNum = parsedData.data.order_count;
          state.totalNum = parsedData.data.ttl_count;;
          state.alarmNum = parsedData.data.alarmNum;
          setOption();


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







onMounted(() => {
    console.log("-----Mounted---------");

  initWebSocket();
    // setInterval(() => {
    //   getData_fromSP();
    // }, 1500);
    // 初始化加载一次数据

  });
const setOption = () => {
  option.value = {
    title: {
      top: "center",
      left: "center",
      text: [`{value|${state.totalNum}}`, "{name|总数}"].join("\n"),
      textStyle: {
        rich: {
          value: {
            color: "#ffffff",
            fontSize: 24,
            fontWeight: "bold",
            lineHeight: 20,
            padding:[4,0,4,0]
          },
          name: {
            color: "#ffffff",
            lineHeight: 20,
          },
        },
      },
    },
    tooltip: {
      trigger: "item",
      backgroundColor: "rgba(0,0,0,.6)",
      borderColor: "rgba(147, 235, 248, .8)",
      textStyle: {
        color: "#FFF",
      },
    },
    series: [
      {
        name: "用户总览",
        type: "pie",
        radius: ["40%", "70%"],
        // avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: "rgba(255,255,255,0)",
          borderWidth: 2,
        },
        color: colors,
        label: {
          show: true,
          formatter: "   {b|{b}}   \n   {c|{c}个}   {per|{d}%}  ",
          //   position: "outside",
          rich: {
            b: {
              color: "#fff",
              fontSize: 12,
              lineHeight: 26,
            },
            c: {
              color: "#31ABE3",
              fontSize: 14,
            },
            per: {
              color: "#31ABE3",
              fontSize: 14,
            },
          },
        },
        emphasis: {
          show: false,
        },
        legend: {
          show: false,
        },
        tooltip: { show: true },

        labelLine: {
          show: true,
          length: 20, // 第一段线 长度
          length2: 36, // 第二段线 长度
          smooth: 0.2,
          lineStyle: {},
        },
        data: [
          {
            value: state.onlineNum,
            name: "关注",
            itemStyle: {
              color: echartsGraphic(["#0BFC7F", "#A3FDE0"]),
            },
          },
          {
            value: state.offlineNum,
            name: "注册",
            itemStyle: {
              color: echartsGraphic(["#A0A0A0", "#DBDFDD"]),
            },
          },
          {
            value: state.lockNum,
            name: "下单",
            itemStyle: {
              color: echartsGraphic(["#F48C02", "#FDDB7D"]),
            },
          },
          {
            value: state.alarmNum,
            name: "普通",
            itemStyle: {
              color: echartsGraphic(["#F4023C", "#FB6CB7"]),
            },
          },
        ],
      },
    ],
  };
};
</script>

<template>
  <v-chart class="chart" :option="option" />
</template>

<style scoped lang="scss"></style>
