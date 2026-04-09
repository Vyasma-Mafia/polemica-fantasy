/**
 * Client-side validation for Telegram Bot API MarkdownV2 (same rules as sendMessage parse_mode).
 * @see https://core.telegram.org/bots/api#markdownv2-style
 */

export const TELEGRAM_MESSAGE_MAX_LENGTH = 4096

const PLAIN_MUST_ESCAPE = new Set(
  '_*[]()~`>#+=|{}.!-'.split(''),
)

function isPlainMustEscape(c: string): boolean {
  return PLAIN_MUST_ESCAPE.has(c)
}

export type TelegramMarkdownV2Validation = {
  ok: boolean
  issues: string[]
}

function pushIssue(issues: string[], msg: string) {
  if (!issues.includes(msg)) issues.push(msg)
}

function findExclusiveLineEnd(s: string, from: number, limit: number): number {
  for (let k = from; k < limit; k++) {
    if (s[k] === '\n' || s[k] === '\r') return k
  }
  return limit
}

function skipLineBreak(s: string, pos: number, limit: number): number {
  if (pos >= limit) return pos
  if (s[pos] === '\r' && pos + 1 < limit && s[pos + 1] === '\n') return pos + 2
  if (s[pos] === '\n' || s[pos] === '\r') return pos + 1
  return pos
}

function isLineStartInSegment(s: string, i: number, segmentStart: number): boolean {
  return i === segmentStart || s[i - 1] === '\n' || s[i - 1] === '\r'
}

function parseLinkLabel(
  s: string,
  start: number,
  end: number,
  issues: string[],
): number {
  let i = start + 1
  const n = end
  while (i < n) {
    if (s[i] === '\\') {
      if (i + 1 >= n) {
        pushIssue(issues, 'Незавершённый escape \\ в тексте ссылки […]')
        return -1
      }
      i += 2
      continue
    }
    if (s[i] === ']') return i + 1
    if (isPlainMustEscape(s[i])) {
      pushIssue(
        issues,
        `Символ «${s[i]}» в тексте ссылки должен быть экранирован как \\${s[i]} (позиция ${i + 1}).`,
      )
      return -1
    }
    i += 1
  }
  pushIssue(issues, 'Не закрыта ссылка: нет «]» после «[»')
  return -1
}

function parseLinkUrl(s: string, start: number, end: number, issues: string[]): number {
  let i = start + 1
  const n = end
  while (i < n) {
    if (s[i] === '\\') {
      if (i + 1 >= n) {
        pushIssue(issues, 'Незавершённый escape \\ в URL ссылки')
        return -1
      }
      const next = s[i + 1]
      if (next !== ')' && next !== '\\') {
        pushIssue(
          issues,
          `В URL после \\ допустимы только ) и \\ (позиция ${i + 1}).`,
        )
        return -1
      }
      i += 2
      continue
    }
    if (s[i] === ')') return i + 1
    i += 1
  }
  pushIssue(issues, 'Не закрыта ссылка: нет «)» в части (url)')
  return -1
}

function parseInlineCode(
  s: string,
  start: number,
  end: number,
  issues: string[],
): number {
  let i = start + 1
  const n = end
  while (i < n) {
    if (s[i] === '\\') {
      if (i + 1 >= n) {
        pushIssue(issues, 'Незавершённый escape \\ внутри inline-кода')
        return -1
      }
      const next = s[i + 1]
      if (next !== '`' && next !== '\\') {
        pushIssue(
          issues,
          `В inline-коде после \\ допустимы только \` и \\ (позиция ${i + 1}).`,
        )
        return -1
      }
      i += 2
      continue
    }
    if (s[i] === '`') return i + 1
    i += 1
  }
  pushIssue(issues, 'Не закрыт inline-код: нет второй «`»')
  return -1
}

