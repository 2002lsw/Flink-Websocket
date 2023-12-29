

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import '@/assets/css/main.scss'
import '@/assets/css/tailwind.css'

import {registerEcharts} from "@/plugins/echarts"
//
 import { mockXHR } from "@/mock/index";
 mockXHR()

const app = createApp(App)
registerEcharts(app)
app.use(createPinia())
app.use(router)

app.mount('#app')
