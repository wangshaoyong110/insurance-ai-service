import { createApp } from 'vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import App from './App.vue';
import router from './router';
import pinia from './stores';
import { installErrorHandler } from './utils/errorHandler';
import '@/styles/index.scss';

const app = createApp(App);

// 全局注册 Element Plus 图标,组件内可直接使用 <el-icon><User /></el-icon>
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component);
});

// 未捕获异常兜底:避免组件报错后直接白屏且用户无任何提示
installErrorHandler(app, 'admin');

app.use(pinia);
app.use(router);
app.mount('#app');
