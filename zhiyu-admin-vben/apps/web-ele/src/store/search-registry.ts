import { computed, ref } from 'vue';

import { defineStore } from 'pinia';

/**
 * 全局搜索的实体注册表（AB2）。
 * 只收集「本会话内前端已加载过的列表行」，不查远端：
 * 列表页每次拉取成功后把当前页行注册进来，命令面板据此把
 * 作品 / 用户 / 小组实体与菜单一起匹配，选中后跳对应列表页并带 query。
 *
 * 落点全部复用页面已支持的 query 定位：
 * - 用户 → /zhiyu/business/users?keyword=<登录名>
 * - 作品 → /zhiyu/business/works?workId=<id>（直达详情抽屉）
 * - 小组 → /zhiyu/business/groups?groupId=<id>（行定位高亮）
 */
export type SearchEntityKind = 'group' | 'user' | 'work';

export interface SearchEntityInput {
  id: number | string;
  /** 展示名（作品标题 / 用户昵称 / 小组名称） */
  label: string;
  /** 跳转时使用的 query 值；缺省用 id */
  query?: string;
}

interface SearchEntity extends SearchEntityInput {
  id: string;
  kind: SearchEntityKind;
}

const KIND_META: Record<
  SearchEntityKind,
  { icon: string; tag: string; to: (entity: SearchEntity) => string }
> = {
  user: {
    icon: 'lucide:user',
    tag: '用户',
    to: (e) =>
      `/zhiyu/business/users?keyword=${encodeURIComponent(e.query ?? e.id)}`,
  },
  work: {
    icon: 'lucide:image',
    tag: '作品',
    to: (e) => `/zhiyu/business/works?workId=${encodeURIComponent(e.id)}`,
  },
  group: {
    icon: 'lucide:users-round',
    tag: '小组',
    to: (e) => `/zhiyu/business/groups?groupId=${encodeURIComponent(e.id)}`,
  },
};

export const useSearchRegistryStore = defineStore(
  'zhiyu-search-registry',
  () => {
    const entities = ref<Record<string, SearchEntity>>({});
    /** 内容版本号：变化时命令面板重建索引（其索引只在挂载时读取一次） */
    const version = ref(0);

    function register(kind: SearchEntityKind, items: SearchEntityInput[]) {
      let changed = false;
      for (const item of items) {
        if (item.id === null || item.id === undefined || item.id === '') {
          continue;
        }
        const key = `${kind}:${item.id}`;
        const label = item.label || String(item.id);
        const existing = entities.value[key];
        if (
          !existing ||
          existing.label !== label ||
          existing.query !== item.query
        ) {
          entities.value[key] = {
            kind,
            id: String(item.id),
            label,
            query: item.query,
          };
          changed = true;
        }
      }
      if (changed) version.value += 1;
    }

    /** 转成 MenuRecordRaw 形状的扁平条目，与菜单一起喂给搜索面板 */
    const asMenus = computed(() =>
      Object.values(entities.value).map((entity) => {
        const meta = KIND_META[entity.kind];
        return {
          name: `${meta.tag} · ${entity.label}`,
          path: meta.to(entity),
          icon: meta.icon,
        };
      }),
    );

    return { register, asMenus, version };
  },
);
