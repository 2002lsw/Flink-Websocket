<script setup lang="ts">
import { ref,onMounted} from "vue";
import { currentGET } from "@/api";
import {graphic} from "echarts/core"
const option = ref({});
import Stomp from "stompjs";

const stompClient = ref(null); // STOMP客户端对象
const messages = ref([]); // 用于存储接收到的消息


let url = '/biRealtimeController/findOrderList'

import request from "../../utils/request";
const getDataFromSP = () => {
  request.get(url).then((res) => {
    console.log("报警次数 ", res);
    if (res.code==0) {
      setOption(res.data.dateList, res.data.numList, res.data.numList2);
    } else {
      window["$message"]({
        text: res.msg,
        type: "warning",
      });
    }
  });
};
const setOption =async (xData:any[], yData:any[], yData2:any[]) => {
  option.value = {
        xAxis: {
          type: "category",
          data: xData,
          inverse:true,
          boundaryGap: false, // 不留白，从原点开始
          splitLine: {
            show: true,
            lineStyle: {
              color: "rgba(31,99,163,.2)",
            },
          },
          axisLine: {
            // show:false,
            lineStyle: {
              color: "rgba(31,99,163,.1)",
            },
          },
          axisLabel: {
            color: "#7EB7FD",
            fontWeight: "500",
          },
        },
        yAxis: {
          type: "value",
          splitLine: {
            show: true,
            lineStyle: {
              color: "rgba(31,99,163,.2)",
            },
          },
          axisLine: {
            lineStyle: {
              color: "rgba(31,99,163,.1)",
            },
          },
          axisLabel: {
            color: "#7EB7FD",
            fontWeight: "500",
          },
        },
        tooltip: {
          trigger: "axis",
          backgroundColor: "rgba(0,0,0,.6)",
          borderColor: "rgba(147, 235, 248, .8)",
          textStyle: {
            color: "#FFF",
          },
        },
        grid: {
          //布局
          show: true,
          left: "10px",
          right: "30px",
          bottom: "10px",
          top: "32px",
          containLabel: true,
          borderColor: "#1F63A3",
        },
        series: [
          {
            data: yData,
            type: "line",
            smooth: true,
            symbol: "none", //去除点
            name: "报警1次数",
            color: "rgba(252,144,16,.7)",
            areaStyle: {
                //右，下，左，上
                color: new graphic.LinearGradient(
                  0,
                  0,
                  0,
                  1,
                  [
                    {
                      offset: 0,
                      color: "rgba(252,144,16,.7)",
                    },
                    {
                      offset: 1,
                      color: "rgba(252,144,16,.0)",
                    },
                  ],
                  false
                ),
            },
            markPoint: {
              data: [
                {
                  name: "最大值",
                  type: "max",
                  valueDim: "y",
                  symbol: "rect",
                  symbolSize: [60, 26],
                  symbolOffset: [0, -20],
                  itemStyle: {
                    color: "rgba(0,0,0,0)",
                  },
                  label: {
                    color: "#FC9010",
                    backgroundColor: "rgba(252,144,16,0.1)",
                    borderRadius: 6,
                    padding: [7, 14],
                    borderWidth: 0.5,
                    borderColor: "rgba(252,144,16,.5)",
                    formatter: "报警1：{c}",
                  },
                },
                {
                  name: "最大值",
                  type: "max",
                  valueDim: "y",
                  symbol: "circle",
                  symbolSize: 6,
                  itemStyle: {
                    color: "#FC9010",
                    shadowColor: "#FC9010",
                    shadowBlur: 8,
                  },
                  label: {
                    formatter: "",
                  },
                },
              ],
            },
          },
          {
            data: yData2,
            type: "line",
            smooth: true,
            symbol: "none", //去除点
            name: "报警2次数",
            color: "rgba(9,202,243,.7)",
            areaStyle: {
                //右，下，左，上
                color: new graphic.LinearGradient(
                  0,
                  0,
                  0,
                  1,
                  [
                    {
                      offset: 0,
                      color: "rgba(9,202,243,.7)",
                    },
                    {
                      offset: 1,
                      color: "rgba(9,202,243,.0)",
                    },
                  ],
                  false
                ),
            },
            markPoint: {
              data: [
                {
                  name: "最大值",
                  type: "max",
                  valueDim: "y",
                  symbol: "rect",
                  symbolSize: [60, 26],
                  symbolOffset: [0, -20],
                  itemStyle: {
                    color: "rgba(0,0,0,0)",
                  },
                  label: {
                    color: "#09CAF3",
                    backgroundColor: "rgba(9,202,243,0.1)",

                    borderRadius: 6,
                    borderColor: "rgba(9,202,243,.5)",
                    padding: [7, 14],
                    formatter: "报警2：{c}",
                    borderWidth: 0.5,
                  },
                },
                {
                  name: "最大值",
                  type: "max",
                  valueDim: "y",
                  symbol: "circle",
                  symbolSize: 6,
                  itemStyle: {
                    color: "#09CAF3",
                    shadowColor: "#09CAF3",
                    shadowBlur: 8,
                  },
                  label: {
                    formatter: "",
                  },
                },
              ],
            },
          },
        ],
      };
}

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
    const subscription = stompClient.value.subscribe(`/topic/live-msg-orderWindowCount`, (message) => {
      const messageData =message.body;
      console.log("STOMP收到消息:", messageData);

      try {
        const parsedData = JSON.parse(messageData); // 解析JSON字符串为对象
        console.log(parsedData.code);

        if (parsedData.code === 0) {
            setOption(parsedData.data.dateList, parsedData.data.numList, parsedData.data.numList2);
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
  initWebSocket()
  // setInterval(() => {
  //   getDataFromSP();
  // }, 1500);
  // 初始化加载一次数据

});
</script>

<template>
  <v-chart
    class="chart"
    :option="option"
    v-if="JSON.stringify(option) != '{}'"
  />
</template>

<style scoped lang="scss"></style>
