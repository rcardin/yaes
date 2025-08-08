# λÆS Documentation Site

This directory contains the Jekyll-based documentation site for λÆS (Yet Another Effect System).

## Local Development

### Prerequisites

- Ruby 3.1 or later
- Bundler

### Setup

1. Install dependencies:
   ```bash
   cd docs
   bundle install
   ```

2. Run the development server:
   ```bash
   bundle exec jekyll serve
   ```

3. Open your browser to `http://localhost:4000`

### Building for Production

```bash
bundle exec jekyll build
```

The built site will be in the `_site` directory.

## Deployment

The site is automatically deployed to GitHub Pages when changes are pushed to the `main` branch using GitHub Actions.

## Structure

- `_config.yml` - Jekyll configuration
- `_layouts/` - Page layouts
- `effects/` - Effect documentation pages
- `index.md` - Homepage
- `getting-started.md` - Getting started guide
- `examples.md` - Practical examples
- `contributing.md` - Contributing guidelines
