import test from 'node:test'
import assert from 'node:assert/strict'
import { createRegistrationFlow } from '../src/utils/registration-flow.js'

test('registration succeeds once; a failed login retries only login with the created account', async () => {
  const calls = []
  let created = 0, login = 0
  const flow = createRegistrationFlow(async options => {
    calls.push(options)
    if (options.url.endsWith('/login') && ++login === 1) throw new Error('network')
    return { token: 'test-token' }
  }, () => { created++ })
  await assert.rejects(flow.submit({ username: 'student', password: 'first-password' }), /network/)
  assert.equal(created, 1)
  const result = await flow.submit({ username: 'accidentally-changed', password: 'correct-password' })
  assert.equal(result.token, 'test-token')
  assert.deepEqual(calls.map(c => c.url), ['/api/auth/register', '/api/auth/login', '/api/auth/login'])
  assert.deepEqual(calls[2].data, { username: 'student', password: 'correct-password' })
})

test('rejected registration never logs in and can be corrected', async () => {
  const calls = []
  const flow = createRegistrationFlow(async options => {
    calls.push(options.url)
    if (options.data.username === 'taken') throw new Error('already exists')
    return { token: 'ok' }
  })
  await assert.rejects(flow.submit({ username: 'taken', password: 'password' }))
  assert.equal(calls.length, 1)
  assert.equal((await flow.submit({ username: 'available', password: 'password' })).token, 'ok')
  assert.deepEqual(calls, ['/api/auth/register', '/api/auth/register', '/api/auth/login'])
})

test('concurrent submits share one registration and one login', async () => {
  let release, count = 0
  const gate = new Promise(resolve => { release = resolve })
  const flow = createRegistrationFlow(async () => { count++; await gate; return { token: 'ok' } })
  const first = flow.submit({ username: 'student', password: 'password' })
  const second = flow.submit({ username: 'student', password: 'password' })
  assert.equal(first, second)
  release()
  await first
  assert.equal(count, 2)
})
