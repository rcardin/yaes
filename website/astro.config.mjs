// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://www.yaes.io',
	integrations: [
		starlight({
			title: 'λÆS — Yet Another Effect System',
			logo: {
				src: './src/assets/logo-icon-square.svg',
			},
			customCss: ['./src/styles/custom.css'],
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/yaes-io/yaes' },
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
					label: 'Advanced',
					collapsed: true,
					items: [
						{ label: 'Unscoped Effect', slug: 'advanced/unscoped-effect' },
					],
				},
				{
					label: 'HTTP Module',
					collapsed: true,
					items: [
						{ label: 'HTTP Server', slug: 'http/server' },
						{ label: 'HTTP Client', slug: 'http/client' },
						{ label: 'JSON with Circe', slug: 'http/circe' },
						{ label: 'JSON with jsoniter-scala', slug: 'http/jsoniter' },
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
					label: 'Testing',
					collapsed: true,
					items: [
						{ label: 'Testing with RaiseSpec', slug: 'testing/raise-spec' },
						{ label: 'Testing HTTP with StubHttpServer', slug: 'testing/stub-http-server' },
					],
				},
				{
					label: 'Community',
					items: [
						{ label: 'Contributing', slug: 'community/contributing' },
						{ label: 'GitHub ↗', link: 'https://github.com/yaes-io/yaes', attrs: { target: '_blank' } },
						{ label: 'Maven Central ↗', link: 'https://central.sonatype.com/artifact/io.yaes/yaes-core_3', attrs: { target: '_blank' } },
					],
				},
			],
		}),
	],
});
