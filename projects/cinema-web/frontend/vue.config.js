const { defineConfig } = require('@vue/cli-service')
// const AutoImport = require('unplugin-auto-import/webpack')
// const Components = require('unplugin-vue-components/webpack')
// const { ElementPlusResolver } = require('unplugin-vue-components/resolvers')

module.exports = defineConfig({
  publicPath: './', // 部署要用
  transpileDependencies: true,
  lintOnSave: false, // 关闭驼峰命名检查

  // 新增 CSS 配置：自动引入 SCSS 变量文件
  css: {
    loaderOptions: {
      scss: {
        additionalData: `@import "@/assets/styles/variables.scss";`
      }
    }
  }

  // 如果你以后需要 ElementPlus 自动引入功能，取消下面注释即可
  // configureWebpack: {
  //   plugins: [
  //     AutoImport({
  //       resolvers: [ElementPlusResolver()],
  //     }),
  //     Components({
  //       resolvers: [ElementPlusResolver()],
  //     }),
  //   ],
  // },
})