import type { RouteLocationNormalized, Router } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import { ElMessage } from 'element-plus';

import { accessRoutes, coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';
import { canAccessAdmin } from './role-access';

const ACCESS_DENIED_MESSAGE =
  '当前账号没有管理端访问权限。学生账号请使用学生端，其他账号请联系平台管理员。';

/**
 * 拒绝无管理端角色（学生 0 / 空角色 / 未知角色）的会话。
 *
 * 先清空本地会话再回登录页：登录页对「已登录」用户会自动跳回首页，
 * 若保留 token，会形成「拒绝 → 登录页 → 自动跳回 → 再拒绝」的无限重定向。
 * 守卫在 beforeEach 中返回 replace 定位，管理端布局不会被渲染。
 */
function redirectToLoginForDeniedRole(to: RouteLocationNormalized) {
  const accessStore = useAccessStore();
  const userStore = useUserStore();

  resetAllStores();
  // resetAllStores 依赖 pinia 已初始化；这里显式兜底，确保不会残留 token 造成重定向循环
  accessStore.setAccessToken(null);
  accessStore.setIsAccessChecked(false);
  userStore.setUserInfo(null);

  ElMessage.error({ message: ACCESS_DENIED_MESSAGE, duration: 5000 });

  return {
    path: LOGIN_PATH,
    query: { redirect: encodeURIComponent(to.fullPath) },
    replace: true,
  };
}

/**
 * 通用守卫配置
 * @param router
 */
function setupCommonGuard(router: Router) {
  // 记录已经加载的页面
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);

    // 页面加载进度条
    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    // 记录页面是否加载,如果已经加载，后续的页面切换动画等效果不在重复执行

    loadedPaths.add(to.path);

    // 关闭页面加载进度条
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 权限访问守卫配置
 * @param router
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();
    const userStore = useUserStore();
    const authStore = useAuthStore();

    // 基本路由，这些路由不需要进入权限拦截
    if (coreRouteNames.includes(to.name as string)) {
      if (to.path === LOGIN_PATH && accessStore.accessToken) {
        return decodeURIComponent(
          (to.query?.redirect as string) ||
            userStore.userInfo?.homePath ||
            preferences.app.defaultHomePath,
        );
      }
      return true;
    }

    // accessToken 检查
    if (!accessStore.accessToken) {
      // 明确声明忽略权限访问权限，则可以访问
      if (to.meta.ignoreAccess) {
        return true;
      }

      // 没有访问权限，跳转登录页面
      if (to.fullPath !== LOGIN_PATH) {
        return {
          path: LOGIN_PATH,
          // 如不需要，直接删除 query
          query:
            to.fullPath === preferences.app.defaultHomePath
              ? {}
              : { redirect: encodeURIComponent(to.fullPath) },
          // 携带当前跳转的页面，登录后重新跳转该页面
          replace: true,
        };
      }
      return to;
    }

    // 是否已经生成过动态路由
    if (accessStore.isAccessChecked) {
      // 会话期间角色被调整为无管理端权限时，不能继续使用已生成的路由
      if (userStore.userInfo && !canAccessAdmin(userStore.userInfo.roles)) {
        return redirectToLoginForDeniedRole(to);
      }
      return true;
    }

    // 生成路由表
    // 当前登录用户拥有的角色标识列表
    const userInfo = userStore.userInfo || (await authStore.fetchUserInfo());

    // token 有效但角色不在 {1,2,3}（含学生 0、空角色与未知角色）时，
    // 在生成管理端路由之前拦截，不渲染管理端布局
    if (!canAccessAdmin(userInfo?.roles)) {
      return redirectToLoginForDeniedRole(to);
    }

    const userRoles = userInfo.roles ?? [];

    // 生成菜单和路由
    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: userRoles,
      router,
      // 则会在菜单中显示，但是访问会被重定向到403
      routes: accessRoutes,
    });

    // 保存菜单信息和路由信息
    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);
    const redirectPath = (from.query.redirect ??
      (to.path === preferences.app.defaultHomePath
        ? userInfo.homePath || preferences.app.defaultHomePath
        : to.fullPath)) as string;

    const resolved = router.resolve(decodeURIComponent(redirectPath));
    // 角色权限收紧后，登录前的 redirect 可能指向当前角色已无权访问的路由，
    // 此时回落到该角色的 homePath，避免落到 404
    if (resolved.matched.length === 0) {
      return {
        ...router.resolve(
          userInfo.homePath || preferences.app.defaultHomePath,
        ),
        replace: true,
      };
    }

    return {
      ...resolved,
      replace: true,
    };
  });
}

/**
 * 项目守卫配置
 * @param router
 */
function createRouterGuard(router: Router) {
  /** 通用 */
  setupCommonGuard(router);
  /** 权限访问 */
  setupAccessGuard(router);
}

export { createRouterGuard };
