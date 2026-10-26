import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
const code=fs.readFileSync(new URL('../src/utils/journey-insights.js',import.meta.url),'utf8')
const {recordTime,withinRange,assessmentGroups,reflectionRecords}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
const now=Date.parse('2026-10-19T12:00:00+08:00')
const record=(id,kind='ASSESSMENT',extra={})=>({id,kind,date:'2026-10-18 10:00:00',scale:'PHQ9',version:'v1',score:3,...extra})
test('ranges use server Beijing time, reject invalid and future dates',()=>{
 assert.equal(recordTime(record('1')),Date.parse('2026-10-18T02:00:00Z'))
 const rows=[record('1'),record('2','ASSESSMENT',{date:'2026-09-01 10:00:00'}),record('3','ASSESSMENT',{date:'broken'}),record('4','ASSESSMENT',{date:'2099-01-01 00:00:00'})]
 assert.deepEqual(withinRange(rows,30,now).map(r=>r.id),['1'])
 assert.deepEqual(withinRange(rows,90,now).map(r=>r.id),['2','1'])
})
test('never mix scale/version, drafts, missing scores or program feedback',()=>{
 const rows=[record('1'),record('2','ASSESSMENT',{score:0}),record('3','ASSESSMENT',{scale:'GAD7'}),record('4','ASSESSMENT',{version:'v2'}),record('5','DRAFT'),record('6','PROGRAM_FEEDBACK'),record('7','ASSESSMENT',{score:null})]
 const groups=assessmentGroups(rows);assert.equal(groups.length,3);assert.deepEqual(groups[0].records.map(r=>r.score),[3,0])
})
test('reflection missing values stay missing and input is not mutated',()=>{
 const rows=[record('1','REFLECTION',{beforeMood:null,afterMood:4}),record('2','REFLECTION',{beforeMood:2,afterMood:0})]
 const results=reflectionRecords(rows);assert.equal(results[0].afterLabel,'未记录');assert.equal(results[1].beforeLabel,'未记录');assert.equal(results[1].afterLabel,'还不错');assert.equal(rows[0].beforeLabel,undefined)
})
