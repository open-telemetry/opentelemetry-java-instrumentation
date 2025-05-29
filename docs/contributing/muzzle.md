# Muzzle

Muzzle is a safety feature of the Java agent that prevents applying instrumentation when a mismatch
between the instrumentation code and the instrumented application code is detected.
It ensures API compatibility between symbols (classes, methods, fields) on the application classpath
and references to those symbols made by instrumentation advices defined in the agent.
In other words, muzzle ensures that the API symbols used by the agent are compatible with the API
symbols on the application classpath.

Muzzle will prevent loading an instrumentation if it detects any mismatch or conflict.

Muzzle's dependency graph and class injection are encountered especially during the writing of
[`instrumentation modules`](writing-instrumentation-module.md). This functionality is required if
the packaged instrumentation utilizes `VirtualField`.

## How it works

Muzzle has two phases:

- at compile time it collects references to the third-party symbols and used helper classes;
- at runtime it compares those references to the actual API symbols on the classpath.

### Compile-time reference collection

The compile-time reference collection and code generation process is implemented using a Gradle
plugin ([`io.opentelemetry.instrumentation.muzzle-generation`](https://plugins.gradle.org/plugin/io.opentelemetry.instrumentation.muzzle-generation)).

For each instrumentation module the code generation plugin first applies
the `InstrumentationModuleMuzzle` interface to it and then proceeds to implement all methods from
that interface by generating the required bytecode.
It collects symbols referring to both internal and third party APIs used by the currently processed
module's type instrumentations (`InstrumentationModule#typeInstrumentations()`). The reference
collection process starts from advice classes (collected by calling the
`TypeInstrumentation#transform(TypeTransformer)` method) and traverses the class graph until it
encounters a reference to a non-instrumentation class (determined by `InstrumentationClassPredicate`
and the `InstrumentationModule#isHelperClass(String)` predicate). Aside from references, the
collection process also builds a graph of dependencies between internal instrumentation helper
classes - this dependency graph is later used to construct a list of helper classes that will be
injected to the application class loader (`InstrumentationModuleMuzzle#getMuzzleHelperClassNames()`).
Muzzle also automatically generates the `InstrumentationModuleMuzzle#registerMuzzleVirtualFields()`
method. All collected references are then used to generate
an `InstrumentationModuleMuzzle#getMuzzleReferences` method.

If your `InstrumentationModule` subclass defines a method with exact same signature as a method
from `InstrumentationModuleMuzzle`, the muzzle compile plugin will not override your code:
muzzle will only generate those methods that do not have a custom implementation.

The source code of the compile-time plugin is located in the `muzzle` module,
package `io.opentelemetry.javaagent.tooling.muzzle.generation`.

### Runtime reference matching

The runtime reference matching process is implemented as a ByteBuddy matcher in `InstrumentationModule`.
`MuzzleMatcher` uses the `InstrumentationModuleMuzzle` methods generated during the compilation phase
to verify that the class loader of the instrumented type has all necessary symbols (classes,
methods, fields). If this matcher finds any mismatch between collected references and the
actual application classpath types the whole instrumentation is discarded.

It is worth noting that because the muzzle check is expensive, it is only performed after a match
has been made by the `InstrumentationModule#classLoaderMatcher()` and `TypeInstrumentation#typeMatcher()`
matchers. The result of muzzle matcher is cached per class loader, so that it is only executed
once for the whole instrumentation module.

The source code of the runtime muzzle matcher is located in the `muzzle` module.

## `muzzle-check` gradle plugin

The [`muzzle-check`](https://plugins.gradle.org/plugin/io.opentelemetry.instrumentation.muzzle-check)
gradle plugin allows to perform the runtime reference matching process against different third party
library versions, when the project is built.

The `muzzle-check` gradle plugin is just an additional utility for enhanced build-time checking
to alert us when there are breaking changes in the underlying third party library
that will cause the instrumentation not to get applied.
**Even without using it muzzle reference matching is _always_ active in runtime**,
it's not an optional feature.

The gradle plugin defines two tasks:

- `muzzle` task runs the runtime muzzle verification against different library versions:

  ```sh
  ./gradlew :instrumentation:google-http-client-1.19:javaagent:muzzle
  ```

  If a new, incompatible version of the instrumented library is published it fails the build.

- `printMuzzleReferences` task prints all API references in a given module:

  ```sh
  ./gradlew :instrumentation:google-http-client-1.19:javaagent:printMuzzleReferences
  ```

The muzzle plugin needs to be configured in the module's `.gradle` file.
Example:

```groovy
muzzle {
  // it is expected that muzzle fails the runtime check for this component
  fail {
    group.set("commons-httpclient")
    module.set("commons-httpclient")
    // versions from this range are checked
    versions.set("[,4.0)")
    // this version is not checked by muzzle
    skip("3.1-jenkins-1")
  }
  // it is expected that muzzle passes the runtime check for this component
  pass {
    group.set("org.springframework")
    module.set("spring-webmvc")
    versions.set("[3.1.0.RELEASE,]")
    // except these versions
    skip("1.2.1", "1.2.2", "1.2.3", "1.2.4")
    skip("3.2.1.RELEASE")
    // this dependency will be added to the classpath when muzzle check is run
    extraDependency("javax.servlet:javax.servlet-api:3.0.1")
    // verify that all other versions - [,3.1.0.RELEASE) in this case - fail the muzzle runtime check
    assertInverse.set(true)
  }
}
```

- Using either `pass` or `fail` directive allows to specify whether muzzle should treat the
  reference check failure as expected behavior;
- `versions` is a version range, where `[]` is inclusive and `()` is exclusive. It is not needed to
  specify the exact version to start/end, e.g. `[1.0.0,4)` would usually behave in the same way as
  `[1.0.0,4.0.0-Alpha)`;
- `assertInverse` is basically a shortcut for adding an opposite directive for all library versions
  that are not included in the specified `versions` range;
- `extraDependency` allows putting additional libs on the classpath just for the compile-time check;
  this is usually used for jars that are not bundled with the instrumented lib but always present
  in the runtime anyway.

The source code of the gradle plugin is located in the `buildSrc` directory.

### Covering all versions and `assertInverse`

Ideally when using the muzzle gradle plugin we should aim to cover all versions of the instrumented
library. Expecting muzzle check failures from some library versions is a way to ensure that the
instrumentation will not be applied to them in the runtime - and won't break anything in the
instrumented application.

The easiest way it can be done is by adding `assertInverse.set(true)` to the `pass` muzzle
directive. The plugin will add an implicit `fail` directive that contains all other versions of the
instrumented library.
You SHOULD use `assertInverse.set(true)` when writing instrumentation modules, even for
very old library versions. The muzzle plugin will ensure that those old versions won't be
accidentally instrumented when we know that the instrumentation will not work properly for them.
Having a `fail` directive forces the authors of the instrumentation module to properly specify
`classLoaderMatcher()` so that only the desired version range is instrumented.

In more complicated scenarios it may be required to use multiple `pass` and `fail` directives
to cover as many versions as possible.
