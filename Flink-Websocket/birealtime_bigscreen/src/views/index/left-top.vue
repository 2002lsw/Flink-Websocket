<script setup lang="ts">
import { reactive, ref ,onMounted} from "vue";
import { currentGET } from "@/api";
import CountUp from "@/components/count-up";
import {GET} from "@/api/api";

import Stomp from "stompjs";

const stompClient = ref(null); // STOMP客户端对象
const messages = ref([]); // 用于存储接收到的消息


const duration = ref(2);
const state = reactive({
  alarmNum: 759,
  offlineNum: 44,
  onlineNum: 654,
  totalNum: 698,
});


let url = '/biRealtimeController/getDeviceInfo'

import request from "../../utils/request"; //实现http请求，通过相关协议实现前后端的拉通
const getData_fromSP = () => {
  request.get(url).then((res) => {
    console.log(res);
    if (res.code==0) {
      state.alarmNum = res.data.warn_count;
      state.offlineNum = res.data.offline_count;
      state.onlineNum = res.data.online_count;
      state.totalNum = res.data.ttl_count;
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
    const subscription = stompClient.value.subscribe(`/topic/live-msg-deviceCount`, (message) => {
      const messageData =message.body;
      console.log("STOMP收到消息:", messageData);

      try {
        const parsedData = JSON.parse(messageData); // 解析JSON字符串为对象
        console.log(parsedData.code);

        if (parsedData.code === 0) {


          state.alarmNum = parsedData.data.warn_count;
          state.offlineNum = parsedData.data.offline_count;
          state.onlineNum = parsedData.data.online_count;
          state.totalNum = parsedData.data.ttl_count;


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


//
// const refreshData = () => {
//   getData();
// };
//
// setTimeout(refreshData, 1000)

//const timer = setInterval(refreshData, 1000);


// 在组件卸载时清除定时器
// onUnmounted(() => {
//   clearInterval(timer);
// });


onMounted(() => {
  console.log("-----Mounted---------");
  initWebSocket();
  // setInterval(() => {
  //   getData_fromSP();
  // }, 1500);
  // 初始化加载一次数据

});


</script>





<template>
  <ul class="user_Overview flex">
    <li class="user_Overview-item" style="color: #00fdfa">
      <div class="user_Overview_nums allnum">
        <CountUp :endVal="state.totalNum" :duration="duration" />
      </div>
      <p>总设备数</p>
    </li>
    <li class="user_Overview-item" style="color: #07f7a8">
      <div class="user_Overview_nums online">
        <CountUp :endVal="state.onlineNum" :duration="duration" />
      </div>
      <p>在线数</p>
    </li>
    <li class="user_Overview-item" style="color: #e3b337">
      <div class="user_Overview_nums offline">
        <CountUp :endVal="state.offlineNum" :duration="duration" />
      </div>
      <p>掉线数</p>
    </li>
    <li class="user_Overview-item" style="color: #f5023d">
      <div class="user_Overview_nums laramnum">
        <CountUp :endVal="state.alarmNum" :duration="duration" />
      </div>
      <p>告警次数</p>
    </li>
  </ul>
</template>

<style scoped lang="scss">
.left-top {
  width: 100%;
  height: 100%;
}

.user_Overview {
  li {
    flex: 1;

    p {
      text-align: center;
      height: 16px;
      font-size: 16px;
    }

    .user_Overview_nums {
      width: 100px;
      height: 100px;
      text-align: center;
      line-height: 100px;
      font-size: 22px;
      margin: 50px auto 30px;
      background-size: cover;
      background-position: center center;
      position: relative;

      &::before {
        content: "";
        position: absolute;
        width: 100%;
        height: 100%;
        top: 0;
        left: 0;
      }

      &.bgdonghua::before {
        animation: rotating 14s linear infinite;
      }
    }

    .allnum {
      &::before {
        background-image: url("@/assets/img/left_top_lan.png");
      }
    }

    .online {
      &::before {
        background-image: url("@/assets/img/left_top_lv.png");
      }
    }

    .offline {
      &::before {
        background-image: url("@/assets/img/left_top_huang.png");
      }
    }

    .laramnum {
      &::before {
        background-image: url("@/assets/img/left_top_hong.png");
      }
    }
  }
}
</style>
