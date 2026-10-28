import { ref, watch } from 'vue';

import { preferences } from '@vben/preferences';

import { buildChartTheme } from '#/utils/chart-theme';

export function useChartTheme() {
  const root = document.documentElement;
  const read = (name: string) =>
    getComputedStyle(root).getPropertyValue(name).trim();
  const theme = ref(buildChartTheme(read));

  watch(
    () => preferences.theme,
    () => {
      theme.value = buildChartTheme(read);
    },
    { deep: true, flush: 'post' },
  );

  return { theme };
}
