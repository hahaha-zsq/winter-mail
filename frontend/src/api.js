/** 统一处理 /api JSON 请求；204 等空响应返回 null，错误优先展示服务端说明。 */
export async function request(path, options = {}) {
  const headers = options.body instanceof FormData
    ? { ...options.headers }
    : { 'Content-Type': 'application/json', ...options.headers }
  const response = await fetch('/api' + path, { ...options, headers })
  const body = await response.text()
  if (!response.ok) {
    let detail = {}
    try { detail = body ? JSON.parse(body) : {} } catch { /* Use the status below. */ }
    throw new Error(detail.error || `请求失败 (${response.status})`)
  }
  return body ? JSON.parse(body) : null
}
