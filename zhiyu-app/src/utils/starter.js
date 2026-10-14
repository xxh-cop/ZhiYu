// Deterministic recommendation, using only choices made on this page.
export function filterStarter(items, { minutes=20, medium='全部', place='宿舍桌面', noScissors=false }={}) {
 return items.filter(item=>Number(item.minutes)<=minutes && (medium==='全部'||item.medium===medium)
  && item.places.includes(place) && (!noScissors||!item.tools.includes('剪刀')))
  .sort((a,b)=>Number(a.minutes)-Number(b.minutes)||String(a.id).localeCompare(String(b.id)))
}
export function starterReason(item, options) {
 return `约 ${item.minutes} 分钟，适合${options.place}；${item.medium}材料${item.tools.includes('剪刀')?'，需要剪刀':'，无需剪刀'}`
}
