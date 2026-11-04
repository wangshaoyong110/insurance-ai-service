import { createApp } from 'vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import App from './App.vue';
import router from './router';
import pinia from './stores';
import '@/styles/index.scss';

const app = createApp(App);

// 全局注册 Element Plus 图标,组件内可直接使用 <el-icon><User /></el-icon>
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component);
});

app.use(pinia);
app.use(router);
app.mount('#app');
