# Style Guide

This project follows the
[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

## Code Formatting

### Auto-formatting

The build will fail if source code is not formatted according to Google Java
Style.

Run the following command to reformat all files:

```bash
./gradlew spotlessApply
```

For IntelliJ users, an `.editorconfig` file is provided that IntelliJ will automatically use to
adjust code formatting settings. However, it does not support all required rules, so you may still
need to run `./gradlew spotlessApply` periodically.

#### Pre-commit hook

To completely delegate code style formatting to the machine,
there is a pre-commit hook setup to verify formatting before committing.
It can be activated with this command:

```bash
git config core.hooksPath .githooks
```

### Additional checks

The build uses checkstyle to verify some parts of the Google Java Style Guide that cannot be handled
by auto-formatting.

To run these checks locally:

```
./gradlew checkstyleMain checkstyleTest
```

### Static imports

Consider statically importing the following commonly used methods and constants:

- **Test methods**
  - `io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.*`
  - `org.assertj.core.api.Assertions.*`
  - `org.mockito.Mockito.*`
  - `org.mockito.ArgumentMatchers.*`
- **Utility methods**
  - `io.opentelemetry.api.common.AttributeKey.*`
  - `java.util.Arrays` - `asList`, `stream`
  - `java.util.Collections` - `singleton*`, `empty*`, `unmodifiable*`, `synchronized*`, `checked*`
  - `java.util.Objects` - `requireNonNull`
  - `java.util.function.Function` - `identity`
  - `java.util.stream.Collectors.*`
- **Utility constants**
  - `java.util.Locale.*`
  - `java.util.concurrent.TimeUnit.*`
  - `java.util.logging.Level.*`
  - `java.nio.charset.StandardCharsets.*`
- **ByteBuddy**
  - `net.bytebuddy.matcher.ElementMatchers.*`
  - `io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.*`
- **OpenTelemetry semantic convention constants**
  - All constants under `io.opentelemetry.semconv.**`, except for
    `io.opentelemetry.semconv.SchemaUrls.*` constants

Some of these are enforced by checkstyle rules:

- Look for `RegexpSinglelineJava` in `checkstyle.xml`
- Use `@SuppressWarnings("checkstyle:RegexpSinglelineJava")` to suppress the checkstyle warning

## Java Language Conventions

### Visibility modifiers

Follow the principle of minimal necessary visibility. Use the most restrictive access modifier that
still allows the code to function correctly.

### Internal packages

Classes in `.internal` packages are not considered public API and may change without notice. These
packages contain implementation details that should not be used by external consumers.

- Use `.internal` packages for implementation classes that need to be public within the module but
  should not be used externally
- Try to avoid referencing `.internal` classes from other modules

### Class organization

Prefer this order:

- Static fields (final before non-final)
- Instance fields (final before non-final)
- Constructors
- Methods
- Nested classes

**Method ordering**: Place calling methods above the methods they call. For example, place private
methods below the non-private methods that use them.

**Static utility classes**: Place the private constructor (used to prevent instantiation) after all
methods.

### `final` keyword usage

Public non-internal non-test classes should be declared `final` where possible.

Methods should only be declared `final` if they are in public non-internal non-test non-final classes.

Fields should be declared `final` where possible.

Method parameters and local variables should never be declared `final`.

### `@Nullable` annotation usage

**Note: This section is aspirational and may not reflect the current codebase.**

Annotate all parameters and fields that can be `null` with `@Nullable`
(specifically `javax.annotation.Nullable`, which is included by the
`otel.java-conventions` Gradle plugin as a `compileOnly` dependency).

`@NonNull` is unnecessary as it is the default.

**Defensive programming**: Public APIs should still check for `null` parameters even if not
annotated with `@Nullable`. Internal APIs do not need these checks.

To help enforce `@Nullable` annotation usage, the `otel.nullaway-conventions` Gradle plugin
should be used in all modules to perform basic nullable usage validation:

```kotlin
plugins {
  id("otel.nullaway-conventions")
}
```

### `Optional` usage

Following the reasoning from
[Writing a Java library with better experience (slide 12)](https://speakerdeck.com/trustin/writing-a-java-library-with-better-experience?slide=12),
`java.util.Optional` usage is kept to a minimum.

**Guidelines**:

- `Optional` shouldn't appear in public API signatures
- Avoid `Optional` on the hot path (instrumentation code), unless the instrumented library uses it

## Semantic convention constants

**Library instrumentation**: Copy semantic convention constants directly into library
instrumentation classes rather than depending on the semconv artifact. Library instrumentation is
used by end users, and this avoids exposing a dependency on the semconv artifact (which may change
across versions). For example:

```java
// copied from MessagingIncubatingAttributes
private static final AttributeKey<String> MESSAGING_SYSTEM =
    AttributeKey.stringKey("messaging.system");
```

**Javaagent instrumentation**: Use the semconv constants from the semconv artifact directly. The
javaagent bundles its own dependencies, so there is no risk of version conflicts for end users.

**Tests**: Use the semconv constants from the semconv artifact directly. Test dependencies do not
affect end users.

## Tooling conventions

### AssertJ

Prefer AssertJ assertions over JUnit assertions (assertEquals, assertTrue, etc.) for better
error messages.

### JUnit

Test classes and test methods should generally be package-protected (no explicit visibility
modifier) rather than `public`. This follows the principle of minimal necessary visibility and is
sufficient for JUnit to discover and execute tests.

## Performance

Avoid allocations on the hot path (instrumentation code) whenever possible. This includes `Iterator`
allocations from collections; note that `for (SomeType t : plainJavaArray)` does not allocate an
iterator object.

Non-allocating Stream API usage on the hot path is acceptable but may not fit the surrounding code
style; this is a judgment call. Some Stream APIs make efficient allocation difficult (e.g.,
`collect` with pre-sized sink data structures involves convoluted `Supplier` code, or lambdas passed
to `forEach` may be capturing/allocating lambdas).
