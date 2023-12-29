<script setup lang="ts">
  import {ref, reactive, onMounted} from "vue";
import CapsuleChart from "@/components/datav/capsule-chart";
import { currentGET } from "@/api";
  import Stomp from "stompjs";

  const stompClient = ref(null); // STOMP客户端对象
  const messages = ref([]); // 用于存储接收到的消息

const config = ref({
  showValue: true,
  unit: "次",
});
const data= ref([])

  let url = '/biRealtimeController/findOrderCityList8'

  import request from "../../utils/request"; //实现http请求，通过相关协议实现前后端的拉通
const getDataFromSP = () => {
  request.get(url).then((res) => {
    console.log("报警排名", res);
    if (res.code==0) {
      data.value =res.data;
    } else {
      window["$message"]({
        text: res.msg,
        type: "warning",
      });
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
      const subscription = stompClient.value.subscribe(`/topic/live-msg-deviceCityCount`, (message) => {
        const messageData =message.body;
        console.log("STOMP收到消息:", messageData);

        try {
          const parsedData = JSON.parse(messageData); // 解析JSON字符串为对象
          console.log(parsedData.code);

          if (parsedData.code === 0) {
            data.value =parsedData.data;

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
    //   getDataFromSP();
    // }, 1500);
    // 初始化加载一次数据

  });
</script>

<template>
  <div class="right_bottom">
    <CapsuleChart :config="config" style="width: 100%; height: 260px" :data="data"/>
  </div>
</template>

<style scoped lang="scss">

.right_bottom {
  box-sizing: border-box;
  padding: 0 16px;
}
</style>
