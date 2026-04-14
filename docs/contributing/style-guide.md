# Style Guide

This project follows the
[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

## Code Formatting

### Auto-formatting

One of the CI checks will fail if source code is not formatted according to Google Java Style.

Run the following command to reformat all files:

```bash
./gradlew spotlessApply
```

In addition to Google Java Style formatting, spotless applies
[custom static importing rules](../../conventions/src/main/kotlin/io/opentelemetry/instrumentation/gradle/StaticImportFormatter.kt)
(e.g. rewriting `Objects.requireNonNull` to a static import).

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

## Java Language Conventions

### Visibility modifiers

Follow the principle of minimal necessary visibility. Use the most restrictive access modifier that
still allows the code to function correctly.

### Internal packages

Classes in `.internal` packages are not considered public API and may change without notice. These
packages contain implementation details that should not be used by external consumers.

Use `.internal` packages for implementation classes that need to be public within the module but
should not be used externally

### Class organization

Prefer this order:

- Static fields (final before non-final)
- Static initializer
- Instance fields (final before non-final)
- Constructors
- Methods
- Nested classes

**Method ordering**: Place calling methods above the methods they call. For example, place private
methods below the non-private methods that use them.

**Exception — static field initialization**: When a `static final` field is initialized by a
private static method or a `static {}` block, it is acceptable to place the method or block
immediately after the field to keep initialization logic co-located, even when this contradicts
the general method ordering above.

**Static factory entry points**: When a class exposes public static factory methods as its primary
creation API (for example `create*(...)` or `builder(...)`), place those methods below fields and
immediately above constructors. Treat static factory methods and constructors as a single
construction section.

**Static utility classes**: Place the private constructor (used to prevent instantiation) after all
methods.

### `final` keyword usage

**Classes**: Declare public classes `final` where possible, but only in public API code.

The following are **not** public API — do not add `final` to classes there:

- `javaagent/src/main/` — internal implementation detail, even when classes are `public` for
  service loading or cross-package access
- `.internal` packages
- Test code — `src/test/` directories and modules whose directory name starts or ends with
  `testing` or `tests` (e.g., `testing/`, `testing-common/`, `quarkus-2.0-testing/`,
  `smoke-tests/`)

**Methods**: Declare `final` only in non-final public API classes.

**Fields**: Declare `final` where possible.

**Parameters and local variables**: Never declare `final`.

### Null comparisons

Prefer `value == null` and `value != null` over left-hand null comparisons such as
`null == value` and `null != value`.

This applies throughout the codebase, including Java, Kotlin, and Scala sources.

### Uppercase field names

Use uppercase (`SCREAMING_SNAKE_CASE`) for constant-like fields whose value is treated as a stable
identifier, immutable descriptor, or immutable value constant.

Examples that may remain uppercase include:

- literal strings, numbers, and booleans that behave like module constants
- immutable value objects that are treated as fixed constants after initialization, such as
  `Duration` timeouts, intervals, or deadlines
- semantic keys and handles such as `AttributeKey`, `ContextKey`, `VirtualField`,
  `MethodHandle`, and `Pattern`
- canonical singleton or sentinel fields named `INSTANCE`, `EMPTY`, or `NOOP`

Do not use uppercase solely because a field is `static final`.

Use lower camel case for runtime-created collaborator objects even when they are `static final`,
for example loggers, instrumenters, helpers, sanitizers, mappers, caches, and similar service
objects.

When deciding between uppercase and lower camel case, distinguish immutable value constants from
collaborators. A `private static final Duration FLUSH_TIMEOUT = ...;` field may remain uppercase
when it is used as a fixed timeout constant, even if its value is computed from configuration at
startup. In contrast, runtime-created service objects such as instrumenters, tracers, loggers, or
helpers should use lower camel case.

### `@Nullable` annotation usage

**Note: This section is aspirational and may not reflect the current codebase.**

Annotate all parameters and fields that can be `null` with `@Nullable`
(specifically `javax.annotation.Nullable`, which is included by the
`otel.java-conventions` Gradle plugin as a `compileOnly` dependency).

`@NonNull` is unnecessary as it is the default.

**Test code**: Do not add `@Nullable` annotations in test code.

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
