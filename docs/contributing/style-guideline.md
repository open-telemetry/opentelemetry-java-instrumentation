# Style guideline

We follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

## Auto-formatting

The build will fail if the source code is not formatted according to the google java style.

The main goal is to avoid extensive reformatting caused by different IDEs having different opinion
about how things should be formatted by establishing.

Running

```bash
./gradlew spotlessApply
```

reformats all the files that need reformatting.

Running

```bash
./gradlew spotlessCheck
```

runs formatting verify task only.

### Pre-commit hook

To completely delegate code style formatting to the machine,
there is a pre-commit hook setup to verify formatting before committing.
It can be activated with this command:

```bash
git config core.hooksPath .githooks
```

### Editorconfig

As additional convenience for IntelliJ users, we provide `.editorconfig`
file. IntelliJ will automatically use it to adjust its code formatting settings.
It does not support all required rules, so you still have to run
`spotlessApply` from time to time.

## Additional checks

The build uses checkstyle to verify some parts of the Google Java Style Guide that cannot be handled
by auto-formatting.

To run these checks locally:

```
./gradlew checkstyleMain checkstyleTest
```

## Static imports

We leverage static imports for many common types of operations. However, not all static methods or
constants are necessarily good candidates for a static import. The following list is a very
rough guideline of what are commonly accepted static imports:

* Test assertions (JUnit and AssertJ)
* Mocking/stubbing in tests (with Mockito)
* Collections helpers (such as `singletonList()` and `Collectors.toList()`)
* ByteBuddy `ElementMatchers` (for building instrumentation modules)
* Immutable constants (where clearly named)
* Singleton instances (especially where clearly named an hopefully immutable)
* `tracer()` methods that expose tracer singleton instances

## Ordering of class contents

The following order is preferred:

* Static fields (final before non-final)
* Instance fields (final before non-final)
* Constructors
* Methods
* Nested classes

If methods call each other, it's nice if the calling method is ordered (somewhere) above
the method that it calls. So, for one example, a private method would be ordered (somewhere) below
the non-private methods that use it.

In static utility classes (where all members are static), the private constructor
(used to prevent construction) should be ordered after methods instead of before methods.

## `final` keyword usage

Public classes should be declared `final` where possible.

Methods should only be declared `final` if they are in non-final public classes.

Fields should be declared `final` where possible.

Method parameters and local variables should never be declared `final`.

## `@Nullable` annotation usage

[Note: this section is aspirational, as opposed to a reflection of the current codebase]

All parameters and fields which can be `null` should be annotated with `@Nullable`
(specifically `javax.annotation.Nullable`, which is included by the
`otel.java-conventions` gradle plugin as a `compileOnly` dependency).

There is no need to use `@NonNull`, as this is the default, which should be declared in a
`package-info.java` file on the root package of each module, e.g.

```java
@DefaultQualifier(
    value = NonNull.class,
    locations = {TypeUseLocation.FIELD, TypeUseLocation.PARAMETER, TypeUseLocation.RETURN})
package io.opentelemetry.instrumentation.api;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
```

Public APIs should still defensively check for `null` parameters, even if the parameter is not
annotated with `@Nullable`. Internal APIs do not need to defensively check for `null` parameters.

To help enforce `@Nullable` annotation usage, the `otel.nullaway-conventions` gradle plugin
should be used in all modules to perform basic nullable usage validation:

```kotlin
plugins {
  id("otel.nullaway-conventions")
}
```
