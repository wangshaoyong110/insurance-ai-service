import { fileURLToPath, URL } from 'node:url';
import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

/**
 * Vite 配置
 * - 多环境变量:loadEnv 读取 .env / .env.[mode]
 * - 本地代理:解决前后端分离跨域
 * - Element Plus 按需自动导入
 * - SCSS 全局变量注入
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080';

  return {
    base: '/',
    plugins: [
      vue(),
      // Vue / VueRouter / Pinia 与 Element Plus API 自动导入
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver()],
        dts: 'src/auto-imports.d.ts',
        eslintrc: { enabled: false },
      }),
      // Element Plus 组件自动按需注册
      Components({
        resolvers: [ElementPlusResolver()],
        dirs: ['src/components'],
        extensions: ['vue'],
        dts: 'src/components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          /**
           * 全局注入变量:
           * - src/styles 目录下的文件自行 @use 变量,跳过注入以避免模块重复加载
           * - 组件/页面样式统一在此注入,开发时无需手动引入
           */
          additionalData: (content: string, filePath: string) =>
            filePath.replace(/\\/g, '/').includes('/src/styles/')
              ? content
              : `@use "@/styles/variables.scss" as *;\n@use "@/styles/mixins.scss" as *;\n${content}`,
          api: 'modern-compiler',
        },
      },
    },
    server: {
      host: true,
      port: 5173,
      open: true,
      proxy: {
        // 业务接口: /dev-api/xxx -> http://127.0.0.1:8080/api/xxx
        '/dev-api': {
          target: proxyTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/dev-api/, '/api'),
        },
        // 上传文件的静态访问: /api/upload/** -> http://127.0.0.1:8080/api/upload/**
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    // 预览打包产物时的接口代理(npm run preview 使用)
    preview: {
      port: 4173,
      proxy: {
        '/dev-api': {
          target: proxyTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/dev-api/, '/api'),
        },
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      // 生产环境关闭 sourcemap,便于排查的测试环境可打开
      sourcemap: mode !== 'production',
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        // 多页面入口:index.html 管理端,mobile.html 客户端手机端
        input: {
          admin: fileURLToPath(new URL('./index.html', import.meta.url)),
          mobile: fileURLToPath(new URL('./mobile.html', import.meta.url)),
        },
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
          },
        },
      },
    },
  };
});
