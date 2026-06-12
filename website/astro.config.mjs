// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	// Set this to your Cloudflare Pages / custom domain once known, e.g.
	// site: 'https://tbc-bq-jdbc.pages.dev',
	integrations: [
		starlight({
			title: 'tbc-bq-jdbc',
			description: 'Modern JDBC 4.3 driver for Google BigQuery, built for Java 21+',
			logo: {
				light: './src/assets/tbc-horizontal.svg',
				dark: './src/assets/tbc-horizontal-white.svg',
				replacesTitle: true,
			},
			favicon: '/favicon.svg',
			customCss: ['./src/styles/tbc.css'],
			social: {
				github: 'https://github.com/Two-Bear-Capital/tbc-bq-jdbc',
			},
			sidebar: [
				{
					label: 'Guides',
					autogenerate: { directory: 'guides' },
				},
				{
					// Pages generated from the driver source of truth (DocGen).
					label: 'Reference',
					autogenerate: { directory: 'reference' },
				},
				{
					label: 'API',
					items: [
						{
							label: 'Javadoc API ↗',
							link: '/api/',
							attrs: { target: '_blank', rel: 'noopener' },
						},
					],
				},
			],
		}),
	],
});
