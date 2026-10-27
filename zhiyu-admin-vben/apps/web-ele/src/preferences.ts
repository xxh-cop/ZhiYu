import {
  defineOverridesPreferences,
} from '@vben/preferences';

/**
 * @description 项目配置文件
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    name: import.meta.env.VITE_APP_TITLE,
    // 「织愈」后端无刷新 token 接口，关闭自动刷新
    enableRefreshToken: false,
    // 使用前端静态路由（「织愈」后端无菜单接口）
    accessMode: 'frontend',
    // 默认登录后跳转路径
    defaultHomePath: '/zhiyu/dashboard',
  },
  theme: {
    mode: 'light',
    // 自定义品牌色必须显式声明 custom，否则抽屉高亮与实际色值不一致
    builtinType: 'custom',
    colorPrimary: '#4F46E5',
    colorSuccess: '#047857',
    colorWarning: '#B45309',
    colorDestructive: '#DC2626',
    radius: '0.5',
  },
  copyright: {
    companyName: '织愈',
    companySiteLink: '',
    date: '2026',
    enable: true,
    icp: '',
    icpLink: '',
    settingShow: true,
  },
  logo: {
    source: '/logo.png',
    sourceDark: '/logo.png',
  },
});
