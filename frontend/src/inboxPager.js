/** 收件箱每次向用户展示 20 封；后端也按该大小返回账号分页。 */
export const INBOX_PAGE_SIZE = 20

/** 为每个账号维护独立游标与缓冲区，再按日期合并为全局页面。 */
export function createInboxPager(accounts, fetchPage) {
  const sources = accounts.map(account => ({ account, offset: 0, buffer: [], hasMore: true }))
  let pending = null

  /** 缓冲区耗尽时才请求该账号下一页，避免较旧账号插队或重复取信。 */
  async function takePage() {
    const messages = []
    const errors = []
    while (messages.length < INBOX_PAGE_SIZE) {
      await Promise.all(sources.filter(source => source.hasMore && !source.buffer.length).map(async source => {
        try {
          const page = await fetchPage(source.account, source.offset)
          source.buffer = page.messages.map(message => ({
            ...message,
            key: `${source.account.id}:${message.id}`,
            accountId: source.account.id,
            accountEmail: source.account.email,
            provider: source.account.provider
          }))
          source.offset += page.messages.length
          source.hasMore = page.hasMore && page.messages.length > 0
        } catch (error) {
          source.hasMore = false
          errors.push(`${source.account.email}：${error.message}`)
        }
      }))
      const available = sources.filter(source => source.buffer.length)
      if (!available.length) break
      available.sort((a, b) => (b.buffer[0].date || '').localeCompare(a.buffer[0].date || ''))
      messages.push(available[0].buffer.shift())
    }
    return { messages, errors, hasMore: sources.some(source => source.hasMore || source.buffer.length) }
  }

  return {
    /** 并发触发的滚动事件共享同一次分页请求。 */
    next() {
      if (!pending) pending = takePage().finally(() => { pending = null })
      return pending
    }
  }
}
