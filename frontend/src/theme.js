/** 优先读取用户保存的主题，存储不可用时退回系统配色。 */
export function readTheme(storage, prefersDark = false) {
  try {
    const saved = storage?.getItem('winter-mail-theme')
    if (saved === 'light' || saved === 'dark') return saved
  } catch (_) {
    // The interface still follows the system when storage is unavailable.
  }
  return prefersDark ? 'dark' : 'light'
}

/** 主题切换只在浅色与深色之间循环。 */
export function nextTheme(current) {
  return current === 'dark' ? 'light' : 'dark'
}
