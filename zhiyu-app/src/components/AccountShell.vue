<template>
  <!--
    AccountShell —— 登录 / 注册 / 找回密码外壳（v9.1 2026-11-23）。
    远山晨光头图 + 招手的小织 + 浮起的表单卡：第一眼是「来这里可以放松一下」，而不是一张冷冰冰的表单。
  -->
  <view class="account-shell">
    <view class="account-hero">
      <view class="hero-hills zy-wave-bg" aria-hidden="true"></view>
      <view class="hero-inner">
        <view class="hero-brand">
          <ZySeal text="织愈" size="md" />
          <view class="hero-brand-copy">
            <text class="hero-brand-name">织愈</text>
            <text class="hero-brand-caption">用一针一线，缝补青春的焦虑</text>
          </view>
        </view>
        <view class="hero-row">
          <view class="hero-copy">
            <text class="hero-title">累了，就来<br/>做一件小手作</text>
            <view class="hero-tags">
              <text class="hero-tag">聊一聊</text>
              <text class="hero-tag">做手作</text>
              <text class="hero-tag">记心情</text>
            </view>
          </view>
          <image class="hero-mascot" src="/static/mascot/xiaozhi-welcome.png" mode="aspectFit" aria-hidden="true" />
        </view>
      </view>
    </view>

    <view class="account-main">
      <view class="account-panel">
        <text v-if="eyebrow" class="account-eyebrow">{{ eyebrow }}</text>
        <text class="account-title" role="heading" aria-level="1">{{ title }}</text>
        <text v-if="description" class="account-description">{{ description }}</text>
        <slot />
      </view>
      <view class="account-footer">
        <text>按自己的节奏开始，也可以随时停下来。</text>
        <text>本系统不能替代专业心理咨询</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import ZySeal from './ZySeal.vue'
defineProps({ title: String, description: String, eyebrow: String })
</script>

<style scoped>
.account-shell { min-height: calc(100vh - var(--window-top, 0px)); background: var(--zy-bg, #f6f8fb); color: var(--zy-title, #141c2e); }

/* 头图：晨雾青 → 白，底部远山 */
.account-hero { position: relative; overflow: hidden; padding: calc(var(--status-bar-height, 0px) + 40rpx) 40rpx 140rpx; background: linear-gradient(180deg, #dfe8f7 0%, #eef3fb 55%, var(--zy-bg, #f6f8fb) 100%); }
.hero-hills { position: absolute; left: 0; right: 0; bottom: 0; height: 260rpx; opacity: 1; }
.hero-inner { position: relative; max-width: 960rpx; margin: 0 auto; }
.hero-brand { display: flex; align-items: center; gap: 18rpx; }
.hero-brand-copy { display: flex; flex-direction: column; }
.hero-brand-name { font-family: var(--zy-font-display); font-size: 40rpx; font-weight: 700; letter-spacing: 8rpx; }
.hero-brand-caption { font-size: 22rpx; color: var(--zy-muted, #586379); letter-spacing: 1rpx; }
.hero-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 16rpx; margin-top: 44rpx; }
.hero-copy { flex: 1; min-width: 0; }
.hero-title { display: block; font-family: var(--zy-font-display); font-size: 56rpx; font-weight: 700; line-height: 1.35; letter-spacing: 3rpx; color: var(--zy-brand-900, #0d1f45); }
.hero-tags { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 22rpx; }
.hero-tag { padding: 6rpx 20rpx; border-radius: 999rpx; background: rgba(255, 255, 255, .8); color: var(--zy-brand-700, #183369); font-size: 24rpx; font-weight: 600; box-shadow: 0 4rpx 12rpx rgba(36, 71, 143, .1); }
.hero-mascot { width: 220rpx; height: 220rpx; flex-shrink: 0; margin-bottom: -10rpx; }

/* 表单卡 */
.account-main { position: relative; z-index: 2; margin-top: -100rpx; padding: 0 28rpx calc(40rpx + env(safe-area-inset-bottom)); display: flex; flex-direction: column; align-items: center; }
.account-panel { width: 100%; max-width: 860rpx; box-sizing: border-box; padding: 44rpx 40rpx 36rpx; background: var(--zy-card-bg, #fff); border-radius: 44rpx; box-shadow: 0 20rpx 60rpx rgba(20, 36, 72, .12); }
.account-eyebrow { display: block; font-size: 24rpx; color: var(--zy-cinnabar, #c0392b); font-weight: 600; letter-spacing: 2rpx; margin-bottom: 8rpx; }
.account-title { display: block; font-family: var(--zy-font-display); font-size: 48rpx; font-weight: 700; line-height: 1.35; letter-spacing: 2rpx; }
.account-description { display: block; margin: 10rpx 0 32rpx; font-size: 27rpx; line-height: 1.7; color: var(--zy-text, #384256); }
.account-footer { margin-top: 32rpx; text-align: center; color: var(--zy-muted, #586379); font-size: 24rpx; line-height: 1.8; }
.account-footer text { display: block; }
.account-footer text + text { font-size: 22rpx; color: var(--zy-faint, #8a93a6); }

.zy-theme-dark .account-hero, .account-shell.zy-theme-dark .account-hero { background: linear-gradient(180deg, #1b2a4a 0%, #16213a 60%, var(--zy-bg) 100%); }
.zy-theme-dark .hero-title { color: var(--zy-title); }
.zy-theme-dark .hero-tag { background: rgba(255, 255, 255, .12); color: var(--zy-brand-700); }

@media (min-width: 900px) {
  .account-hero { padding-top: 56px; padding-bottom: 120px; }
  .hero-title { font-size: 44px; }
  .hero-mascot { width: 180px; height: 180px; }
  .account-panel { max-width: 460px; }
}
</style>
