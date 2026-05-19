/**
 * 生成 UUID v4。
 * 优先使用 Web Crypto API（标准且性能更好），降级到 Math.random 实现。
 */
export function uuidv4(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID()
    }
    // RFC 4122 v4 的简易实现，仅在不支持 crypto.randomUUID 的旧环境使用
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        const v = c === 'x' ? r : (r & 0x3) | 0x8
        return v.toString(16)
    })
}
