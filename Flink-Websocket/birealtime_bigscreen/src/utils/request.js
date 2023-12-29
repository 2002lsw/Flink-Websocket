import axios from "axios";
import router from "../router";
const BASE_URL = "http://localhost:8076"; // Set your base URL here
const API_PREFIX = ""; // Replace '/api' with the desired prefix
const request =axios.create({
  timeout:5000, //超时时间
      baseURL: '/api'
})
//拦截器
request.interceptors.request.use(config => {
  //请求头的编码格式
  config.headers['Context-Type'] = 'application/json;charset=utf-8';

  let admin = sessionStorage.getItem("admin");
  let customer = sessionStorage.getItem("customer");
  let driver = sessionStorage.getItem("driver");


  if (!(null == admin && null == customer && null == driver)) {

  }else{
    router.push('/login')
  }

   //config.url = config.url.replace(/^\/api/, API_PREFIX);
  return config
},
  error => {
  return Promise.reject(error)
  }
);
//response 拦截器
//可以在接口响应后统一处理结果
request.interceptors.response.use(
  response =>{
    let res=response.data;
    //如果有返回的文件
    if(response.config.responseType ==='blob'){
      return res
    }
    //兼容服务端返回的字符串数据
    if(typeof res === 'string'){
      res = res ? JSON.parse(res) :res
    }
    return res;
  },
  error => {
    console.log('err'+ error) //for debug
    return Promise.reject(error)
  }
)
export  default  request