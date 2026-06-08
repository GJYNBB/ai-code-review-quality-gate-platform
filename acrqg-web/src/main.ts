import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from '@/App.vue'
import router from '@/router'
import { setupStores } from '@/stores'
import '@/styles/index.scss'

const app = createApp(App)

// 注册 Element Plus 图标（按需在模板中通过 component 标签使用）
for (const [iconName, iconComponent] of Object.entries(ElementPlusIconsVue)) {
    app.component(iconName, iconComponent as never)
}

app.use(ElementPlus)
setupStores(app)
app.use(router)

app.mount('#app')
