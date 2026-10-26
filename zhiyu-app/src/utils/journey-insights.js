// Compare only like-for-like records. Missing values are never treated as zero.
export function recordTime(record) {
  const value = String(record.date || '').replace(' ', 'T')
  return Date.parse(/[Zz]|[+-]\d\d:\d\d$/.test(value) ? value : value + '+08:00')
}
export function withinRange(records, days, now = Date.now()) {
  const cutoff = days ? now - days * 86400000 : -Infinity
  return records.filter(r => { const time = recordTime(r); return Number.isFinite(time) && time >= cutoff && time <= now })
    .slice().sort((a,b) => recordTime(a)-recordTime(b) || String(a.id).localeCompare(String(b.id)))
}
export function assessmentGroups(records) {
  const groups = new Map()
  for (const r of records) {
    if (r.kind !== 'ASSESSMENT' || !r.scale || !r.version || !Number.isFinite(r.score) || r.score < 0) continue
    const key = JSON.stringify([r.scale,r.version])
    if (!groups.has(key)) groups.set(key,{key,scale:r.scale,version:r.version,records:[]})
    groups.get(key).records.push(r)
  }
  return [...groups.values()]
}
export const moodLabels = ['很低落','有些低落','平静','还不错','很开心']
export function moodLabel(value) { return Number.isInteger(value) && value >= 1 && value <= 5 ? moodLabels[value-1] : '未记录' }
export function reflectionRecords(records) {
  return records.filter(r=>r.kind==='REFLECTION').slice().reverse().map(r=>({...r,beforeLabel:moodLabel(r.beforeMood),afterLabel:moodLabel(r.afterMood)}))
}
