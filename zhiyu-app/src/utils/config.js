/**
 * 织愈前端集中配置。
 *
 * 换网络时只需改这一处（API_BASE_URL 的局域网 IP）。
 * 查看本机局域网 IP：ipconfig | Select-String "IPv4"
 */

// #ifdef MP-WEIXIN
export const API_BASE_URL = 'http://192.168.0.102:8080'
// #endif

// #ifndef MP-WEIXIN
export const API_BASE_URL = 'http://localhost:8080'
// #endif

export const TOKEN_KEY = 'token'
export const REQUEST_TIMEOUT = 30000
