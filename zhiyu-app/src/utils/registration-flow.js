/** Keep the successful registration step in memory so a failed login can be retried safely. */
export function createRegistrationFlow(request, onCreated = () => {}) {
  let registeredUsername
  let pending
  async function run(form) {
    if (registeredUsername === undefined) {
      await request({ url: '/api/auth/register', method: 'POST', auth: false, timeout: 15000, data: form })
      registeredUsername = form.username
      onCreated()
    }
    return request({ url: '/api/auth/login', method: 'POST', auth: false, timeout: 15000,
      data: { username: registeredUsername, password: form.password } })
  }
  return {
    submit(form) {
      if (!pending) pending = run({ ...form }).finally(() => { pending = undefined })
      return pending
    },
  }
}
