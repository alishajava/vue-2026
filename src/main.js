import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-alpine.css'

// Chart.js 공통 register: 앱 전체에서 이 한 번의 import로 필요한 컨트롤러/엘리먼트가
// register 된다. 각 차트 컴포넌트는 이걸 다시 import하거나, 이미 register된 Chart.js를
// 그대로 사용하는 vue-chartjs 컴포넌트를 사용하면 된다.
import './charts/chartRegister'

import './styles/global.css'
import App from './App.vue'

createApp(App).use(Antd).mount('#app')
