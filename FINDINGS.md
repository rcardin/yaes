# Site Migration Findings

## Chunk 1 — Completed

### Key Decisions

1. **Placeholder pages required**: Starlight validates all sidebar slugs at build time and fails if any referenced page doesn't exist. Steps 3-8, HTTP, Integrations, and Community pages were created as placeholders so the full sidebar config can be committed from the start. Each subsequent chunk fills in the real content.

2. **`create-astro` flags**: Used `npx create-astro@latest website --template starlight --no-install --no-git --yes` for non-interactive scaffold (note: directory goes before `--`, not after with `-- --template`).

3. **Base path**: Set `base: '/yaes'` and `site: 'https://rcardin.github.io'` in `astro.config.mjs`. All internal links in homepage use `/yaes/` prefix.

4. **Logo**: The default `houston.webp` from the Starlight template was replaced with `logo.svg`. The old default asset files were left in place (they are not referenced and don't affect build).

5. **Default template directories removed**: `src/content/docs/guides/` and `src/content/docs/reference/` were deleted since they are replaced by our structure.

6. **CSS variables**: Starlight uses `--sl-color-*` custom properties, not the generic `--bg-*` from the Docsify theme. The Catppuccin colors were mapped to the Starlight variable set. The `[data-theme='dark']` and `[data-theme='light']` selectors are Starlight's built-in theme toggle mechanism.

### For Next Chunks

- **Chunk 2** fills in steps 3, 4, 5 — replace placeholder content in:
  - `website/src/content/docs/learn/3-basic-effects.md`
  - `website/src/content/docs/learn/4-error-handling.md`
  - `website/src/content/docs/learn/5-concurrency.md`
- **Chunk 3** fills in steps 6, 7, 8
- **Chunk 4** fills in HTTP, Integrations, Community + CI/CD + cleanup
- All internal links in content pages should use `/yaes/` prefix (e.g., `/yaes/learn/2-core-concepts/`)
- `data-structures.md` is 3743 lines — read in two passes (lines 1-2000 then 2000+) as the task notes warn

## Chunk 3 — Step 7 completed

### Key Decisions

1. **Page structure**: Organized as intro → Flow → Reactive Streams Integration (FlowPublisher) → Channels → Combining Flows with Channels (channelFlow + buffer)

2. **FlowPublisher section**: data-structures.md has a very large Reactive Streams Integration section (~2700 lines from line 1010 onward). All content was preserved in the merged page.

3. **channelFlow and buffer** are documented in `communication-primitives.md`, not `data-structures.md` — they bridge channels and flows, so they go in the "Combining" section at the end.

### For Next Tasks

- **3.3**: Step 8 — merge `docs/yaes-app.md` (301 lines) + `docs/examples.md` (336 lines)
  - This is the capstone page — show how everything comes together
  - `website/src/content/docs/learn/8-building-applications.md` is currently a placeholder
- **3.4**: Verify full learning path navigation (steps 1→8)
- **Chunk 4**: HTTP pages, Integrations, Community, CI/CD, final cleanup
