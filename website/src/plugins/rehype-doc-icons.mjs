/**
 * rehype plugin: replace emoji glyphs in the docs with Starlight's built-in
 * icon SVGs at build time. The source Markdown keeps emoji (so it stays
 * GitHub-readable); the rendered site shows proper, brand-colorable icons.
 *
 * SVG path data is copied verbatim from @astrojs/starlight/components/Icons.ts
 * so we don't depend on a deep, unexported import path.
 *
 * Emojis with no good Starlight equivalent (🔒 📊 💰) are stripped on the site
 * rather than rendered as a mismatched icon.
 */

// Inner SVG markup for each icon (24x24 viewBox), from Starlight's icon set.
const ICON_INNER = {
	ok: '<path d="m14.72 8.79-4.29 4.3-1.65-1.65a1 1 0 1 0-1.41 1.41l2.35 2.36a1 1 0 0 0 1.41 0l5-5a1.002 1.002 0 1 0-1.41-1.42ZM12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 18a8 8 0 1 1 0-16.001A8 8 0 0 1 12 20Z"/>',
	no: '<path d="m13.41 12 6.3-6.29a1.004 1.004 0 1 0-1.42-1.42L12 10.59l-6.29-6.3a1.004 1.004 0 0 0-1.42 1.42l6.3 6.29-6.3 6.29a1 1 0 0 0 0 1.42.998.998 0 0 0 1.42 0l6.29-6.3 6.29 6.3a.999.999 0 0 0 1.42 0 1 1 0 0 0 0-1.42L13.41 12Z"/>',
	warn: '<path d="M12 16a1 1 0 1 0 0 2 1 1 0 0 0 0-2Zm10.67 1.47-8.05-14a3 3 0 0 0-5.24 0l-8 14A3 3 0 0 0 3.94 22h16.12a3 3 0 0 0 2.61-4.53Zm-1.73 2a1 1 0 0 1-.88.51H3.94a1 1 0 0 1-.88-.51 1 1 0 0 1 0-1l8-14a1 1 0 0 1 1.78 0l8.05 14a1 1 0 0 1 .05 1.02v-.02ZM12 8a1 1 0 0 0-1 1v4a1 1 0 0 0 2 0V9a1 1 0 0 0-1-1Z"/>',
	rocket: '<path fill-rule="evenodd" d="M1.44 8.855v-.001l3.527-3.516c.34-.344.802-.541 1.285-.548h6.649l.947-.947c3.07-3.07 6.207-3.072 7.62-2.868a1.821 1.821 0 0 1 1.557 1.557c.204 1.413.203 4.55-2.868 7.62l-.946.946v6.649a1.845 1.845 0 0 1-.549 1.286l-3.516 3.528a1.844 1.844 0 0 1-3.11-.944l-.858-4.275-4.52-4.52-2.31-.463-1.964-.394A1.847 1.847 0 0 1 .98 10.693a1.843 1.843 0 0 1 .46-1.838Zm5.379 2.017-3.873-.776L6.32 6.733h4.638l-4.14 4.14Zm8.403-5.655c2.459-2.46 4.856-2.463 5.89-2.33.134 1.035.13 3.432-2.329 5.891l-6.71 6.71-3.561-3.56 6.71-6.711Zm-1.318 15.837-.776-3.873 4.14-4.14v4.639l-3.364 3.374Z" clip-rule="evenodd"/><path d="M9.318 18.345a.972.972 0 0 0-1.86-.561c-.482 1.435-1.687 2.204-2.934 2.619a8.22 8.22 0 0 1-1.23.302c.062-.365.157-.79.303-1.229.415-1.247 1.184-2.452 2.62-2.935a.971.971 0 1 0-.62-1.842c-.12.04-.236.084-.35.13-2.02.828-3.012 2.588-3.493 4.033a10.383 10.383 0 0 0-.51 2.845l-.001.016v.063c0 .536.434.972.97.972H2.24a7.21 7.21 0 0 0 .878-.065c.527-.063 1.248-.19 2.02-.447 1.445-.48 3.205-1.472 4.033-3.494a5.828 5.828 0 0 0 .147-.407Z"/>',
	book: '<path d="M21.17 2.06A13.1 13.1 0 0 0 19 1.87a12.94 12.94 0 0 0-7 2.05 12.94 12.94 0 0 0-7-2 13.1 13.1 0 0 0-2.17.19 1 1 0 0 0-.83 1v12a1 1 0 0 0 1.17 1 10.9 10.9 0 0 1 8.25 1.91l.12.07h.11a.91.91 0 0 0 .7 0h.11l.12-.07A10.899 10.899 0 0 1 20.83 16 1 1 0 0 0 22 15V3a1 1 0 0 0-.83-.94ZM11 15.35a12.87 12.87 0 0 0-6-1.48H4v-10c.333-.02.667-.02 1 0a10.86 10.86 0 0 1 6 1.8v9.68Zm9-1.44h-1a12.87 12.87 0 0 0-6 1.48V5.67a10.86 10.86 0 0 1 6-1.8c.333-.02.667-.02 1 0v10.04Zm1.17 4.15a13.098 13.098 0 0 0-2.17-.19 12.94 12.94 0 0 0-7 2.05 12.94 12.94 0 0 0-7-2.05c-.727.003-1.453.066-2.17.19A1 1 0 0 0 2 19.21a1 1 0 0 0 1.17.79 10.9 10.9 0 0 1 8.25 1.91 1 1 0 0 0 1.16 0A10.9 10.9 0 0 1 20.83 20a1 1 0 0 0 1.17-.79 1 1 0 0 0-.83-1.15Z"/>',
};

