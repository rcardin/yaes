---
title: Contributing
description: Guidelines for contributing to λÆS.
sidebar:
  label: Contributing
  order: 1
---

# Contributing to λÆS

Thank you for your interest in contributing to λÆS! Any help is welcome.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/yaes.git
   cd yaes
   ```
3. **Set up the development environment**:
   ```bash
   # Ensure you have sbt installed
   sbt compile
   sbt test
   ```

## Development Workflow

### Running Tests

```bash
# Run all tests
sbt test

# Run tests for a specific module
sbt yaes-core/test
sbt yaes-data/test

# Run tests continuously
sbt ~test
```

### Code Style

λÆS follows standard Scala conventions:

- Use 2 spaces for indentation
- Line length should not exceed 120 characters
- Use meaningful variable and function names
- Add documentation for public APIs

### Making Changes

1. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** with appropriate tests

3. **Run the full test suite**:
   ```bash
   sbt clean test
   ```

4. **Commit your changes**:
   ```bash
   git add .
   git commit -m "Add: description of your changes"
   ```

5. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Create a Pull Request** on GitHub

## What Can You Contribute?

### New Effects

λÆS is always looking for new effects! Consider adding:

- Database effects (SQL, NoSQL)
- HTTP client effects
- File system effects
- Caching effects
- Metrics and monitoring effects

### Documentation

- Improve existing documentation
- Add more examples
- Create tutorials
- Fix typos and grammar

### Bug Fixes

- Check the issue tracker for bugs
- Add reproduction tests
- Fix the issue
- Verify the fix works

### Performance Improvements

- Benchmark existing code
- Optimize hot paths
- Reduce memory allocations
- Improve concurrency

## Project Structure

```
yaes/
├── yaes-core/          # Main effects library
│   ├── src/main/       # Source code
│   └── src/test/       # Tests
├── yaes-data/          # Data structures
│   ├── src/main/       # Source code
│   └── src/test/       # Tests
├── docs/               # Documentation source files
├── website/            # Starlight documentation site
└── README.md           # Main documentation
```

## Code Review Process

All contributions go through code review:

1. **Automated checks** run on your PR (CI/CD)
2. **Manual review** by maintainers
3. **Feedback** and requested changes
4. **Approval** and merge

## Questions?

- Open an issue for questions about the library
- Start a discussion for feature requests
- Join the community discussions

## Acknowledgments

λÆS has been influenced by many great engineers and projects. Special thanks to:

- **Daniel Ciocîrlan** - Mentor and inspiration
- **Simon Vergauwen** - Arrow Kt library insights
- **Jon Pretty** - Raise effect discussions
- **Noel Welsh** - Functional error handling insights
- **Flavio Brasil** - Kyo library inspiration

Thank you for helping make λÆS better!
> This page is coming soon. Content will be added in a subsequent migration step.