function parseFencedCodeBlock(
  s: string,
  start: number,
  end: number,
  issues: string[],
): number {
  if (!s.startsWith('```', start)) return start
  let i = start + 3
  const n = end
  while (i < n && s[i] !== '\n' && s[i] !== '\r') {
    i++
  }
  if (i < n && (s[i] === '\r' || s[i] === '\n')) {
    if (s[i] === '\r' && i + 1 < n && s[i + 1] === '\n') i += 2
    else i += 1
  }
  while (i < n) {
    if (s.startsWith('```', i)) {
      return i + 3
    }
    if (s[i] === '\\') {
      if (i + 1 >= n) {
        pushIssue(issues, 'Незавершённый escape \\ внутри блока кода')
        return -1
      }
      const next = s[i + 1]
      if (next !== '`' && next !== '\\') {
        pushIssue(
          issues,
          `В блоке кода после \\ допустимы только \` и \\ (позиция ${i + 1}).`,
        )
        return -1
      }
      i += 2
      continue
    }
    i += 1
  }
  pushIssue(issues, 'Не закрыт блок кода: нет завершающих «```»')
  return -1
}

/** Inside *bold*: unescaped * closes the bold. */
function parseInsideBold(
  s: string,
  start: number,
  end: number,
  issues: string[],
): number {
  let i = start
  while (i < end) {
    if (s[i] === '\\') {
      if (i + 1 >= end) {
        pushIssue(issues, 'Строка заканчивается на «\\» внутри жирного текста.')
        return -1
      }
      i += 2
      continue
    }
    if (s[i] === '*') return i + 1

    if (s.startsWith('```', i)) {
      const after = parseFencedCodeBlock(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '`') {
      const after = parseInlineCode(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s.startsWith('||', i)) {
      const after = parseInsideSpoiler(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s.startsWith('__', i)) {
      const after = parseInsideUnderline(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '_') {
      const after = parseInsideItalic(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '~') {
      const after = parseInsideStrikethrough(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '[') {
      const afterLabel = parseLinkLabel(s, i, end, issues)
      if (afterLabel < 0) return -1
      if (afterLabel >= end || s[afterLabel] !== '(') {
        pushIssue(issues, 'После «]» в ссылке ожидается «(» с URL.')
        return -1
      }
      const afterUrl = parseLinkUrl(s, afterLabel, end, issues)
      if (afterUrl < 0) return -1
      i = afterUrl
      continue
    }
    if (isPlainMustEscape(s[i])) {
      pushIssue(
        issues,
        `Символ «${s[i]}» нужно экранировать как \\${s[i]} (позиция ${i + 1}).`,
      )
      return -1
    }
    i += 1
  }
  pushIssue(issues, 'Не закрыт жирный текст: нет парной «*»')
  return -1
}

function parseInsideSpoiler(
  s: string,
  start: number,
  end: number,
  issues: string[],
): number {
  let i = start
  while (i < end) {
    if (s[i] === '\\') {
      if (i + 1 >= end) {
        pushIssue(issues, 'Незавершённый escape \\ внутри спойлера.')
        return -1
      }
      i += 2
      continue
    }
    if (s.startsWith('||', i)) return i + 2

    if (s.startsWith('```', i)) {
      const after = parseFencedCodeBlock(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '`') {
      const after = parseInlineCode(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '*') {
      const after = parseInsideBold(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s.startsWith('__', i)) {
      const after = parseInsideUnderline(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '_') {
      const after = parseInsideItalic(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '~') {
      const after = parseInsideStrikethrough(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '[') {
      const afterLabel = parseLinkLabel(s, i, end, issues)
      if (afterLabel < 0) return -1
      if (afterLabel >= end || s[afterLabel] !== '(') {
        pushIssue(issues, 'После «]» в ссылке ожидается «(» с URL.')
        return -1
      }
      const afterUrl = parseLinkUrl(s, afterLabel, end, issues)
      if (afterUrl < 0) return -1
      i = afterUrl
      continue
    }
    if (isPlainMustEscape(s[i])) {
      pushIssue(
        issues,
        `Символ «${s[i]}» нужно экранировать как \\${s[i]} (позиция ${i + 1}).`,
      )
      return -1
    }
    i += 1
  }
  pushIssue(issues, 'Не закрыт спойлер: нет «||»')
  return -1
}

function parseInsideUnderline(
  s: string,
  start: number,
  end: number,
  issues: string[],
): number {
  let i = start
  while (i < end) {
    if (s[i] === '\\') {
      if (i + 1 >= end) {
        pushIssue(issues, 'Незавершённый escape \\ внутри подчёркивания.')
        return -1
      }
      i += 2
      continue
    }
    if (s.startsWith('__', i)) return i + 2

    if (s.startsWith('```', i)) {
      const after = parseFencedCodeBlock(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '`') {
      const after = parseInlineCode(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s.startsWith('||', i)) {
      const after = parseInsideSpoiler(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '*') {
      const after = parseInsideBold(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '_') {
      const after = parseInsideItalic(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '~') {
      const after = parseInsideStrikethrough(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '[') {
      const afterLabel = parseLinkLabel(s, i, end, issues)
      if (afterLabel < 0) return -1
      if (afterLabel >= end || s[afterLabel] !== '(') {
        pushIssue(issues, 'После «]» в ссылке ожидается «(» с URL.')
        return -1
      }
      const afterUrl = parseLinkUrl(s, afterLabel, end, issues)
      if (afterUrl < 0) return -1
      i = afterUrl
      continue
    }
    if (isPlainMustEscape(s[i])) {
      pushIssue(
        issues,
        `Символ «${s[i]}» нужно экранировать как \\${s[i]} (позиция ${i + 1}).`,
      )
      return -1
    }
    i += 1
  }
  pushIssue(issues, 'Не закрыто подчёркивание: нет «__»')
  return -1
}

/** Inside _italic_: `__` → underline; lone `_` closes. */
function parseInsideItalic(
  s: string,
  start: number,
  end: number,
  issues: string[],
): number {
  let i = start
  while (i < end) {
    if (s[i] === '\\') {
      if (i + 1 >= end) {
        pushIssue(issues, 'Незавершённый escape \\ внутри курсива.')
        return -1
      }
      i += 2
      continue
    }
    if (s.startsWith('__', i)) {
      const after = parseInsideUnderline(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '_') return i + 1

    if (s.startsWith('```', i)) {
      const after = parseFencedCodeBlock(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '`') {
      const after = parseInlineCode(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s.startsWith('||', i)) {
      const after = parseInsideSpoiler(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '*') {
      const after = parseInsideBold(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '~') {
      const after = parseInsideStrikethrough(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '[') {
      const afterLabel = parseLinkLabel(s, i, end, issues)
      if (afterLabel < 0) return -1
      if (afterLabel >= end || s[afterLabel] !== '(') {
        pushIssue(issues, 'После «]» в ссылке ожидается «(» с URL.')
        return -1
      }
      const afterUrl = parseLinkUrl(s, afterLabel, end, issues)
      if (afterUrl < 0) return -1
      i = afterUrl
      continue
    }
    if (isPlainMustEscape(s[i])) {
      pushIssue(
        issues,
        `Символ «${s[i]}» нужно экранировать как \\${s[i]} (позиция ${i + 1}).`,
      )
      return -1
    }
    i += 1
  }
  pushIssue(issues, 'Не закрыт курсив: нет парной «_»')
  return -1
}

function parseInsideStrikethrough(
  s: string,
  start: number,
  end: number,
  issues: string[],
): number {
  let i = start
  while (i < end) {
    if (s[i] === '\\') {
      if (i + 1 >= end) {
        pushIssue(issues, 'Незавершённый escape \\ внутри зачёркнутого.')
        return -1
      }
      i += 2
      continue
    }
    if (s[i] === '~') return i + 1

    if (s.startsWith('```', i)) {
      const after = parseFencedCodeBlock(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '`') {
      const after = parseInlineCode(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s.startsWith('||', i)) {
      const after = parseInsideSpoiler(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s.startsWith('__', i)) {
      const after = parseInsideUnderline(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '*') {
      const after = parseInsideBold(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '_') {
      const after = parseInsideItalic(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '[') {
      const afterLabel = parseLinkLabel(s, i, end, issues)
      if (afterLabel < 0) return -1
      if (afterLabel >= end || s[afterLabel] !== '(') {
        pushIssue(issues, 'После «]» в ссылке ожидается «(» с URL.')
        return -1
      }
      const afterUrl = parseLinkUrl(s, afterLabel, end, issues)
      if (afterUrl < 0) return -1
      i = afterUrl
      continue
    }
    if (isPlainMustEscape(s[i])) {
      pushIssue(
        issues,
        `Символ «${s[i]}» нужно экранировать как \\${s[i]} (позиция ${i + 1}).`,
      )
      return -1
    }
    i += 1
  }
  pushIssue(issues, 'Не закрыт зачёркнутый текст: нет парной «~»')
  return -1
}

function parseBounded(
  s: string,
  start: number,
  end: number,
  issues: string[],
): number {
  let i = start
  while (i < end) {
    if (s[i] === '\\') {
      if (i + 1 >= end) {
        pushIssue(issues, 'Строка заканчивается на «\\» — добавьте символ после него.')
        return -1
      }
      i += 2
      continue
    }

    if (isLineStartInSegment(s, i, start) && s[i] === '>') {
      let j = i
      while (j < end && isLineStartInSegment(s, j, start) && s[j] === '>') {
        const lineEnd = findExclusiveLineEnd(s, j, end)
        const after = parseBounded(s, j + 1, lineEnd, issues)
        if (after < 0) return -1
        if (after !== lineEnd) {
          pushIssue(
            issues,
            'Неверная разметка в строке цитаты (проверьте экранирование).',
          )
          return -1
        }
        j = skipLineBreak(s, lineEnd, end)
      }
      i = j
      continue
    }

    if (s.startsWith('```', i)) {
      const after = parseFencedCodeBlock(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '`') {
      const after = parseInlineCode(s, i, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s.startsWith('||', i)) {
      const after = parseInsideSpoiler(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s.startsWith('__', i)) {
      const after = parseInsideUnderline(s, i + 2, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '_') {
      const after = parseInsideItalic(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '*') {
      const after = parseInsideBold(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '~') {
      const after = parseInsideStrikethrough(s, i + 1, end, issues)
      if (after < 0) return -1
      i = after
      continue
    }
    if (s[i] === '[') {
      const afterLabel = parseLinkLabel(s, i, end, issues)
      if (afterLabel < 0) return -1
      if (afterLabel >= end || s[afterLabel] !== '(') {
        pushIssue(issues, 'После «]» в ссылке ожидается «(» с URL.')
        return -1
      }
      const afterUrl = parseLinkUrl(s, afterLabel, end, issues)
      if (afterUrl < 0) return -1
      i = afterUrl
      continue
    }

    if (isPlainMustEscape(s[i])) {
      pushIssue(
        issues,
        `Символ «${s[i]}» нужно экранировать как \\${s[i]} (позиция ${i + 1}), либо это ошибка разметки.`,
      )
      return -1
    }
    i += 1
  }
  return i
}

function parseRoot(s: string, issues: string[]): number {
  return parseBounded(s, 0, s.length, issues)
}

/**
 * Проверяет текст так, как его разберёт Telegram при parse_mode = MarkdownV2
 * (рассылка из админки).
 */
export function validateTelegramMarkdownV2(text: string): TelegramMarkdownV2Validation {
  const issues: string[] = []
  const t = text
  if (t.length === 0) {
    return { ok: false, issues: ['Введите текст сообщения.'] }
  }
  if (t.length > TELEGRAM_MESSAGE_MAX_LENGTH) {
    issues.push(
      `Длина ${t.length} символов — максимум ${TELEGRAM_MESSAGE_MAX_LENGTH} (как в Telegram API).`,
    )
    return { ok: false, issues }
  }
  const end = parseRoot(t, issues)
  if (end < 0 || end !== t.length) {
    if (issues.length === 0) {
      pushIssue(issues, 'Не удалось разобрать разметку до конца строки.')
    }
    return { ok: false, issues }
  }
  return { ok: true, issues: [] }
}
