/**
 * Clipboard helper with a fallback for HTTP/dev environments.
 * navigator.clipboard is only reliably available on HTTPS or localhost.
 */
export async function copyToClipboard(text: string): Promise<void> {
  if (!text) return
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'readonly')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()
  textarea.setSelectionRange(0, textarea.value.length)
  try {
    const ok = document.execCommand('copy')
    if (!ok) throw new Error('document.execCommand(copy) returned false')
  } finally {
    document.body.removeChild(textarea)
  }
}
