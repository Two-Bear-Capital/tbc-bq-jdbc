/**
 * Sync the repository's Markdown docs into the Starlight content collection.
 *
 * The repo keeps its docs as plain GitHub-readable Markdown under ../docs (no
 * frontmatter). Starlight requires a `title` in frontmatter, so rather than
 * editing every doc, this script derives the title from the first H1 and writes
 * frontmatter'd copies into src/content/docs/. The output dirs are gitignored —
 * they are build artifacts, never edited by hand.
 *
 *   ../docs/*.md            -> src/content/docs/guides/<slug>.md      (Guides)
 *   ../docs/generated/*.md  -> src/content/docs/reference/<slug>.md   (Reference, from DocGen)
 *
 * Doc scoping convention: only TOP-LEVEL ../docs/*.md is user-facing and synced
 * here. Contributor/build/release docs live in ../docs/contributing/ (a
 * subdirectory) and are intentionally NOT synced — this script ignores
 * subdirectories. Keep "how to use the driver" at the top level; keep "how to
 * build/test/release" under docs/contributing/.
 */
import {
	readdirSync,
	readFileSync,
	writeFileSync,
	mkdirSync,
	rmSync,
	existsSync,
} from 'node:fs';
import { join, resolve, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = fileURLToPath(new URL('.', import.meta.url));
const websiteDir = resolve(scriptDir, '..');
const repoRoot = resolve(websiteDir, '..');

const docsDir = join(repoRoot, 'docs');
const generatedDir = join(docsDir, 'generated');
const contentDir = join(websiteDir, 'src', 'content', 'docs');
const guidesOut = join(contentDir, 'guides');
const referenceOut = join(contentDir, 'reference');

/** Turn a file name like CONNECTION_PROPERTIES.md into "connection-properties". */
function slugify(fileName) {
	return fileName
		.slice(0, fileName.length - extname(fileName).length)
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, '-')
		.replace(/^-+|-+$/g, '');
}

/** Remove a leading HTML comment block (e.g. DocGen's DO-NOT-EDIT banner). */
function stripLeadingComment(md) {
	return md.replace(/^\s*<!--[\s\S]*?-->\s*/, '');
}

/**
 * Build the Starlight page: strip leading comment, pull the first H1 out to use
 * as the frontmatter title (avoiding a duplicate heading), prepend frontmatter.
 */
function toStarlightPage(md, fallbackTitle) {
	const lines = stripLeadingComment(md).split('\n');
	let title = fallbackTitle;
	for (let i = 0; i < lines.length; i++) {
		const match = lines[i].match(/^#\s+(.+?)\s*$/);
		if (match) {
			title = match[1];
			lines.splice(i, 1);
			break;
		}
	}
	const body = lines.join('\n').replace(/^\n+/, '');
	return `---\ntitle: ${JSON.stringify(title)}\n---\n\n${body}`;
}

function resetDir(dir) {
	rmSync(dir, { recursive: true, force: true });
	mkdirSync(dir, { recursive: true });
}

/** Copy every top-level *.md in srcDir into outDir as a Starlight page. */
function syncDir(srcDir, outDir) {
	if (!existsSync(srcDir)) {
		return [];
	}
	const written = [];
	for (const entry of readdirSync(srcDir, { withFileTypes: true })) {
		if (!entry.isFile() || !entry.name.endsWith('.md')) {
			continue;
		}
		const slug = slugify(entry.name);
		const raw = readFileSync(join(srcDir, entry.name), 'utf8');
		writeFileSync(join(outDir, `${slug}.md`), toStarlightPage(raw, slug), 'utf8');
		written.push(slug);
	}
	return written;
}

resetDir(guidesOut);
resetDir(referenceOut);
const guides = syncDir(docsDir, guidesOut);
const reference = syncDir(generatedDir, referenceOut);

if (guides.length === 0) {
	console.warn(`[sync-docs] warning: no guide docs found in ${docsDir}`);
}
console.log(
	`[sync-docs] ${guides.length} guide(s), ${reference.length} reference page(s)`,
);
