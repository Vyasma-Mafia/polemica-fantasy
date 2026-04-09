import { Fragment, type ReactNode } from 'react'

/**
 * Approximate Telegram MarkdownV2 → React for admin broadcast preview.
 * Edge cases may differ slightly from the Telegram client.
 */

function findUnescaped(
  s: string,
  from: number,
  delimiter: string,
): number {
  let i = from
  while (i < s.length) {
    if (s[i] === '\\' && i + 1 < s.length) {
      i += 2
      continue
    }
    if (s.startsWith(delimiter, i)) return i
    i += 1
  }
  return -1
}

function stripOneEscapeLayer(s: string): string {
  let out = ''
  for (let i = 0; i < s.length; i++) {
    if (s[i] === '\\' && i + 1 < s.length) {
      out += s[i + 1]
      i++
    } else {
      out += s[i]
    }
  }
  return out
}

function safeHref(url: string): string | null {
  const t = url.trim()
  if (/^https?:\/\//i.test(t)) return t
  return null
}

function parseInline(s: string): ReactNode[] {
  const nodes: ReactNode[] = []
  let i = 0
  let key = 0
  const pushText = (t: string) => {
    if (t) nodes.push(t)
  }

  while (i < s.length) {
    if (s[i] === '\\' && i + 1 < s.length) {
      pushText(s[i + 1]!)
      i += 2
      continue
    }

    // Link [label](url)
    if (s[i] === '[') {
      const closeBracket = findUnescaped(s, i + 1, ']')
      if (
        closeBracket > i + 1 &&
        closeBracket + 1 < s.length &&
        s[closeBracket + 1] === '('
      ) {
        const closeParen = findUnescaped(s, closeBracket + 2, ')')
        if (closeParen > 0) {
          const labelRaw = s.slice(i + 1, closeBracket)
          const urlRaw = s.slice(closeBracket + 2, closeParen)
          const label = stripOneEscapeLayer(labelRaw)
          const url = stripOneEscapeLayer(urlRaw)
          const href = safeHref(url)
          const k = key++
          if (href) {
            nodes.push(
              <a
                key={k}
                href={href}
                target="_blank"
                rel="noopener noreferrer"
                style={{ color: '#1677ff' }}
              >
                {label}
              </a>,
            )
          } else {
            nodes.push(<span key={k}>{`[${label}](${url})`}</span>)
          }
          i = closeParen + 1
          continue
        }
      }
    }

    // Inline code `...`
    if (s[i] === '`') {
      const end = findUnescaped(s, i + 1, '`')
      if (end > 0) {
        const inner = stripOneEscapeLayer(s.slice(i + 1, end))
        const k = key++
        nodes.push(
          <code
            key={k}
            style={{
              fontFamily: 'ui-monospace, monospace',
              background: 'rgba(0,0,0,0.06)',
              padding: '0 4px',
              borderRadius: 4,
              fontSize: '0.9em',
            }}
          >
            {inner}
          </code>,
        )
        i = end + 1
        continue
      }
    }

    // Spoiler ||...||
    if (s[i] === '|' && s[i + 1] === '|') {
      const end = findUnescaped(s, i + 2, '||')
      if (end > 0) {
        const inner = s.slice(i + 2, end)
        const k = key++
        nodes.push(
          <span
            key={k}
            style={{
              background: '#555',
              color: '#555',
              borderRadius: 2,
              padding: '0 2px',
            }}
            title="Spoiler (hover to peek in Telegram)"
          >
            {parseInline(inner)}
          </span>,
        )
        i = end + 2
        continue
      }
    }

    // Underline __...__
    if (s[i] === '_' && s[i + 1] === '_') {
      const end = findUnescaped(s, i + 2, '__')
      if (end > 0) {
        const inner = s.slice(i + 2, end)
        const k = key++
        nodes.push(
          <u key={k}>{parseInline(inner)}</u>,
        )
        i = end + 2
        continue
      }
    }

    // Bold *...*
    if (s[i] === '*') {
      const end = findUnescaped(s, i + 1, '*')
      if (end > 0) {
        const inner = s.slice(i + 1, end)
        const k = key++
        nodes.push(<strong key={k}>{parseInline(inner)}</strong>)
        i = end + 1
        continue
      }
    }

    // Italic _..._ ( __...__ is handled above )
    if (s[i] === '_') {
      const end = findUnescaped(s, i + 1, '_')
      if (end > 0) {
        const inner = s.slice(i + 1, end)
        const k = key++
        nodes.push(<em key={k}>{parseInline(inner)}</em>)
        i = end + 1
        continue
      }
    }

    // Strikethrough ~...~
    if (s[i] === '~') {
      const end = findUnescaped(s, i + 1, '~')
      if (end > 0) {
        const inner = s.slice(i + 1, end)
        const k = key++
        nodes.push(
          <span key={k} style={{ textDecoration: 'line-through' }}>
            {parseInline(inner)}
          </span>,
        )
        i = end + 1
        continue
      }
    }

    pushText(s[i]!)
    i += 1
  }

  return nodes
}

export function TelegramMarkdownV2Preview({ text }: { text: string }) {
  const lines = text.split('\n')
  return (
    <div
      style={{
        fontSize: 14,
        lineHeight: 1.45,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
      }}
    >
      {lines.map((line, lineIdx) => (
        <Fragment key={lineIdx}>
          {lineIdx > 0 ? <br /> : null}
          {parseInline(line)}
        </Fragment>
      ))}
    </div>
  )
}
