/**
 * 极简 Markdown 渲染
 * <p>
 * 模型回包里的 `**加粗**`、`- 列表`、`1. 列表` 直接以纯文本显示时，页面上会出现一堆星号，
 * 读起来很别扭。这里不引第三方库（体积、安全都要额外考虑），只支持对话回答真正会用到的几种语法：
 * 加粗、行内代码、标题、有序/无序列表、空行分段。
 * </p>
 * <p>
 * 安全策略：<b>先整体转义 HTML，再拼接自己生成的标签</b>，因此模型输出里即使带
 * `<script>` 也只会被当作文字显示，可以放心用 v-html。
 * </p>
 */

/** HTML 转义 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** 行内语法：加粗 + 行内代码 */
function renderInline(text: string): string {
  return text
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    // 单个星号斜体在中文语境里误伤率高（如 3*4），只处理成对且不含空格的场景
    .replace(/(^|[\s(])\*([^*\s][^*]*)\*(?=[\s).,，。；;]|$)/g, '$1<em>$2</em>');
}

/**
 * 把回答文本渲染为安全的 HTML 片段
 *
 * @param text 原始回答
 */
export function renderMarkdown(text: string): string {
  if (!text) {
    return '';
  }
  const lines = escapeHtml(text).split('\n');
  const html: string[] = [];
  /** 当前打开的列表类型 */
  let listType: 'ul' | 'ol' | null = null;
  /** 当前段落缓冲 */
  let paragraph: string[] = [];

  const flushParagraph = (): void => {
    if (paragraph.length) {
      html.push(`<p>${renderInline(paragraph.join('<br>'))}</p>`);
      paragraph = [];
    }
  };

  const closeList = (): void => {
    if (listType) {
      html.push(`</${listType}>`);
      listType = null;
    }
  };

  for (const raw of lines) {
    const line = raw.trimEnd();
    if (!line.trim()) {
      flushParagraph();
      closeList();
      continue;
    }

    // 标题 ### / ## / #
    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      flushParagraph();
      closeList();
      const level = Math.min(heading[1].length + 2, 6);
      html.push(`<h${level}>${renderInline(heading[2])}</h${level}>`);
      continue;
    }

    // 分隔线
    if (/^([-*_])\1{2,}$/.test(line.trim())) {
      flushParagraph();
      closeList();
      html.push('<hr>');
      continue;
    }

    // 无序列表
    const unordered = /^\s*[-*+]\s+(.*)$/.exec(line);
    if (unordered) {
      flushParagraph();
      if (listType !== 'ul') {
        closeList();
        listType = 'ul';
        html.push('<ul>');
      }
      html.push(`<li>${renderInline(unordered[1])}</li>`);
      continue;
    }

    // 有序列表
    const ordered = /^\s*(\d+)[.)]\s+(.*)$/.exec(line);
    if (ordered) {
      flushParagraph();
      if (listType !== 'ol') {
        closeList();
        listType = 'ol';
        // 列表项之间夹着续行时会被拆成多个 <ol>，必须带上 start，否则编号又从 1 开始
        const start = Number(ordered[1]);
        html.push(start > 1 ? `<ol start="${start}">` : '<ol>');
      }
      html.push(`<li>${renderInline(ordered[2])}</li>`);
      continue;
    }

    // 列表项的续行（缩进 2 空格以上）：并回上一项，否则会散成独立段落
    const continuation = /^\s{2,}(\S.*)$/.exec(line);
    if (continuation && listType && html.length > 0) {
      const last = html.pop() as string;
      if (last.endsWith('</li>')) {
        html.push(last.replace(/<\/li>$/, `<br>${renderInline(continuation[1])}</li>`));
        continue;
      }
      html.push(last);
    }

    closeList();
    paragraph.push(line);
  }

  flushParagraph();
  closeList();
  return html.join('');
}
