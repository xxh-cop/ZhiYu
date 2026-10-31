import { request } from './request'

export const PRACTICE_KIND_LABELS = {
	HERITAGE: '非遗手作',
	CRAFT: '手作',
	MINDFULNESS: '正念',
	DRAWING: '绘色',
}

const ACTIVE_STATUSES = [0, 1, 2, 3, 4]

export function practiceKindLabel(kind) {
	return PRACTICE_KIND_LABELS[kind] || '练习'
}

export function isActivePractice(session) {
	return !!session && ACTIVE_STATUSES.includes(Number(session.status))
}

export function createPracticeSession(payload) {
	return request({ url: '/api/practice/sessions', method: 'POST', data: payload })
}

export function getPracticeSession(id) {
	return request({ url: '/api/practice/sessions/' + encodeURIComponent(id), method: 'GET' })
}

export function listPracticeSessions(limit = 5) {
	return request({ url: '/api/practice/sessions?limit=' + encodeURIComponent(limit), method: 'GET' })
}

export function transitionPracticeSession(id, action) {
	return request({
		url: '/api/practice/sessions/' + encodeURIComponent(id) + '/status',
		method: 'PUT',
		data: { action },
	})
}

export function getPracticeArtifact(id) {
	return request({
		url: '/api/practice/sessions/' + encodeURIComponent(id) + '/artifact',
		method: 'GET',
	})
}

export function savePracticeArtifact(id, payload) {
	return request({
		url: '/api/practice/sessions/' + encodeURIComponent(id) + '/artifact',
		method: 'POST',
		data: payload,
	})
}

export function deletePracticeArtifact(id) {
	return request({
		url: '/api/practice/sessions/' + encodeURIComponent(id) + '/artifact',
		method: 'DELETE',
	})
}
