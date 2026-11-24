<template>
  <view class="account-shell">
    <view class="account-story">
      <view class="account-brand"><view class="account-brand-seal" role="img" aria-label="织愈">织</view><view><text>织愈</text><text class="brand-caption">用一针一线，缝补青春的焦虑</text></view></view>
      <view class="story-copy"><text class="story-label">手作 · 陪伴 · 日常</text><text class="story-title">从一针一线，<br/>回到自己的节奏。</text><text class="story-description">和小织聊聊，尝试一份手作，<br/>为今天留下一点属于自己的时光。</text></view>
      <image class="story-art" src="/static/brand/weave-scene.png" mode="aspectFit" aria-label="交织的纸带与叶形纹样"/>
      <view class="story-foot"><text>江西师范大学人工智能学院 · 校内试用</text><text>江西 · 南昌</text></view>
    </view>
    <view class="account-main">
      <view class="account-mobile-brand"><view class="account-mobile-seal" role="img" aria-label="织愈">织</view><text>织愈 · 创作与陪伴</text></view>
      <view class="account-panel">
        <view class="account-heading"><view><text class="account-eyebrow">{{eyebrow}}</text><text class="account-title" role="heading" aria-level="1">{{title}}</text></view><XiaotuanPet :size="72" face="calm" aria-hidden="true"/></view>
        <text class="account-description">{{description}}</text>
        <slot />
      </view>
      <view class="account-footer"><text>本系统不能替代专业心理咨询</text><text>按自己的节奏开始，也可以随时停下来。</text></view>
    </view>
  </view>
</template>
<script setup>
import XiaotuanPet from './XiaotuanPet.vue'
defineProps({title:String,description:String,eyebrow:String})
</script>
<style scoped>
.account-shell{min-height:calc(100vh - var(--window-top,0px));display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);background:var(--zy-bg,#f1f4f1);color:var(--zy-title,#111816);font-family:var(--zy-font,-apple-system,"Microsoft YaHei",sans-serif)}
.account-story{background:#183369;color:#f1f4f1;padding:42px 48px;display:flex;flex-direction:column;overflow:hidden;min-width:0}.account-brand{display:flex;align-items:center;gap:14px;font-size:26px;font-weight:500}.account-brand-seal{width:48px;height:48px;display:flex;align-items:center;justify-content:center;flex-shrink:0;border:1px solid #a64842;color:#e7b9ae;font-family:var(--zy-font-display,"Songti SC","STSong","Noto Serif CJK SC","SimSun",serif);font-size:22px;line-height:1}.account-brand text{display:block}.account-brand .brand-caption{font-size:12px;color:#b9cbc4;font-weight:400;margin-top:4px}.story-copy{margin-top:70px;position:relative;z-index:1}.story-label{display:block;font-size:12px;letter-spacing:3px;color:#b9cbc4}.story-title{display:block;font-family:var(--zy-font-display,"Songti SC","STSong","Noto Serif CJK SC","SimSun",serif);font-size:clamp(30px,3.1vw,48px);line-height:1.6;font-weight:500;margin-top:20px;letter-spacing:1px}.story-description{display:block;font-size:15px;line-height:1.9;color:var(--zy-border);margin-top:18px}.story-art{width:100%;height:250px;flex:1;min-height:200px;max-height:370px;margin:16px 0}.story-foot{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;border-top:1px solid #ffffff25;padding-top:20px;color:#b9cbc4;font-size:11px}
.account-main{padding:44px 44px 28px;display:flex;flex-direction:column;align-items:center;justify-content:center;min-width:0}.account-panel{width:100%;max-width:430px;background:var(--zy-card-bg,#ffffff);padding:32px;border:1px solid var(--zy-border,#d3ded8);border-radius: var(--zy-radius-control, 18rpx);box-sizing:border-box;box-shadow:none}.account-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.account-heading image{width:66px;height:82px;flex-shrink:0}.account-eyebrow{display:block;font-size:12px;letter-spacing:2px;color:var(--zy-muted,#6e7b75);margin-bottom:10px}.account-title{display:block;font-family:var(--zy-font-display,"Songti SC","STSong","Noto Serif CJK SC","SimSun",serif);font-size:30px;font-weight:500;line-height:1.4;letter-spacing:1px}.account-description{display:block;font-size:14px;line-height:1.8;color:var(--zy-text,#42524c);margin:8px 0 24px}.account-footer{text-align:center;color:var(--zy-muted,#6e7b75);font-size:12px;line-height:1.8;margin-top:24px}.account-footer text{display:block}.account-footer text+text{font-size:11px;margin-top:4px}.account-mobile-brand{display:none}
.account-mobile-seal{width:32px;height:32px;display:flex;align-items:center;justify-content:center;flex-shrink:0;border:1px solid var(--zy-care,#a64842);color:var(--zy-care,#a64842);font-family:var(--zy-font-display,"Songti SC","STSong","Noto Serif CJK SC","SimSun",serif);font-size:16px;line-height:1}
@media(max-width:900px){.account-shell{grid-template-columns:1fr}.account-story{display:none}.account-main{padding:24px 20px calc(24px + env(safe-area-inset-bottom));justify-content:flex-start}.account-mobile-brand{display:flex;align-items:center;gap:10px;width:100%;max-width:430px;margin:0 0 22px;font-size:15px;font-weight:600}.account-panel{padding:26px 24px}.account-title{font-size:26px}}
@media(max-width:360px){.account-main{padding-left:14px;padding-right:14px}.account-panel{padding:22px 18px}.account-title{font-size:24px}}
</style>