// emoji -> icon kind
const EMOJI_TO_ICON = {
	'✅': 'ok', // ✅
	'❌': 'no', // ❌
	'⚠': 'warn', // ⚠
	'\u{1F680}': 'rocket', // 🚀
	'\u{1F4D6}': 'book', // 📖
};

// emoji with no good Starlight icon — removed on the site
const STRIP = new Set(['\u{1F512}', '\u{1F4CA}', '\u{1F4B0}']); // 🔒 📊 💰

const GLYPHS = [...Object.keys(EMOJI_TO_ICON), ...STRIP];
// match any handled glyph, plus an optional trailing variation selector (U+FE0F)
const GLYPH_RE = new RegExp(`(?:${GLYPHS.join('|')})\\uFE0F?`, 'gu');

function iconSvg(kind) {
	return `<svg class="doc-icon doc-icon--${kind}" viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor" aria-hidden="true" focusable="false">${ICON_INNER[kind]}</svg>`;
}

/** Split a text value into text + raw-SVG nodes, or null if no glyphs present. */
function splitText(value) {
	const matches = [...value.matchAll(GLYPH_RE)];
	if (matches.length === 0) {
		return null;
	}
	const nodes = [];
	let last = 0;
	for (const match of matches) {
		if (match.index > last) {
			nodes.push({ type: 'text', value: value.slice(last, match.index) });
		}
		const glyph = [...match[0]][0]; // first code point (drop any variation selector)
		const kind = EMOJI_TO_ICON[glyph];
		if (kind) {
			nodes.push({ type: 'raw', value: iconSvg(kind) });
		}
		// STRIP glyphs contribute nothing
		last = match.index + match[0].length;
	}
	if (last < value.length) {
		nodes.push({ type: 'text', value: value.slice(last) });
	}
	return nodes;
}

function transform(node) {
	if (!node.children) {
		return;
	}
	const out = [];
	for (const child of node.children) {
		if (child.type === 'text') {
			const replacement = splitText(child.value);
			if (replacement) {
				out.push(...replacement);
			} else {
				out.push(child);
			}
		} else if (child.type === 'element' && (child.tagName === 'code' || child.tagName === 'pre')) {
			// leave code samples untouched
			out.push(child);
		} else {
			transform(child);
			out.push(child);
		}
	}
	node.children = out;
}

export default function rehypeDocIcons() {
	return (tree) => transform(tree);
}
