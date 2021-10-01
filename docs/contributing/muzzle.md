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
* at compile time it collects references to the third-party symbols and used helper classes;
* at runtime it compares those references to the actual API symbols on the classpath.

### Compile-time reference collection

The compile-time reference collection and code generation process is implemented using a ByteBuddy
plugin (called `MuzzleCodeGenerationPlugin`).

For each instrumentation module the ByteBuddy plugin collects symbols referring to both internal and
third party APIs used by the currently processed module's type
instrumentations (`InstrumentationModule#typeInstrumentations()`). The reference collection process
starts from advice classes (values of the map returned by the
`TypeInstrumentation#transformers()` method) and traverses the class graph until it encounters a
reference to a non-instrumentation class (determined by `InstrumentationClassPredicate` and
the `InstrumentationModule#isHelperClass(String)` predicate). Aside from references,
the collection process also builds a graph of dependencies between internal instrumentation helper
classes - this dependency graph is later used to construct a list of helper classes that will be
injected to the application classloader (`InstrumentationModule#getMuzzleHelperClassNames()`).
Muzzle also automatically generates the `InstrumentationModule#registerMuzzleVirtualFields()`
method.

If you extend any of these `getMuzzle...()` methods in your `InstrumentationModule`, the muzzle
compile plugin will not override your code: muzzle will only override those methods that do not have
a custom implementation.

All collected references are then used to create a `ReferenceMatcher` instance. This matcher
is stored in the instrumentation module class in the method `InstrumentationModule#getMuzzleReferenceMatcher()`
and is shared between all type instrumentations. The bytecode of this method (basically an array of
`Reference` builder calls) and the `getMuzzleHelperClassNames()` is generated automatically by the
ByteBuddy plugin using an ASM code visitor.

The source code of the compile-time plugin is located in the `javaagent-tooling` module,
package `io.opentelemetry.javaagent.muzzle.generation.collector`.

### Runtime reference matching

The runtime reference matching process is implemented as a ByteBuddy matcher in `InstrumentationModule`.
`MuzzleMatcher` uses the `getMuzzleReferenceMatcher()` method generated during the compilation phase
to verify that the class loader of the instrumented type has all necessary symbols (classes,
methods, fields). If the `ReferenceMatcher` finds any mismatch between collected references and the
actual application classpath types the whole instrumentation is discarded.

It is worth noting that because the muzzle check is expensive, it is only performed after a match
has been made by the `InstrumentationModule#classLoaderMatcher()` and `TypeInstrumentation#typeMatcher()`
matchers. The result of muzzle matcher is cached per classloader, so that it is only executed
once for the whole instrumentation module.

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
    ./gradlew :instrumentation:google-http-client-1.19:javaagent:muzzle
    ```
    If a new, incompatible version of the instrumented library is published it fails the build.

* `printMuzzleReferences` task prints all API references in a given module:
    ```sh
    ./gradlew :instrumentation:google-http-client-1.19:javaagent:printMuzzleReferences
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
    skip('3.1-jenkins-1')
  }
  // it is expected that muzzle passes the runtime check for this component
  pass {
    group = 'org.springframework'
    module = 'spring-webmvc'
    versions = "[3.1.0.RELEASE,]"
    // except these versions
    skip('1.2.1', '1.2.2', '1.2.3', '1.2.4')
    skip('3.2.1.RELEASE')
    // this dependency will be added to the classpath when muzzle check is run
    extraDependency "javax.servlet:javax.servlet-api:3.0.1"
    // verify that all other versions - [,3.1.0.RELEASE) in this case - fail the muzzle runtime check
    assertInverse = true
  }
}
```

* Using either `pass` or `fail` directive allows to specify whether muzzle should treat the
  reference check failure as expected behavior;
* `versions` is a version range, where `[]` is inclusive and `()` is exclusive. It is not needed to
  specify the exact version to start/end, e.g. `[1.0.0,4)` would usually behave in the same way as
  `[1.0.0,4.0.0-Alpha)`;
* `assertInverse` is basically a shortcut for adding an opposite directive for all library versions
  that are not included in the specified `versions` range;
* `extraDependency` allows putting additional libs on the classpath just for the compile-time check;
  this is usually used for jars that are not bundled with the instrumented lib but always present
  in the runtime anyway.

The source code of the gradle plugin is located in the `buildSrc` directory.

### Covering all versions and `assertInverse`

Ideally when using the muzzle gradle plugin we should aim to cover all versions of the instrumented
library. Expecting muzzle check failures from some library versions is a way to ensure that the
instrumentation will not be applied to them in the runtime - and won't break anything in the
instrumented application.

The easiest way it can be done is by adding `assertInverse = true` to the `pass` muzzle
directive. The plugin will add an implicit `fail` directive that contains all other versions of the
instrumented library.
It is worth using `assertInverse = true` by default when writing instrumentation modules, even for
very old library versions. The muzzle plugin will ensure that those old versions won't be
accidentally instrumented when we know that the instrumentation will not work properly for them.
Having a `fail` directive forces the authors of the instrumentation module to properly specify
`classLoaderMatcher()` so that only the desired version range is instrumented.

In more complicated scenarios it may be required to use multiple `pass` and `fail` directives
to cover as many versions as possible.
