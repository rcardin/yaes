// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://rcardin.github.io',
	base: '/yaes',
	integrations: [
		starlight({
			title: 'λÆS — Yet Another Effect System',
			logo: {
				src: './src/assets/logo.svg',
			},
			customCss: ['./src/styles/custom.css'],
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/rcardin/yaes' },
			],
			sidebar: [
				{
					label: 'Learn λÆS',
					items: [
						{ label: '1. Getting Started', slug: 'learn/1-getting-started' },
						{ label: '2. Core Concepts', slug: 'learn/2-core-concepts' },
						{ label: '3. Basic Effects', slug: 'learn/3-basic-effects' },
						{ label: '4. Error Handling', slug: 'learn/4-error-handling' },
						{ label: '5. Concurrency', slug: 'learn/5-concurrency' },
						{ label: '6. State & Resources', slug: 'learn/6-state-and-resources' },
						{ label: '7. Streams & Channels', slug: 'learn/7-streams-and-channels' },
						{ label: '8. Building Applications', slug: 'learn/8-building-applications' },
					],
				},
				{
					label: 'HTTP Module',
					collapsed: true,
					items: [
						{ label: 'HTTP Server', slug: 'http/server' },
						{ label: 'HTTP Client', slug: 'http/client' },
						{ label: 'JSON with Circe', slug: 'http/circe' },
					],
				},
				{
					label: 'Integrations',
					collapsed: true,
					items: [
						{ label: 'Cats Effect', slug: 'integrations/cats-effect' },
						{ label: 'SLF4J Logging', slug: 'integrations/slf4j-logging' },
					],
				},
				{
					label: 'Community',
					items: [
						{ label: 'Contributing', slug: 'community/contributing' },
						{ label: 'GitHub ↗', link: 'https://github.com/rcardin/yaes', attrs: { target: '_blank' } },
						{ label: 'Maven Central ↗', link: 'https://central.sonatype.com/artifact/in.rcard.yaes/yaes-core_3', attrs: { target: '_blank' } },
					],
				},
			],
		}),
	],
});
