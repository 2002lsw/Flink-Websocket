<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from "vue";
import { currentGET } from "@/api";
import {graphic} from "echarts/core"
const option = ref({});

import Stomp from "stompjs";

const stompClient = ref(null); // STOMP客户端对象
const messages = ref([]); // 用于存储接收到的消息

let url = '/biRealtimeController/findOrderTtlList'

import request from "../../utils/request"; //实现http请求，通过相关协议实现前后端的拉通

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
    const subscription = stompClient.value.subscribe(`/topic/live-msg-orderTtlWindowCount`, (message) => {
      const messageData =message.body;
      console.log("STOMP收到消息:", messageData);

      try {
        const parsedData = JSON.parse(messageData); // 解析JSON字符串为对象
        console.log(parsedData.code);

        if (parsedData.code === 0) {
          setOption(parsedData.data);
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

const getData = () => {
  request.get(url).then((res) => {
    console.log("实时订单分析", res);
    if (res.code==0) {
      setOption(res.data);
    } else {
      window["$message"]({
        text: res.msg,
        type: "warning",
      });
    }
  });
};
const setOption =async (newData: any) => {
  option.value = {
    tooltip: {
      trigger: "axis",
      backgroundColor: "rgba(0,0,0,.6)",
      borderColor: "rgba(147, 235, 248, .8)",
      textStyle: {
        color: "#FFF",
      },
      formatter: function (params: any) {
        // 添加单位
        var result = params[0].name + "<br>";
        params.forEach(function (item: any) {
          if (item.value) {
            if (item.seriesName == "安装率") {
              result +=
                item.marker +
                " " +
                item.seriesName +
                " : " +
                item.value +
                "%</br>";
            } else {
              result +=
                item.marker +
                " " +
                item.seriesName +
                " : " +
                item.value +
                "个</br>";
            }
          } else {
            result += item.marker + " " + item.seriesName + " :  - </br>";
          }
        });
        return result;
      },
    },
    legend: {
      data: ["已关注", "已下单", "下单率"],
      textStyle: {
        color: "#B4B4B4",
      },
      top: "0",
    },
    grid: {
      left: "50px",
      right: "40px",
      bottom: "30px",
      top: "20px",
    },
    xAxis: {
      data: newData.category,
      inverse:true,
      axisLine: {
        lineStyle: {
          color: "#B4B4B4",
        },
      },
      axisTick: {
        show: false,
      },
    },
    yAxis: [
      {
        splitLine: { show: false },
        axisLine: {
          lineStyle: {
            color: "#B4B4B4",
          },
        },

        axisLabel: {
          formatter: "{value}",
        },
      },
      {
        splitLine: { show: false },
        axisLine: {
          lineStyle: {
            color: "#B4B4B4",
          },
        },
        axisLabel: {
          formatter: "{value}% ",
        },
      },
    ],
    series: [
      {
        name: "已关注",
        type: "bar",
        barWidth: 10,
        itemStyle: {
          borderRadius: 5,
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: "#956FD4" },
            { offset: 1, color: "#3EACE5" },
          ]),
        },
        data: newData.barData,
      },
      {
        name: "已下单",
        type: "bar",
        barGap: "-100%",
        barWidth: 10,
        itemStyle: {
          borderRadius: 5,
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: "rgba(156,107,211,0.8)" },
            { offset: 0.2, color: "rgba(156,107,211,0.5)" },
            { offset: 1, color: "rgba(156,107,211,0.2)" },
          ]),
        },
        z: -12,
        data: newData.lineData,
      },
      {
        name: "下单率",
        type: "line",
        smooth: true,
        showAllSymbol: true,
        symbol: "emptyCircle",
        symbolSize: 8,
        yAxisIndex: 1,
        itemStyle: {
          color: "#F02FC2",
        },
        data: newData.rateData,
      },
    ],
  };
};
onMounted(() => {
  console.log("-----Mounted---------");
  initWebSocket();
  // setInterval(() => {

  //   getData();
  // }, 1500);
  // 初始化加载一次数据

});
</script>

<template>
  <v-chart class="chart" :option="option" v-if="JSON.stringify(option)!='{}'"/>
</template>

<style scoped lang="scss"></style>
