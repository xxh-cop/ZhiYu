import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const code = fs.readFileSync(new URL('../src/utils/recommend-click-payload.js', import.meta.url), 'utf8')
const { buildRecommendationClickPayload } = await import(
	'data:text/javascript;base64,' + Buffer.from(code).toString('base64')
)

test('uses string IDs and allowed sources', () => {
	assert.deepEqual(
		buildRecommendationClickPayload(930000000000000001n, 940001n, 'HOME'),
		{ recommendationId: '930000000000000001', projectId: '940001', source: 'HOME' }
	)
	assert.equal(buildRecommendationClickPayload('rec', 'project', 'RECOMMEND').source, 'RECOMMEND')
})

test('skips missing IDs or unknown sources', () => {
	assert.equal(buildRecommendationClickPayload('', '1', 'HOME'), null)
	assert.equal(buildRecommendationClickPayload('1', '', 'HOME'), null)
	assert.equal(buildRecommendationClickPayload('1', '2', 'OTHER'), null)
})
