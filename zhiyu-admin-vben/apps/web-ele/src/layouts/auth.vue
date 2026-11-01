<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router';
</script>

<template>
  <main class="auth-shell">
    <section class="auth-story">
      <RouterLink
        class="auth-brand zy-slide-up zy-stagger"
        :style="{ '--stagger-i': 0 }"
        to="/auth/login"
      >
        <img alt="织愈" src="/logo.svg" />
        <span>织愈<span class="brand-en">ZHIYU</span></span>
      </RouterLink>

      <div
        class="story-copy zy-slide-up zy-stagger"
        :style="{ '--stagger-i': 1 }"
      >
        <p>CRAFTING CONNECTIONS</p>
        <h1>让每一份创作，<br />连接真实的关怀。</h1>
        <span>一个工作空间，连接手作内容、校园活动与日常陪伴。</span>
      </div>

      <div
        class="story-art zy-slide-up zy-stagger"
        :style="{ '--stagger-i': 2 }"
      >
        <img alt="纸带交织成圆，象征创作与连接" src="/weave-scene.svg" />
        <span class="art-note">一针一线 · 一点一滴</span>
      </div>

      <footer>
        <span>内容运营 / 活动协作 / 数据洞察</span>
        <span>江西 · 南昌</span>
      </footer>
    </section>

    <section class="auth-form-side">
      <div class="access-label">
        <span></span> 织愈管理工作空间
      </div>
      <div
        class="auth-route zy-glass zy-slide-up zy-stagger"
        :style="{ '--stagger-i': 3 }"
      >
        <RouterView />
      </div>
      <footer>
        本系统不能替代专业心理咨询<br />
        <span>© 2026 织愈 · 用心连接每一份创作</span>
      </footer>
    </section>
  </main>
</template>

<style scoped>
.auth-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(400px, 1.1fr) minmax(440px, 1fr);
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
}

/* ── 左侧故事区：深靛底 + 漂移光斑 ── */
.auth-story {
  position: relative;
  background: color-mix(in srgb, var(--el-color-primary) 72%, black);
  color: var(--el-color-white);
  min-height: 100dvh;
  padding: 46px 56px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.auth-story::before {
  content: '';
  position: absolute;
  inset: -25% -15%;
  z-index: 0;
  background:
    radial-gradient(34rem 22rem at 18% 12%, rgb(255 255 255 / 12%), transparent 62%),
    radial-gradient(30rem 20rem at 82% 78%, hsl(var(--primary) / 55%), transparent 66%);
  animation: zyAuroraDrift 24s var(--zy-ease-in-out) infinite alternate;
  pointer-events: none;
}

/* 掠光扫过，比通用 .zy-sheen 更弱，避免在深底上闪白 */
.auth-story::after {
  content: '';
  position: absolute;
  inset: -40% -10% auto -55%;
  height: 180%;
  z-index: 0;
  background: linear-gradient(100deg, transparent 42%, rgb(255 255 255 / 8%) 50%, transparent 58%);
  transform: translate3d(-34%, 0, 0) rotate(6deg);
  animation: zyStorySheen 9s var(--zy-ease-in-out) infinite;
  pointer-events: none;
}

.auth-story > * {
  position: relative;
  z-index: 1;
}

@keyframes zyAuroraDrift {
  from {
    transform: translate3d(-3%, -2%, 0) scale(1);
  }
  to {
    transform: translate3d(5%, 4%, 0) scale(1.14);
  }
}

@keyframes zyStorySheen {
  0%,
  58% {
    transform: translate3d(-34%, 0, 0) rotate(6deg);
  }
  100% {
    transform: translate3d(122%, 0, 0) rotate(6deg);
  }
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 13px;
  font-size: 24px;
  letter-spacing: 3px;
  width: fit-content;
  color: inherit;
}
.auth-brand img {
  width: 44px;
  height: 44px;
}
.brand-en {
  display: block;
  font-size: 8px;
  letter-spacing: 5px;
  margin-top: 3px;
  color: rgb(255 255 255 / 84%);
}

.story-copy {
  margin-top: 70px;
}
.story-copy > p {
  font-size: 10px;
  letter-spacing: 4px;
  color: rgb(255 255 255 / 80%);
  margin-bottom: 24px;
}
.story-copy h1 {
  font-size: 42px;
  line-height: 1.6;
  letter-spacing: 2px;
  font-weight: 450;
  font-family: Georgia, 'Microsoft YaHei', serif;
}
.story-copy > span {
  display: block;
  color: rgb(255 255 255 / 86%);
  font-size: 13px;
  margin-top: 24px;
  line-height: 1.9;
  max-width: 360px;
}

.story-art {
  position: relative;
  flex: 1;
  min-height: 280px;
  display: grid;
  place-items: center;
}
.story-art img {
  width: 110%;
  max-width: 620px;
  filter: saturate(0.7);
  opacity: 0.95;
  animation: zyArtFloat 13s var(--zy-ease-in-out) infinite alternate;
}
.art-note {
  position: absolute;
  right: 0;
  bottom: 22px;
  writing-mode: vertical-rl;
  font-size: 11px;
  letter-spacing: 5px;
  color: rgb(255 255 255 / 82%);
}

@keyframes zyArtFloat {
  from {
    transform: translateY(-7px) rotate(-0.7deg);
  }
  to {
    transform: translateY(9px) rotate(0.7deg);
  }
}

.auth-story footer {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: rgb(255 255 255 / 78%);
  letter-spacing: 1px;
  border-top: 1px solid rgb(255 255 255 / 20%);
  padding-top: 22px;
}

/* ── 右侧表单区：环境光 + 玻璃卡片 ── */
.auth-form-side {
  padding: 40px 64px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  background-image: var(--zy-ambient);
  background-attachment: fixed;
  background-repeat: no-repeat;
  background-size: cover;
}

.access-label {
  position: absolute;
  top: 46px;
  right: 48px;
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 11px;
  color: var(--el-text-color-regular);
  letter-spacing: 1px;
}
.access-label > span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
  animation: zyLiveDot 2.8s var(--zy-ease-in-out) infinite;
}

