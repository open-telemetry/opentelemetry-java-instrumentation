# Style guide

This project follows the
[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

## Repository rules

Repository-specific rules live in the [repository instructions](../../.github/copilot-instructions.md)
and [topic instructions](../../.github/instructions/).

## Formatting

Format source files with Spotless:

```bash
./gradlew spotlessApply
```

Spotless also applies
[custom static importing rules](../../conventions/src/main/kotlin/io/opentelemetry/instrumentation/gradle/StaticImportFormatter.kt)
(for example, rewriting `Objects.requireNonNull` to a static import).

Markdown files are formatted and linted separately through
[flint](https://github.com/grafana/flint), run via [mise](https://mise.jdx.dev/):

```bash
mise run lint:fix
```

Flint also checks that links in Markdown files resolve.

## Pre-commit hook

Enable the repository's pre-commit hook with:

```bash
git config core.hooksPath .githooks
```

## Additional checks

Checkstyle verifies parts of the Google Java Style Guide that auto-formatting cannot handle.

To run these checks locally:

```bash
./gradlew checkstyleMain checkstyleTest
```
