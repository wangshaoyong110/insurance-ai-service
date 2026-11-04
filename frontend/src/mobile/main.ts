import { createApp } from 'vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import MobileApp from './App.vue';
import router from './router';
import pinia from '@/stores';
import '@/styles/index.scss';
import './styles/mobile.scss';

/**
 * 客户端(手机端 H5)入口
 * <p>与后台管理端是两个独立的页面:mobile.html 入口、独立路由、独立登录态</p>
 */
const app = createApp(MobileApp);

// 全局注册 Element Plus 图标,模板中可直接使用 <el-icon><Umbrella /></el-icon>
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component);
});

app.use(pinia);
app.use(router);
app.mount('#app');
