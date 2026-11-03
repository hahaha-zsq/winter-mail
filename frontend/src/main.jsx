import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './style.css'
import { request } from './api.js'
import { nextTheme, readTheme } from './theme.js'
import { createInboxPager, INBOX_PAGE_SIZE } from './inboxPager.js'

const providerNames = { gmail: 'Gmail', qq: 'QQ 邮箱', 163: '网易 163', 126: '网易 126', custom: '自定义邮箱' }
const providerClass = { gmail: 'gmail', qq: 'qq', 163: 'netease', 126: 'netease', custom: 'custom' }
const icons = {
  inbox: <><path d="M4 4h16v11l-3 5H7l-3-5V4Z"/><path d="M4 14h5l1.5 2h3L15 14h5"/></>,
  write: <><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L9 17l-4 1 1-4L16.5 3.5Z"/></>,
  settings: <><rect x="3" y="4" width="18" height="16" rx="3"/><path d="M3 9h18M8 14h4M8 17h8"/></>,
  refresh: <><path d="M20 11a8 8 0 0 0-14.5-4.5L3 9"/><path d="M3 4v5h5M4 13a8 8 0 0 0 14.5 4.5L21 15"/><path d="M21 20v-5h-5"/></>,
  search: <><circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/></>,
  chevron: <path d="m9 18 6-6-6-6"/>,
  close: <path d="M5 5 19 19M19 5 5 19"/>,
  plus: <path d="M12 5v14M5 12h14"/>,
  arrow: <><path d="M4 12h16m-7-7 7 7-7 7"/></>,
  check: <path d="m5 12 4 4L19 6"/>,
  trash: <><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5m4-5v5"/></>,
  mail: <><rect x="2.5" y="5" width="19" height="14" rx="3"/><path d="m3 7 9 7 9-7"/></>,
  shield: <><path d="M12 2 4 5v6c0 5 3.5 8 8 11 4.5-3 8-6 8-11V5l-8-3Z"/><path d="m9 12 2 2 4-4"/></>,
  globe: <><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c-2.5 2.5-4 5.5-4 9s1.5 6.5 4 9c2.5-2.5 4-5.5 4-9s-1.5-6.5-4-9Z"/></>,
  alert: <><circle cx="12" cy="12" r="9"/><path d="M12 7v6M12 17h.01"/></>,
  clip: <path d="m20 12-8.5 8.5a5 5 0 0 1-7-7L14 4a3.5 3.5 0 0 1 5 5l-9.5 9.5a2 2 0 0 1-3-3L15 7"/>,
  sun: <><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.93 4.93l1.41 1.41m11.32 11.32 1.41 1.41M2 12h2m16 0h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></>,
  moon: <path d="M20.2 15.6A8.5 8.5 0 0 1 8.4 3.8 8.5 8.5 0 1 0 20.2 15.6Z"/>
}
function Icon({name, size=18, stroke=1.8}) { return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{icons[name]}</svg> }
function badge(account) { return providerNames[account.provider] || account.provider }
function dateLabel(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  if (date.toDateString() === now.toDateString()) return date.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})
  return date.toLocaleDateString('zh-CN',{month:'2-digit',day:'2-digit'})
}
function initials(value) { return (value || '?').trim().charAt(0).toUpperCase() }
/** 邮箱客户端主界面：集中管理账号、收件箱、详情与发信表单。 */
function App() {
  const [theme, setTheme] = useState(() => readTheme(window.localStorage, window.matchMedia('(prefers-color-scheme: dark)').matches))
  const [accounts, setAccounts] = useState([])
  const [accountFilter, setAccountFilter] = useState('all')
  const [senderId, setSenderId] = useState('')
  const [messages, setMessages] = useState([])
  const [activeMessage, setActiveMessage] = useState(null)
  const [messageDetail, setMessageDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [view, setView] = useState('inbox')
  const [loading, setLoading] = useState(false)
  const [inboxLoading, setInboxLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [removingAccountId, setRemovingAccountId] = useState(null)
  const [notice, setNotice] = useState(null)
  const [search, setSearch] = useState('')
  const [account, setAccount] = useState({email:'',password:'',provider:'gmail',imapHost:'',imapPort:993,smtpHost:'',smtpPort:465,smtpSsl:true})
  const [draft, setDraft] = useState({recipients:'',cc:'',subject:'',body:'',format:'text'})
  const [sendResult, setSendResult] = useState(null)
  const [files, setFiles] = useState([])
  const fileInputRef = useRef(null)
  const navRef = useRef(null)
  const pagerRef = useRef(null)
  const inboxGenerationRef = useRef(0)
  const pageRequestRef = useRef(false)
  const [navPill, setNavPill] = useState(null)
  const [navMotionReady, setNavMotionReady] = useState(false)
  // 根据当前菜单按钮的位置调整导航胶囊；窗口变化时重新测量。
  useLayoutEffect(() => {
    const nav = navRef.current
    if (!nav) return
    const updatePill = () => {
      const selected = nav.querySelector('[aria-current="page"]')
      if (selected) setNavPill({ x: selected.offsetLeft, width: selected.offsetWidth })
    }
    updatePill()
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(updatePill)
    observer?.observe(nav)
    if (!observer) window.addEventListener('resize', updatePill)
    return () => {
      observer?.disconnect()
      if (!observer) window.removeEventListener('resize', updatePill)
    }
  }, [view])
  useEffect(() => {
    if (!navPill || navMotionReady) return
    const frame = requestAnimationFrame(() => setNavMotionReady(true))
    return () => cancelAnimationFrame(frame)
  }, [navPill, navMotionReady])
  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try { window.localStorage.setItem('winter-mail-theme', theme) } catch (_) { /* Storage can be disabled. */ }
  }, [theme])

  // 账号列表刷新后修正已删除的筛选项和发件账号。
  const refreshAccounts = useCallback(async () => {
    const data = await request('/accounts')
    setAccounts(data)
    setAccountFilter(old => old === 'all' || data.some(a => a.id === old) ? old : 'all')
    setSenderId(old => data.some(a => a.id === old) ? old : data[0]?.id || '')
    return data
  }, [])
  useEffect(() => { refreshAccounts().catch(e => setNotice({kind:'error',text:e.message})) }, [refreshAccounts])
  // 每次切换筛选或刷新时重建分页器，代次编号用于忽略过期响应。
  const loadInbox = useCallback(async () => {
    const generation = ++inboxGenerationRef.current
    pageRequestRef.current = false
    setLoadingMore(false)
    if (!accounts.length) { pagerRef.current = null; setMessages([]); setActiveMessage(null); setHasMore(false); setInboxLoading(false); return }
    setInboxLoading(true)
    setHasMore(false)
    setNotice(null)
    const targets = accountFilter === 'all' ? accounts : accounts.filter(a => a.id === accountFilter)
    const pager = createInboxPager(targets, (account, offset) => request(`/accounts/${account.id}/messages?limit=${INBOX_PAGE_SIZE}&offset=${offset}`))
    pagerRef.current = pager
    try {
      const page = await pager.next()
      if (inboxGenerationRef.current !== generation) return
      setMessages(page.messages)
      setHasMore(page.hasMore)
      setActiveMessage(current => page.messages.find(m => m.key === current?.key) || null)
      if (page.errors.length) setNotice({kind:'error',text:page.errors.join('；')})
    } catch(e) { if (inboxGenerationRef.current === generation) setNotice({kind:'error',text:e.message}) }
    finally { if (inboxGenerationRef.current === generation) setInboxLoading(false) }
  }, [accounts, accountFilter])
  useEffect(() => { if (view === 'inbox') loadInbox() }, [view, loadInbox])
  // 防止滚动事件重复发请求，同时保留已显示的邮件。
  const loadMore = useCallback(async () => {
    if (pageRequestRef.current || inboxLoading || !hasMore || !pagerRef.current) return
    pageRequestRef.current = true
    setLoadingMore(true)
    const generation = inboxGenerationRef.current
    try {
      const page = await pagerRef.current.next()
      if (inboxGenerationRef.current !== generation) return
      setMessages(current => [...current, ...page.messages])
      setHasMore(page.hasMore)
      if (page.errors.length) setNotice({kind:'error',text:page.errors.join('；')})
    } catch(e) { if (inboxGenerationRef.current === generation) setNotice({kind:'error',text:e.message}) }
    finally { if (inboxGenerationRef.current === generation) { pageRequestRef.current = false; setLoadingMore(false) } }
  }, [inboxLoading, hasMore])
  function onMessageScroll(event) {
    const list = event.currentTarget
    if (list.scrollHeight - list.scrollTop - list.clientHeight < 160) loadMore()
  }
  const visibleMessages = useMemo(() => {
    const query = search.trim().toLocaleLowerCase()
    return query ? messages.filter(m => [m.from,m.subject,m.preview,m.accountEmail].some(value => (value || '').toLocaleLowerCase().includes(query))) : messages
  }, [messages, search])
  // 点击邮件后单独拉取完整正文；切换邮件时忽略旧请求结果。
  useEffect(() => {
    if (!activeMessage) { setMessageDetail(null); setDetailLoading(false); return }
    let cancelled = false
    setMessageDetail(null)
    setDetailLoading(true)
    request(`/accounts/${activeMessage.accountId}/messages/${activeMessage.id}`)
      .then(detail => { if (!cancelled) setMessageDetail(detail) })
      .catch(error => { if (!cancelled) setNotice({kind:'error',text:error.message}) })
      .finally(() => { if (!cancelled) setDetailLoading(false) })
    return () => { cancelled = true }
  }, [activeMessage?.key])
  // 下载时由浏览器 Blob 生成本地文件名，不直接使用邮件里的路径。
  async function downloadAttachment(file) {
    if (!activeMessage) return
    try {
      const response = await fetch(`/api/accounts/${activeMessage.accountId}/messages/${activeMessage.id}/attachments/${file.index}`)
      if (!response.ok) throw new Error(`附件下载失败 (${response.status})`)
      const url = URL.createObjectURL(await response.blob())
      const link = document.createElement('a')
      link.href = url
      link.download = file.name.split(/[\\/]/).pop() || 'attachment'
      document.body.appendChild(link)
      link.click()
      link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 60000)
    } catch (error) { setNotice({kind:'error',text:error.message}) }
  }
  // 连接新账号并刷新列表；授权码只提交给后端，不写入本地存储。
  async function addAccount(e) {
    e.preventDefault(); setLoading(true); setNotice(null)
    try {
      const added = await request('/accounts',{method:'POST',body:JSON.stringify(account)})
      await refreshAccounts(); setAccountFilter(added.id); setSenderId(added.id)
      setAccount(old => ({...old,email:'',password:''})); setView('inbox')
      setNotice({kind:'success',text:`${added.email} 已连接`})
    } catch(e) { setNotice({kind:'error',text:e.message}) } finally { setLoading(false) }
  }
  async function removeAccount(id) {
    if (!window.confirm('确定移除这个邮箱账号？')) return
    setRemovingAccountId(id); setNotice(null)
    try { await request(`/accounts/${id}`,{method:'DELETE'}); await refreshAccounts(); setAccountFilter('all'); setActiveMessage(null); setNotice({kind:'success',text:'邮箱账号已移除'}) }
    catch(e) { setNotice({kind:'error',text:e.message}) } finally { setRemovingAccountId(null) }
  }
  function addFiles(e) {
    const chosen = Array.from(e.target.files || [])
    const next = [...files, ...chosen]
    const total = next.reduce((sum, file) => sum + file.size, 0)
    if (next.length > 10 || chosen.some(file => file.size > 10 * 1024 * 1024) || total > 25 * 1024 * 1024) {
      setNotice({kind:'error',text:'最多 10 个附件；单个不超过 10 MB，总计不超过 25 MB'})
    } else { setFiles(next); setNotice(null) }
    e.target.value = ''
  }
  // 有附件时提交 multipart/form-data，其余情况提交 JSON。
  async function send(e) {
    e.preventDefault(); if (!senderId) return
    setLoading(true); setNotice(null); setSendResult(null)
    try {
      let body
      if (files.length) {
        body = new FormData()
        Object.entries({...draft,accountId:senderId}).forEach(([key,value]) => body.append(key,value))
        files.forEach(file => body.append('attachments', file))
      } else body = JSON.stringify({...draft,accountId:senderId})
      const result = await request('/send',{method:'POST',body})
      setSendResult(result)
      if (!Object.keys(result.failed).length) { setDraft(current => ({recipients:'',cc:'',subject:'',body:'',format:current.format})); setFiles([]); if (fileInputRef.current) fileInputRef.current.value = '' }
    } catch(e) { setNotice({kind:'error',text:e.message}) } finally { setLoading(false) }
  }
  const activeAccount = accounts.find(a => a.id === accountFilter)
  const recipientCount = new Set(draft.recipients.split(/[,;\s]+/).map(s => s.trim().toLowerCase()).filter(Boolean)).size
  const ccCount = new Set(draft.cc.split(/[,;\s]+/).map(s => s.trim().toLowerCase()).filter(Boolean)).size
  const viewTitle = {inbox:'收件箱',compose:'写邮件',accounts:'邮箱账号'}[view]
  const viewDescription = {inbox:'所有来信，集中处理。',compose:'从任意已连接邮箱发送邮件。',accounts:'连接并管理你的发件身份。'}[view]
  return <div className="app-shell">
    <a className="skip-link" href="#main">跳转到主要内容</a>
    <header className="global-nav">
      <div className="global-nav-inner">
        <nav ref={navRef} className={navPill ? 'main-nav has-indicator' : 'main-nav'} aria-label="主导航">
          {navPill && <span className={navMotionReady ? 'nav-indicator is-ready' : 'nav-indicator'} aria-hidden="true" style={{transform:`translate3d(${navPill.x}px, 0, 0) scaleX(${navPill.width / 100})`}}/>}
          <button className={view==='inbox'?'nav-item selected':'nav-item'} aria-current={view==='inbox'?'page':undefined} onClick={()=>setView('inbox')}><Icon name="inbox" size={18}/> 收件箱</button>
          <button className={view==='compose'?'nav-item selected':'nav-item'} aria-current={view==='compose'?'page':undefined} onClick={()=>{setView('compose');setSendResult(null)}}><Icon name="write" size={18}/> 写邮件</button>
          <button className={view==='accounts'?'nav-item selected':'nav-item'} aria-current={view==='accounts'?'page':undefined} onClick={()=>setView('accounts')}><Icon name="settings" size={18}/> 邮箱账号</button>
        </nav>
        <button className="theme-toggle" type="button" onClick={() => setTheme(current => nextTheme(current))} aria-label={theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'} title={theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'}><Icon name={theme === 'dark' ? 'sun' : 'moon'} size={18}/></button>
      </div>
    </header>
    <main className="workspace" id="main">
      <div className="content">
        <div className="page-heading"><div><h1>{viewTitle}<span className="heading-period">.</span></h1><p>{viewDescription}</p></div>{view==='inbox'&&<button className="secondary-button" onClick={loadInbox} disabled={inboxLoading || !accounts.length}><Icon name="refresh" size={16}/> {inboxLoading?'正在同步':'刷新邮件'}</button>}{view==='accounts'&&<span className="heading-count">{accounts.length} 个账号</span>}</div>
        {notice && <div className={`notice ${notice.kind}`} role="status"><Icon name={notice.kind==='error'?'alert':'check'} size={17}/><span>{notice.text}</span><button aria-label="关闭提示" onClick={()=>setNotice(null)}><Icon name="close" size={16}/></button></div>}
        {view==='inbox'&&<div className={activeMessage?"inbox-layout has-selection":"inbox-layout"}><section className="mail-surface" aria-label="邮件列表"><div className="mail-toolbar"><div className="toolbar-title"><span className="toolbar-label">{activeAccount?.email || '全部收件箱'}</span><span className="message-count">{visibleMessages.length}</span></div><label className="search-box"><Icon name="search" size={17}/><input aria-label="搜索当前邮件" value={search} onChange={e=>setSearch(e.target.value)} placeholder="搜索邮件"/></label></div><div className="account-tabs"><button className={accountFilter==='all'?'tab active':'tab'} onClick={()=>setAccountFilter('all')}>全部邮箱</button>{accounts.map(a=><button className={accountFilter===a.id?'tab active':'tab'} key={a.id} onClick={()=>setAccountFilter(a.id)}>{badge(a)}</button>)}</div><div className="message-list" onScroll={onMessageScroll}>{inboxLoading ? <div className="loading-list">{[0,1,2,3].map(i=><div className="skeleton-row" key={i}><span/><div><i/><i/></div></div>)}</div> : visibleMessages.length ? visibleMessages.map(m=><button key={m.key} className={activeMessage?.key===m.key?'message-row active':'message-row'} onClick={()=>setActiveMessage(m)}><span className={`sender-avatar ${providerClass[m.provider] || 'custom'}`}>{initials(m.from)}</span><span className="message-summary"><span className="message-meta"><strong>{m.from}</strong><time>{dateLabel(m.date)}</time></span><span className="message-subject">{m.subject || '(无主题)'}</span><span className="message-preview">{m.preview || '此邮件没有文本预览'}</span><span className="message-account">收件于 {m.accountEmail}</span></span><Icon name="chevron" size={15}/></button>) : <div className="empty-state"><div className="empty-art"><Icon name={search?'search':'inbox'} size={32} stroke={1.4}/></div><h2>{search?'没有找到匹配的邮件':accounts.length?'收件箱暂时为空':'从连接邮箱开始'}</h2><p>{search?'换一个关键词试试。':accounts.length?'刷新以获取最新邮件。':'连接 Gmail、QQ 或网易邮箱，邮件会集中出现在这里。'}</p>{!accounts.length&&<button className="text-action" onClick={()=>setView('accounts')}>连接邮箱 <Icon name="arrow" size={16}/></button>}</div>}{!inboxLoading && messages.length > 0 && <div className="message-list-footer" role="status">{loadingMore ? "正在加载更多邮件…" : hasMore ? <button type="button" onClick={loadMore}>加载更多邮件</button> : "已加载全部邮件"}</div>}</div></section><aside className="reading-pane" aria-label="邮件预览">{activeMessage ? <><div className="reading-heading"><button className="mobile-back" onClick={()=>setActiveMessage(null)}>‹ 返回列表</button><span>邮件预览</span><span className="reading-date">{activeMessage.date ? new Date(activeMessage.date).toLocaleString('zh-CN') : ''}</span></div><div className="reading-content"><span className="reading-provider">{providerNames[activeMessage.provider] || activeMessage.provider}</span><h2>{activeMessage.subject || '(无主题)'}</h2><div className="reading-sender"><span className={`sender-avatar ${providerClass[activeMessage.provider] || 'custom'}`}>{initials(activeMessage.from)}</span><div><strong>{activeMessage.from}</strong><small>发送至 {activeMessage.accountEmail}</small></div></div>{detailLoading ? <p className="preview-note">正在加载邮件正文…</p> : messageDetail?.html ? <iframe className="reading-html" title="邮件正文" sandbox="" referrerPolicy="no-referrer" srcDoc={`<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data:">${messageDetail.html}`} /> : <div className="reading-body">{messageDetail?.text || activeMessage.preview || '此邮件没有可显示的正文。'}</div>}{messageDetail?.attachments?.length > 0 && <div className="received-attachments"><h3>附件 · {messageDetail.attachments.length}</h3>{messageDetail.attachments.map(file => <button key={file.index} type="button" onClick={() => downloadAttachment(file)}><Icon name="clip" size={15}/><span>{file.name}</span><small>{file.size >= 0 ? `${(file.size / 1024).toFixed(0)} KB` : ''}</small><span>下载</span></button>)}</div>}</div></> : <div className="reading-placeholder"><div className="reading-placeholder-icon"><Icon name="mail" size={35} stroke={1.3}/></div><h2>选择一封邮件</h2><p>在左侧列表中选择邮件，快速查看发件人、主题和内容摘要。</p></div>}</aside></div>}
        {view==='accounts'&&<div className="accounts-layout"><section className="form-surface"><div className="surface-heading"><div className="section-icon"><Icon name="plus" size={20}/></div><div><h2>连接新邮箱</h2><p>填写邮箱地址和服务商提供的授权码</p></div></div><form className="account-form" onSubmit={addAccount}><div className="field"><label htmlFor="provider">邮箱服务商</label><select id="provider" value={account.provider} onChange={e=>setAccount({...account,provider:e.target.value})}><option value="gmail">Gmail</option><option value="qq">QQ 邮箱</option><option value="163">网易 163</option><option value="126">网易 126</option><option value="custom">自定义 IMAP / SMTP</option></select></div><div className="field"><label htmlFor="email">邮箱地址</label><input id="email" type="email" required autoComplete="email" value={account.email} onChange={e=>setAccount({...account,email:e.target.value})} placeholder="you@example.com"/></div><div className="field"><label htmlFor="password">授权码 / 应用密码</label><input id="password" type="password" required autoComplete="new-password" value={account.password} onChange={e=>setAccount({...account,password:e.target.value})} placeholder="填写服务商生成的授权码"/><small>通常不是邮箱登录密码。请先在邮箱设置中开启 IMAP/SMTP。</small></div>{account.provider==='custom'&&<div className="custom-fields"><h3>服务器设置</h3><div className="field-row"><div className="field"><label htmlFor="imapHost">IMAP 主机</label><input id="imapHost" required value={account.imapHost} onChange={e=>setAccount({...account,imapHost:e.target.value})} placeholder="imap.example.com"/></div><div className="field"><label htmlFor="imapPort">端口</label><input id="imapPort" required type="number" min="1" max="65535" value={account.imapPort} onChange={e=>setAccount({...account,imapPort:Number(e.target.value)})}/></div></div><div className="field-row"><div className="field"><label htmlFor="smtpHost">SMTP 主机</label><input id="smtpHost" required value={account.smtpHost} onChange={e=>setAccount({...account,smtpHost:e.target.value})} placeholder="smtp.example.com"/></div><div className="field"><label htmlFor="smtpPort">端口</label><input id="smtpPort" required type="number" min="1" max="65535" value={account.smtpPort} onChange={e=>setAccount({...account,smtpPort:Number(e.target.value)})}/></div></div><label className="checkbox-field"><input type="checkbox" checked={account.smtpSsl} onChange={e=>setAccount({...account,smtpSsl:e.target.checked})}/> SMTP 使用 SSL，关闭后使用 STARTTLS</label></div>}<div className="form-footer"><span><Icon name="shield" size={15}/> 授权码加密存储</span><button className="primary-button" disabled={loading}>{loading?'连接中':'连接邮箱'} <Icon name="arrow" size={17}/></button></div></form></section><section className="connected-surface"><div className="surface-heading"><div className="section-icon muted"><Icon name="mail" size={20}/></div><div><h2>已连接账号</h2><p>在这些邮箱之间自由收发</p></div></div>{accounts.length ? <div className="connected-list">{accounts.map(a=><div className="connected-row" key={a.id}><span className={`account-icon ${providerClass[a.provider] || 'custom'}`}>{initials(a.email)}</span><div className="connected-detail"><strong>{a.email}</strong><small>{badge(a)} · IMAP / SMTP</small></div><button className="icon-button" title={`移除 ${a.email}`} aria-label={`移除 ${a.email}`} disabled={removingAccountId !== null} onClick={()=>removeAccount(a.id)}><Icon name="trash" size={17}/></button></div>)}</div> : <div className="connected-empty">尚未连接邮箱账号</div>}<div className="security-note"><Icon name="shield" size={20}/><div><strong>安全存储</strong><p>授权码在服务端加密后保存至 MySQL。请妥善保管加密密钥。</p></div></div></section></div>}
        {view==='compose'&&<div className="compose-layout"><section className="form-surface compose-surface"><div className="surface-heading"><div className="section-icon"><Icon name="write" size={20}/></div><div><h2>新邮件</h2><p>填写内容后从所选邮箱发送</p></div></div><form className="compose-form" onSubmit={send}><div className="field"><label htmlFor="sender">发件账号</label><select id="sender" required value={senderId} onChange={e=>setSenderId(e.target.value)}><option value="" disabled>选择邮箱账号</option>{accounts.map(a=><option value={a.id} key={a.id}>{a.email} · {badge(a)}</option>)}</select></div><div className="field"><label htmlFor="recipients">收件人</label><textarea id="recipients" required rows="3" value={draft.recipients} onChange={e=>setDraft({...draft,recipients:e.target.value})} placeholder="name@example.com，多个地址用逗号、分号或换行分隔"/><small>每位收件人会收到独立邮件，单次最多 100 位。</small></div><div className="field"><label htmlFor="cc">抄送 <span className="optional-label">可选</span></label><textarea id="cc" rows="2" value={draft.cc} onChange={e=>setDraft({...draft,cc:e.target.value})} placeholder="copy@example.com，多个地址用逗号、分号或换行分隔"/><small>批量发送时，每封邮件都会抄送给这些地址。</small></div><div className="field"><label htmlFor="subject">主题</label><input id="subject" required value={draft.subject} onChange={e=>setDraft({...draft,subject:e.target.value})} placeholder="写一个清楚的主题"/></div><div className="field"><span className="field-label">邮件格式</span><div className="format-switch" role="group" aria-label="邮件格式"><button type="button" className={draft.format==='text'?'active':''} aria-pressed={draft.format==='text'} onClick={()=>setDraft({...draft,format:'text'})}>纯文本</button><button type="button" className={draft.format==='html'?'active':''} aria-pressed={draft.format==='html'} onClick={()=>setDraft({...draft,format:'html'})}>HTML</button></div><small>{draft.format==='html'?'输入 HTML 源码，邮件将以 HTML 格式发送。':'以纯文本发送，不解析 HTML 标签。'}</small></div><div className="field"><label htmlFor="body">{draft.format==='html'?'HTML 正文':'正文'}</label><textarea id="body" className={draft.format==='html'?'message-body code-body':'message-body'} required value={draft.body} onChange={e=>setDraft({...draft,body:e.target.value})} placeholder={draft.format==='html'?'<h1>邮件标题</h1>\n<p>在这里写下内容…</p>':'在这里写下邮件内容…'}/></div>{draft.format==='html'&&<div className="html-preview"><div className="html-preview-heading">HTML 预览 <span>脚本已禁用</span></div><iframe title="HTML 邮件预览" sandbox="" referrerPolicy="no-referrer" srcDoc={draft.body || '<p style="color:#809084;font:14px sans-serif;padding:16px">在左侧输入 HTML 后预览会显示在这里</p>'} /></div> }<div className="attachment-field"><div className="attachment-heading"><span><Icon name="clip" size={16}/> 附件 <small>可选</small></span><span>{files.length}/10 个</span></div><label className="attachment-picker"><input ref={fileInputRef} type="file" multiple onChange={addFiles} aria-label="选择附件"/><Icon name="plus" size={16}/> 添加附件</label><p>单个文件不超过 10 MB，附件总计不超过 25 MB。</p>{files.length>0&&<ul className="attachment-list">{files.map((file,index)=><li key={`${file.name}-${file.lastModified}-${index}`}><Icon name="clip" size={14}/><span title={file.name}>{file.name}</span><small>{(file.size/1024/1024).toFixed(1)} MB</small><button type="button" aria-label={`移除 ${file.name}`} onClick={()=>setFiles(current=>current.filter((_,i)=>i!==index))}><Icon name="close" size={15}/></button></li>)}</ul>}</div><div className="form-footer"><span>{recipientCount} 位收件人</span><button className="primary-button" disabled={!senderId || loading}>{loading?'发送中…':'发送邮件'} <Icon name="arrow" size={17}/></button></div></form></section><aside className="send-aside"><div className="send-summary"><div className="summary-heading"><Icon name="mail" size={18}/> 发送摘要</div><div className="summary-row"><span>发件邮箱</span><strong>{accounts.find(a=>a.id===senderId)?.email || '尚未选择'}</strong></div><div className="summary-row"><span>收件人数</span><strong>{recipientCount}</strong></div><div className="summary-row"><span>抄送人数</span><strong>{ccCount}</strong></div><div className="summary-row"><span>附件</span><strong>{files.length} 个</strong></div><div className="summary-row"><span>邮件格式</span><strong>{draft.format==='html'?'HTML':'纯文本'}</strong></div><div className="summary-row"><span>发送方式</span><strong>逐人单独发送</strong></div></div>{sendResult&&<div className="send-result" role="status"><h3>发送结果</h3><p><strong>{sendResult.sent.length}</strong> 封成功 <span>·</span> <strong>{Object.keys(sendResult.failed).length}</strong> 封失败</p>{Object.keys(sendResult.failed).length>0&&<ul>{Object.entries(sendResult.failed).map(([email,reason])=><li key={email}>{email}：{reason}</li>)}</ul>}</div>}</aside></div>}
      </div>
    </main>
  </div>
}
createRoot(document.getElementById('root')).render(<App />)
