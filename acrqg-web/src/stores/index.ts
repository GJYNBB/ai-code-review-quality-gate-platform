import type { App } from 'vue'
import { createPinia } from 'pinia'

const pinia = createPinia()

/** 在 main.ts 中调用：注册 Pinia 到 Vue App */
export function setupStores(app: App): void {
    app.use(pinia)
}

export { pinia }
