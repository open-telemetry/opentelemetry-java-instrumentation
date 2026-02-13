# Style guideline

We follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

## Code Formatting

### Auto-formatting

The build will fail if the source code is not formatted according to the google java style.

The main goal is to avoid extensive reformatting caused by different IDEs having different opinions
about how things should be formatted by establishing a consistent standard.

Running

```bash
./gradlew spotlessApply
```

reformats all the files that need reformatting.

Running

```bash
./gradlew spotlessCheck
```

runs the formatting verification task only.

#### Pre-commit hook

To completely delegate code style formatting to the machine,
there is a pre-commit hook setup to verify formatting before committing.
It can be activated with this command:

```bash
git config core.hooksPath .githooks
```

#### Editorconfig

As additional convenience for IntelliJ users, we provide an `.editorconfig`
file. IntelliJ will automatically use it to adjust its code formatting settings.
It does not support all required rules, so you still have to run
`spotlessApply` from time to time.

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

### Ordering of class contents

The following order is preferred:

- Static fields (final before non-final)
- Instance fields (final before non-final)
- Constructors
- Methods
- Nested classes

If methods call each other, it's nice if the calling method is ordered (somewhere) above
the method that it calls. For example, a private method would be ordered (somewhere) below
the non-private methods that use it.

In static utility classes (where all members are static), the private constructor
(used to prevent construction) should be ordered after methods instead of before methods.

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

Following the reasoning from [Writing a Java library with better experience (slide 12)](https://speakerdeck.com/trustin/writing-a-java-library-with-better-experience?slide=12),
`java.util.Optional` usage is kept to a minimum.

- `Optional` shouldn't appear in public API signatures
- Avoid `Optional` on the hot path (instrumentation code), unless the instrumented library uses it

## Performance

Avoid allocations on the hot path (instrumentation code) whenever possible. This includes `Iterator`
allocations from collections; note that `for (SomeType t : plainJavaArray)` does not allocate an
iterator object.

Non-allocating Stream API usage on the hot path is acceptable but may not fit the surrounding code
style; this is a judgment call. Some Stream APIs make efficient allocation difficult (e.g.,
`collect` with pre-sized sink data structures involves convoluted `Supplier` code, or lambdas passed
to `forEach` may be capturing/allocating lambdas).
