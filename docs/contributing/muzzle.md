# Muzzle

Muzzle is a safety feature of the Java agent that prevents applying instrumentation when a mismatch
between the instrumentation code and the instrumented application code is detected.
It ensures API compatibility between symbols (classes, methods, fields) on the application classpath
and references to those symbols made by instrumentation advices defined in the agent.
In other words, muzzle ensures that the API symbols used by the agent are compatible with the API
symbols on the application classpath.

Muzzle will prevent loading an instrumentation if it detects any mismatch or conflict.

## How it works

Muzzle has two phases:
* at compile time it collects references to the third-party symbols;
* at runtime it compares those references to the actual API symbols on the classpath.

### Compile-time reference collection

The compile-time reference collection and code generation process is implemented using a ByteBuddy
plugin (called `MuzzleCodeGenerationPlugin`).

For each instrumentation the ByteBuddy plugin collects symbols referring to both internal and third
party APIs used by the currently processed instrumentation. The reference collection process starts
from advice classes (values of the map returned by the `Instrumenter.Default#transformers()` method)
and traverses the class graph until it encounters a reference to a non-instrumentation class.

All collected references are then used to create a `ReferenceMatcher` instance. This matcher
is stored in the instrumentation class in the method `Instrumenter.Default#getMuzzleReferenceMatcher()`.
The bytecode of this method (basically an array of `Reference` builder calls) is generated
automatically by the ByteBuddy plugin using an ASM code visitor.

The source code of the compile-time plugin is located in the `javaagent-tooling` module,
package `io.opentelemetry.javaagent.tooling.muzzle.collector`.

### Runtime reference matching

The runtime reference matching process is implemented as a ByteBuddy matcher in `Instrumenter.Default`.
`MuzzleMatcher` uses the `getMuzzleReferenceMatcher()` method generated during the compilation phase
to verify that the class loader of the instrumented type has all necessary symbols (classes,
methods, fields). If the `ReferenceMatcher` finds any mismatch between collected references and the
actual application classpath types the whole instrumentation is discarded.

It is worth noting that because the muzzle check is expensive, it is only performed after a match
has been made by the `InstrumenterDefault#classLoaderMatcher()` and `Instrumenter.Default#typeMatcher()`
matchers.

The source code of the runtime muzzle matcher is located in the `javaagent-tooling` module,
in the class `Instrumenter.Default` and under the package `io.opentelemetry.javaagent.tooling.muzzle`.

## Muzzle gradle plugin

The muzzle gradle plugin allows to perform the runtime reference matching process against different
third party library versions, when the project is built.

Muzzle gradle plugin is just an additional utility for enhanced build-time checking
to alert us when there are breaking changes in the underlying third party library
that will cause the instrumentation not to get applied.
**Even without using it muzzle reference matching is _always_ active in runtime**,
it's not an optional feature.

The gradle plugin defines two tasks:

* `muzzle` task runs the runtime muzzle verification against different library versions:
    ```sh
    ./gradlew :instrumentation:google-http-client-1.19:muzzle
    ```
    If a new, incompatible version of the instrumented library is published it fails the build.

* `printReferences` task prints all API references in a given module:
    ```sh
    ./gradlew :instrumentation:google-http-client-1.19:printReferences
    ```

The muzzle plugin needs to be configured in the module's `.gradle` file.
Example:

```groovy
muzzle {
  // it is expected that muzzle fails the runtime check for this component
  fail {
    group = "commons-httpclient"
    module = "commons-httpclient"
    // versions from this range are checked
    versions = "[,4.0)"
    // this version is not checked by muzzle
    skipVersions += '3.1-jenkins-1'
  }
  // it is expected that muzzle passes the runtime check for this component
  pass {
    group = "org.apache.httpcomponents"
    module = "httpclient"
    versions = "[4.0,)"
    // verify that all other versions - [,4.0) in this case - fail the muzzle runtime check
    assertInverse = true
  }
  // ...
}
```

* Using either `pass` or `fail` directive allows to specify whether muzzle should treat the
  reference check failure as expected behavior;
* `versions` is a version range, where `[]` is inclusive and `()` is exclusive. It is not needed to
  specify the exact version to start/end, e.g. `[1.0.0,4)` would usually behave in the same way as
  `[1.0.0,4.0.0-Alpha)`;
* `assertInverse` is basically a shortcut for adding an opposite directive for all library versions
  that are not included in the specified `versions` range.

The source code of the gradle plugin is located in the `buildSrc` directory.
