/**
 * 二十四节气 + 每日「宜 / 忌」（首页黄历卡，v9 青花瓷 2026-11-23）
 * 节气日期取近年常见日（±1 天误差对展示无影响）；不联网、无随机：同一天恒得同一结果。
 */
const TERMS = [
	{ m: 1, d: 5, name: '小寒', line: '寒气渐深，适合在灯下做一件小物。', craft: '红纸剪窗花' },
	{ m: 1, d: 20, name: '大寒', line: '一年最冷的时候，手里有活，心就暖。', craft: '毛线编织杯垫' },
	{ m: 2, d: 4, name: '立春', line: '万物开始苏醒，给自己一个新的开头。', craft: '剪一枚春字' },
	{ m: 2, d: 19, name: '雨水', line: '雨落无声，慢慢润进土里。', craft: '扎染一方手帕' },
	{ m: 3, d: 5, name: '惊蛰', line: '春雷一响，心里的念头也醒了。', craft: '折纸小昆虫' },
	{ m: 3, d: 20, name: '春分', line: '昼夜平分，给忙与闲各留一半。', craft: '绣一片柳叶' },
	{ m: 4, d: 4, name: '清明', line: '天清地明，适合整理，也适合想念。', craft: '纸鸢小挂件' },
	{ m: 4, d: 20, name: '谷雨', line: '雨生百谷，也让心里长出点什么。', craft: '茶染书签' },
	{ m: 5, d: 5, name: '立夏', line: '日子变长，可以多留一点给自己。', craft: '编一条五彩绳' },
	{ m: 5, d: 21, name: '小满', line: '小满即安，不必太满。', craft: '陶泥小碟' },
	{ m: 6, d: 5, name: '芒种', line: '忙着播种，也别忘了歇一歇。', craft: '竹编小扇' },
	{ m: 6, d: 21, name: '夏至', line: '一年中白昼最长，光正好。', craft: '蓝染方巾' },
	{ m: 7, d: 7, name: '小暑', line: '热起来了，做点安静的事降降温。', craft: '团扇绘青花' },
	{ m: 7, d: 22, name: '大暑', line: '最热的时候，慢下来就好。', craft: '剪一池荷花' },
	{ m: 8, d: 7, name: '立秋', line: '暑气未消，秋意已在路上。', craft: '树叶拓印' },
	{ m: 8, d: 23, name: '处暑', line: '炎热到此为止，心也可以收一收。', craft: '绣一枚桂花' },
	{ m: 9, d: 7, name: '白露', line: '露凝而白，早晚记得添衣。', craft: '编织月饼挂袋' },
	{ m: 9, d: 23, name: '秋分', line: '秋色平分，把日子过得匀一些。', craft: '纸灯笼' },
	{ m: 10, d: 8, name: '寒露', line: '露水更凉，适合做一件温暖的东西。', craft: '毛毡小柿子' },
	{ m: 10, d: 23, name: '霜降', line: '霜打过的叶子，红得最好看。', craft: '枫叶书签' },
	{ m: 11, d: 7, name: '立冬', line: '冬天开始，把心事慢慢收藏。', craft: '编一顶小帽' },
	{ m: 11, d: 22, name: '小雪', line: '初雪将至，围炉做点小手工。', craft: '剪一朵雪花' },
	{ m: 12, d: 7, name: '大雪', line: '雪落满城，屋里正好安静。', craft: '赣绣小书签' },
	{ m: 12, d: 21, name: '冬至', line: '冬至一阳生，最长的夜之后，白天会回来。', craft: '画一幅九九消寒图' },
]

const YI = ['起针', '慢读', '散步', '整理桌面', '给朋友写信', '泡一壶茶', '剪纸', '早睡', '听雨', '描红', '晒太阳', '收拾心情']
const JI = ['赶工', '熬夜', '比较', '自责', '急躁', '久坐', '刷屏到深夜', '勉强自己']

/** 当前所处节气（取不晚于今天的最近一个） */
export function currentTerm(date = new Date()) {
	const md = (date.getMonth() + 1) * 100 + date.getDate()
	let hit = TERMS[TERMS.length - 1]
	for (const t of TERMS) if (t.m * 100 + t.d <= md) hit = t
	return hit
}

/** 下一个节气与相隔天数 */
export function nextTerm(date = new Date()) {
	const y = date.getFullYear()
	const today = new Date(y, date.getMonth(), date.getDate())
	for (const t of TERMS) {
		const day = new Date(y, t.m - 1, t.d)
		if (day > today) return { ...t, days: Math.round((day - today) / 86400000) }
	}
	const first = TERMS[0]
	const day = new Date(y + 1, first.m - 1, first.d)
	return { ...first, days: Math.round((day - today) / 86400000) }
}

/** 今日宜忌（按日期确定性挑选） */
export function almanac(date = new Date()) {
	const seed = date.getFullYear() * 372 + date.getMonth() * 31 + date.getDate()
	const yi = [YI[seed % YI.length], YI[(seed * 7 + 3) % YI.length]]
	if (yi[0] === yi[1]) yi[1] = YI[(seed + 5) % YI.length]
	return { yi, ji: [JI[(seed * 3) % JI.length]] }
}

const LUNAR_GREETING = ['夜深了', '早上好', '上午好', '中午好', '下午好', '傍晚好', '晚上好']
export function greeting(date = new Date()) {
	const h = date.getHours()
	return LUNAR_GREETING[h < 5 ? 0 : h < 9 ? 1 : h < 11 ? 2 : h < 13 ? 3 : h < 17 ? 4 : h < 19 ? 5 : 6]
}