@keyframes zyLiveDot {
  0%,
  100% {
    box-shadow: 0 0 0 0 hsl(var(--primary) / 42%);
  }
  55% {
    box-shadow: 0 0 0 7px hsl(var(--primary) / 0%);
  }
}

/* 玻璃面板比内容宽一档：内层表单仍保持原 370px 视觉宽度 */
.auth-route {
  max-width: 420px;
  width: 100%;
  margin: 80px 0 40px;
  padding: 34px 32px 28px;
  border-radius: calc(var(--radius) * 2.6);
  box-shadow:
    var(--zy-glass-shadow),
    var(--zy-pearl-ring),
    inset 0 1px 0 var(--zy-glass-inner);
}

.auth-form-side footer {
  text-align: center;
  font-size: 12px;
  line-height: 2.1;
  color: var(--el-text-color-regular);
}
.auth-form-side footer span {
  font-size: 10px;
  color: var(--el-text-color-regular);
}

@media (min-width: 1550px) {
  .auth-story {
    padding: 58px 80px;
  }
  .story-copy {
    margin-top: 90px;
  }
  .story-copy h1 {
    font-size: 50px;
  }
}

@media (max-width: 900px) {
  .auth-shell {
    /* 1fr 等价 minmax(auto,1fr)，会被 input 的固有宽度撑开；玻璃卡加内边距后就会横向溢出 */
    grid-template-columns: minmax(0, 1fr);
  }
  .auth-story {
    display: none;
  }
  .auth-form-side {
    min-width: 0;
    min-height: 100dvh;
    padding: 30px;
  }
  .auth-route {
    min-width: 0;
    margin: 70px 0 32px;
    padding: 26px 20px 22px;
  }
  .access-label {
    right: 30px;
    top: 32px;
  }
}

.auth-brand:focus-visible {
  outline: 3px solid var(--el-color-white);
  outline-offset: 6px;
}

@media (prefers-reduced-motion: reduce) {
  * {
    transition: none !important;
  }
  .auth-story::before,
  .auth-story::after,
  .story-art img,
  .access-label > span {
    animation: none !important;
  }
  .auth-story::after {
    opacity: 0;
  }
}
</style>
