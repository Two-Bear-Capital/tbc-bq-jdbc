// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import rehypeDocIcons from './src/plugins/rehype-doc-icons.mjs';

// https://astro.build/config
export default defineConfig({
	// Set this to your Cloudflare Pages / custom domain once known, e.g.
	// site: 'https://tbc-bq-jdbc.pages.dev',
	markdown: {
		// Replace emoji glyphs in docs with Starlight icon SVGs at build time.
		rehypePlugins: [rehypeDocIcons],
	},
	// The Javadoc lives at public/api/index.html. `astro dev` doesn't serve the
	// /api/ directory index (Starlight's catch-all would 404 it), so the sidebar
	// links to /javadoc, which redirects to the real file. The redirect source
	// (/javadoc) differs from /api so the build's redirect stub (dist/javadoc/...)
	// never overwrites the real Javadoc at dist/api/index.html. Works in dev,
	// preview, and the deployed build.
	redirects: {
		'/javadoc': '/api/index.html',
	},
	integrations: [
		starlight({
			title: 'TBC BigQuery JDBC Driver',
			description: 'Modern JDBC 4.3 driver for Google BigQuery, built for Java 21+',
			logo: {
				light: './src/assets/tbc-horizontal.svg',
				dark: './src/assets/tbc-horizontal-white.svg',
				replacesTitle: true,
			},
			favicon: '/favicon.svg',
			customCss: ['./src/styles/tbc.css'],
			social: [
				{
					icon: 'github',
					label: 'GitHub',
					href: 'https://github.com/Two-Bear-Capital/tbc-bq-jdbc',
				},
			],
			sidebar: [
				{
					// Explicit order (Quick Start first). Add new guides here.
					label: 'Guides',
					items: [
						{ slug: 'guides/quickstart' },
						{ slug: 'guides/authentication' },
						{ slug: 'guides/connection-properties' },
						{ slug: 'guides/type-mapping' },
						{ slug: 'guides/compatibility' },
						{ slug: 'guides/comparison' },
						{ slug: 'guides/logging' },
						{ slug: 'guides/observability' },
						{ slug: 'guides/intellij' },
						{ slug: 'guides/jetbrains-issues' },
					],
				},
				{
					label: 'API',
					items: [
						{
							// /javadoc → redirected to /api/index.html (see redirects below).
							label: 'Javadoc API ↗',
							link: '/javadoc',
							attrs: { target: '_blank', rel: 'noopener' },
						},
					],
				},
			],
		}),
	],
});
