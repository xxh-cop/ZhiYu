<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';

import { useAccessStore } from '@vben/stores';

import { apiBaseURL, requestClient } from '#/api/request';

/**
 * 步骤引导音频上传（AW2 2026-11-05，对接后端 AU3）。
 *
 * 契约口径（POST /api/files/upload/audio）：
 * - 仅传承人（role=1）与管理员（role=3）——可见性由父级按角色控制，本组件不重复判；
 * - 服务端魔数嗅探 MP3/WAV/M4A/OGG，≤10MB，每用户每日 20 次配额；
 * - 失败展示后端原文（配额/格式/权限），不吞错；
 * - 成功返回私有媒体引用 /api/media/{id}/content，写入 craft_step.audio_url。
 *
 * 播放校验：上传成功后经 /media/{id}/playback 换签名地址，
 * 以 Range 首字节 GET 确认 HTTP 200/206 + audio/*（content 端点仅 GET 匿名放行），再给 <audio> 试听。
 */
const props = defineProps<{
  /** 音频引用（/api/media/{id}/content 或已授权地址）；空串表示未设置 */
  modelValue?: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: string];
  busy: [value: boolean];
}>();

const access = useAccessStore();
const progress = ref(0);
const busy = ref(false);
const error = ref('');
const input = ref<HTMLInputElement>();

/** 试听解析 */
const previewUrl = ref('');
const previewLoading = ref(false);
const verifyNote = ref('');
const verifyOk = ref(false);

let request: XMLHttpRequest | null = null;
let resolveTurn = 0;

const ACCEPT = '.mp3,.wav,.m4a,.ogg';
const MAX_SIZE = 10 * 1024 * 1024;

function cancel() {
  const old = request;
  request = null;
  old?.abort();
  busy.value = false;
  emit('busy', false);
}

function choose(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0];
  if (!file) return;
  if (!/\.(mp3|wav|m4a|ogg)$/i.test(file.name) || file.size > MAX_SIZE) {
    error.value = '请选择不超过 10MB 的 MP3/WAV/M4A/OGG 音频';
    return;
  }
  cancel();
  busy.value = true;
  emit('busy', true);
  error.value = '';
  progress.value = 0;

  const xhr = new XMLHttpRequest();
  request = xhr;
  xhr.open('POST', `${apiBaseURL}/files/upload/audio`);
  xhr.setRequestHeader('Authorization', `Bearer ${access.accessToken}`);
  xhr.timeout = 120_000;
  xhr.upload.onprogress = (ev) => {
    if (ev.lengthComputable) progress.value = Math.round((ev.loaded / ev.total) * 100);
  };
  xhr.onload = () => {
    if (request !== xhr) return;
    try {
      const result = JSON.parse(xhr.responseText);
      if (xhr.status >= 400 || result.code !== 0) {
        // 后端原文（配额超限 / 格式不符 / 权限不足），不吞不改写
        throw new Error(result.message || '上传失败');
      }
      emit('update:modelValue', result.data.url);
    } catch (err) {
      error.value = err instanceof Error ? err.message : '上传失败';
    } finally {
      cancel();
      if (input.value) input.value.value = '';
    }
  };
  xhr.onerror = xhr.ontimeout = () => {
    if (request === xhr) {
      error.value = '上传失败，请检查网络后重新选择文件。';
      cancel();
    }
  };
  const form = new FormData();
  form.append('file', file);
  xhr.send(form);
}

/** 签名地址可播性校验：Range 首字节 GET（content 端点仅 GET 匿名放行，HEAD 会被安全链 401） */
async function verifyPlayable(url: string): Promise<{ ok: boolean; note: string }> {
  try {
    const res = await fetch(url, { headers: { Range: 'bytes=0-1' } });
    const type = res.headers.get('content-type') || '';
    const playable = (res.status === 200 || res.status === 206) && type.toLowerCase().startsWith('audio/');
    return playable
      ? { ok: true, note: `签名地址已验证可播（HTTP ${res.status} · ${type}）` }
      : { ok: false, note: `签名地址校验未通过（HTTP ${res.status} · ${type || '未知类型'}），可尝试重新上传。` };
  } catch {
    return { ok: false, note: '签名地址校验请求失败，请检查网络后重试。' };
  }
}

/** 解析试听地址：私有媒体引用经 playback 换签名地址；外链/静态地址原样使用 */
async function resolvePreview(value: string) {
  const turn = ++resolveTurn;
  previewUrl.value = '';
  verifyNote.value = '';
  verifyOk.value = false;
  if (!value) return;
  previewLoading.value = true;
  try {
    const match = value.match(/^\/api\/media\/(\d+)\/content/);
    const url = match
      ? (await requestClient.get<{ url: string }>(`/media/${match[1]}/playback`)).url
      : value;
    if (turn !== resolveTurn) return;
    previewUrl.value = url;
    const v = await verifyPlayable(url);
    if (turn !== resolveTurn) return;
    verifyOk.value = v.ok;
    verifyNote.value = v.note;
  } catch {
    if (turn !== resolveTurn) return;
    verifyNote.value = '试听地址解析失败，可保存后重试或重新上传。';
  } finally {
    if (turn === resolveTurn) previewLoading.value = false;
  }
}

watch(() => props.modelValue, (v) => resolvePreview(v || ''), { immediate: true });
onBeforeUnmount(() => {
  cancel();
  resolveTurn++;
});
</script>

<template>
  <section class="audio-upload">
    <input
      ref="input"
      type="file"
      :accept="ACCEPT"
      :disabled="disabled || busy"
      aria-label="选择引导音频"
      @change="choose"
    />
    <p class="hint">MP3 / WAV / M4A / OGG · 最大 10MB · 每日 20 次；音频与项目一起审核。</p>
    <div v-if="busy" role="status" class="uploading">
      <progress :value="progress" max="100" /> {{ progress }}%
      <button type="button" @click="cancel">取消上传</button>
    </div>
    <p v-if="error" role="alert" class="error">{{ error }}</p>

    <template v-if="modelValue && !busy">
      <p v-if="previewLoading" class="hint">正在解析试听地址…</p>
      <template v-else-if="previewUrl">
        <audio :src="previewUrl" controls preload="none" class="player" />
        <p v-if="verifyNote" class="hint" :class="{ error: !verifyOk }">{{ verifyNote }}</p>
      </template>
      <button
        v-if="!disabled"
        type="button"
        class="remove"
        @click="emit('update:modelValue', '')"
      >
        移除音频
      </button>
    </template>
  </section>
</template>

<style scoped>
.audio-upload {
  width: 100%;
  padding: 14px 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 12px;
  background: var(--el-fill-color-extra-light);
}
.hint {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
  margin: 8px 0 0;
}
.error {
  color: var(--el-color-danger);
}
.uploading {
  margin-top: 8px;
  font-size: 12px;
}
.uploading button {
  margin-left: 8px;
  color: var(--el-color-primary);
}
progress {
  accent-color: var(--el-color-primary);
  width: 220px;
}
.player {
  display: block;
  width: 100%;
  margin-top: 10px;
}
.remove {
  margin-top: 10px;
  padding: 6px 12px;
  color: var(--el-color-primary);
}
</style>
