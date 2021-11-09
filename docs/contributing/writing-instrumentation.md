# Writing instrumentation

**Warning**: The repository is still in the process of migrating to the structure described here.

Any time we want to add OpenTelemetry support for a new Java library, e.g., so usage
of that library has tracing, we must write new instrumentation for that library. Let's
go over some terms first.

**Library instrumentation**: This is logic that creates spans and enriches them with data
using library-specific monitoring APIs. For example, when instrumenting an RPC library,
the instrumentation will use some library-specific functionality to listen to events such
as the start and end of a request and will execute code to start and end spans in these
listeners. Many of these libraries will provide interception type APIs such as the gRPC
`ClientInterceptor` or servlet's `Filter`. Others will provide a Java interface whose methods
correspond to a request, and instrumentation can define an implementation which delegates
to the standard, wrapping methods with the logic to manage spans. Users will add code to their
apps that initialize the classes provided by library instrumentation, and the library instrumentation
can be found inside the user's app itself.

Some libraries will have no way of intercepting requests because they only expose static APIs
and no interception hooks. For these libraries it is not possible to create library
instrumentation.

**Java agent instrumentation**: This is logic that is similar to library instrumentation, but instead
of a user initializing classes themselves, a Java agent automatically initializes them during
class loading by manipulating byte code. This allows a user to develop their apps without thinking
about instrumentation and get it "for free". Often, the agent instrumentation will generate
bytecode that is more or less identical to what a user would have written themselves in their app.

In addition to automatically initializing library instrumentation, agent instrumentation can be used
for libraries where library instrumentation is not possible, such as `URLConnection`, because it can
intercept even the JDK's classes. Such libraries will not have library instrumentation but will have
agent instrumentation.

## Folder Structure

Refer to some of our existing instrumentations for examples of the folder structure, for example:
[aws-sdk-2.2](../../instrumentation/aws-sdk/aws-sdk-2.2).

When writing new instrumentation, create a directory inside `instrumentation` that corresponds to
the instrumented library and the oldest version being targeted. Ideally an old version of the
library is targeted in a way that the instrumentation applies to a large range of versions, but this
may be restricted by the interception APIs provided by the library.

Within the subfolder, create three folders `library` (skip if library instrumentation is not
possible),`javaagent`, and `testing`.

For example, if you are targeting the RPC framework `yarpc` at version `1.0`, you would have a
directory tree like the following:

```
instrumentation ->
    ...
    yarpc-1.0 ->
        javaagent
            build.gradle.kts
        library
            build.gradle.kts
        testing
            build.gradle.kts
```

The top level `settings.gradle.kts` file would contain the following:

```kotlin
include("instrumentation:yarpc-1.0:javaagent")
include("instrumentation:yarpc-1.0:library")
include("instrumentation:yarpc-1.0:testing")
```

## Writing library instrumentation

Start by creating the `build.gradle.kts` file in the `library`
directory:

```kotlin
plugins {
  id("otel.library-instrumentation")
}
```

The `otel.library-instrumentation` gradle plugin will apply all the default settings and configure
build tooling for the library instrumentation module.

By convention, OpenTelemetry library instrumentations are centered around `*Tracing`
and `*TracingBuilder` classes. These two are usually the only public classes in the whole module.
Keep the amount of public classes and methods as small as possible.

Start by creating a `YarpcTracing` class:

```java
public final class YarpcTracing {

  public static YarpcTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static YarpcTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new YarpcTracingBuilder(openTelemetry);
  }

  // ...

  YarpcTracing() {}

  public Interceptor newTracingInterceptor() {
    // ...
  }
}
```

By convention, the `YarpcTracing` class exposes the `create()` and `builder()` methods as the only
way of constructing a new instance; the constructor must be kept package-private (at most). Most of
the configuration/construction logic happens in the builder class. Don't expose any other way of
creating a new instance other than using the builder.

The `newTracingInterceptor()` method listed in the example code returns an implementation of one of
the library interfaces which adds the telemetry. This part might look different for every
instrumented library: some of them expose interceptor/listener interfaces that can be easily plugged
into the library, some others have a library interface that you can use to implement a decorator that
emits telemetry when used.

Consider the following builder class:

```java
public final class YarpcTracingBuilder {

  YarpcTracingBuilder(OpenTelemetry openTelemetry) {}

  // ...

  public YarpcTracing build() {
    // ...
  }
}
```

The builder must have a package-private constructor, so that the only way of creating a new one is
calling the `YarpcTracing#builder()` method and a public `build()` method that will return a new,
properly configured `YarpcTracing` instance.

The library instrumentation builders can contain configuration settings that let you customize the
behavior of the instrumentation. Most of these options are used to configure the
underlying `Instrumenter` instance that's used to encapsulate the whole telemetry generation
process.

The configuration and usage of the `Instrumenter` class is described in
[a separate document](using-instrumenter-api.md). In most cases, the `build()`
method is supposed to create a fully configured `Instrumenter` instance and pass it
to `YarpcTracing`, which in turn can pass it to the interceptor returned
by `newTracingInterceptor()`. The actual process of configuring an `Instrumenter` and various
interfaces involved are described in the [`Instrumenter` API doc](using-instrumenter-api.md).

## Writing instrumentation tests

Once the library instrumentation is completed, add tests to the `testing` module. Start
by setting up the `build.gradle.kts` file:

```kotlin
plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  // ...
}
```

Tests in the `testing` module describe scenarios that apply to both library and javaagent
instrumentations, the only difference being how the instrumented library is initialized. In a
library instrumentation test, there will be code calling into the instrumentation API, while in a
javaagent instrumentation test it will generally use the underlying library API as is and just rely
on the javaagent to apply all the necessary bytecode changes automatically.

You can use either JUnit 5 (recommended) or Spock to test the instrumentation. Start by creating an
abstract class with an abstract method, for example `configure()`, that returns the instrumented
object, such as a client, server, or the main class of the instrumented library. Then, depending on
the chosen test library, go to the [JUnit](#junit) or [Spock](#spock) section.

After writing some tests, return to the `library` package and make sure it has
a `testImplementation` dependency on the `testing` submodule. Then, create a test class that extends
the abstract test class from `testing`. You should implement the abstract `configure()` method to
initialize the library using the exposed mechanism to register interceptors/listeners, perhaps a
method like `registerInterceptor`. You can also wrap the object with the instrumentation decorator.
Make sure that the test class is marked as a library instrumentation test. Both JUnit and Spock test
utilities expose a way to specify whether you're running a library or javaagent test. If the tests
pass, the library instrumentation is working.

### JUnit

The `testing-common` module exposes several JUnit extensions that facilitate writing instrumentation
tests. In particular, we'll take a look at `LibraryInstrumentationExtension`
, `AgentInstrumentationExtension`, and their parent class `InstrumentationExtension`. The extension
class implements several useful methods, such as `waitAndAssertTraces` and `waitAndAssertMetrics`,
that you can use in your test cases to verify that the correct telemetry has been produced.

Consider the following abstract test case class:

```java
public abstract class AbstractYarpcTest {

  protected abstract InstrumentationExtension testing();

  protected abstract Yarpc configure(Yarpc yarpc);

  @Test
  void testSomething() {
    // ...
  }
}
```

In addition to the `configure()` method mentioned earlier, you have to add an additional `testing()`
method that returns an `InstrumentationExtension` and is supposed to be implemented by the extending
class.

The library instrumentation class would look like the following:

```java
class LibraryYarpcTest extends AbstractYarpcTest {

  @RegisterExtension
  InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Yarpc configure(Yarpc yarpc) {
    // register interceptor/listener etc
  }
}
```

You can use the `@RegisterExtension` annotation to make sure that the instrumentation extension gets
picked up by JUnit. Then, return the same extension instance in the `testing()` method
implementation so that it's used in all test scenarios implemented in the abstract class.

### Spock

The `testing-common` module contains some utilities that facilitate writing Spock instrumentation
tests, such as the `InstrumentationSpecification` base class and the `LibraryTestTrait`
and `AgentTestTrait` traits.

Consider the following abstract test class extending `InstrumentationSpecification`:

```groovy
abstract class AbstractYarpcTest extends InstrumentationSpecification {

  abstract Yarpc configure(Yarpc yarpc);

  def "test something"() {
    // ...
  }
}
```

The `InstrumentationSpecification` class contains abstract methods that are implemented by one of
our test traits in the actual test class. For example:

```groovy
class LibraryYarpcTest extends AbstractYarpcTest implements LibraryTestTrait {

  @Override
  Yarpc configure(Yarpc yarpc) {
    // register interceptor/listener etc
  }
}
```

## Writing Java agent instrumentation

Now that you have working and tested library instrumentation, implement the javaagent
instrumentation so that the users of the agent do not have to modify their apps to enable telemetry
for the library.

Start with the gradle file to make sure that the `javaagent` submodule has a dependency on
the `library` submodule and a test dependency on the `testing` submodule.

```kotlin
plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:yarpc-1.0:library"))

  testImplementation(project(":instrumentation:yarpc-1.0:testing"))
}
```

All javaagent instrumentation modules should also have the muzzle plugins configured. You can read
more about how to set this up properly in the [muzzle docs](muzzle.md#muzzle-check-gradle-plugin).

Javaagent instrumentation defines matching classes for which bytecode is generated. You often match
against the class you used in the test for library instrumentation, for example the builder of a
client. You can also match against the method that creates the builder, for example its constructor.
Agent instrumentation can inject bytecode to be run before the constructor returns, which would
invoke, for example,`registerInterceptor` and initialize the instrumentation. Often, the code inside
the bytecode decorator is identical to the one in the test you wrote above, because the agent does
the work for initializing the instrumentation library, so a user doesn't have to. You can find a
detailed explanation of how to implement a javaagent instrumentation
[here](writing-instrumentation-module.md).

Next, add tests for the agent instrumentation. You want to ensure that the instrumentation works
without the user knowing about the instrumentation.

Create a test that extends the base class you wrote earlier but does nothing in the `configure()`
method. Unlike the library instrumentation, the javaagent instrumentation is supposed to work
without any explicit user code modification. Depending on the testing framework, either use
the `AgentInstrumentationExtension` or implement the `AgentTestingTrait`, and try running tests in
this class. All tests should pass.

Note that all the tests inside the `javaagent` module are run using the `agent-for-testing`
javaagent, with the instrumentation being loaded as an extension. This is done to perform the same
bytecode instrumentation as when the agent is run against a normal app, and means that the javaagent
instrumentation will be hidden inside the javaagent (loaded by the `AgentClassLoader`) and will not
be directly accessible in your test code. Make sure not to use the classes from the javaagent
instrumentation in your test code. If for some reason you need to write unit tests for the javaagent
code, see [this section](#writing-java-agent-unit-tests).

## Additional considerations regarding instrumentations

### Instrumenting code that is not available as a Maven dependency

If an instrumented server or library jar isn't available in any public Maven repository you can
create a module with stub classes that defines only the methods that you need to write the
instrumentation. Methods in these stub classes can just `throw new UnsupportedOperationException()`;
these classes are only used to compile the advice classes and won't be packaged into the agent.
During runtime, real classes from instrumented server or library will be used.

Start by creating a module called `compile-stub` and add a `build.gradle.kts` file with the
following content:

```kotlin
plugins {
  id("otel.java-conventions")
}
```

In the `javaagent` module add a `compileOnly` dependency to the newly created stub module:

```kotlin
compileOnly(project(":instrumentation:yarpc-1.0:compile-stub"))
```

Now you can use your stub classes inside the javaagent instrumentation.

## Writing Java agent unit tests

As mentioned before, tests in the `javaagent` module cannot access the javaagent instrumentation
classes directly because of classloader separation - the javaagent classes are hidden and not
accessible from the instrumented application code.

Ideally javaagent instrumentation is just a thin wrapper over library instrumentation, and so there
is no need to write unit tests that directly access the javaagent instrumentation classes.

If you still want to write a unit test against javaagent instrumentation, add another module
named `javaagent-unit-tests`. Continuing with the example above:

```
instrumentation ->
    ...
    yarpc-1.0 ->
        javaagent
            build.gradle.kts
        javaagent-unit-tests
            build.gradle.kts
        ...
```

Set up the unit tests project as a standard Java project:

```kotlin
plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:yarpc-1.0:javaagent"))
}
```

## Why we don't use ByteBuddy @Advice.Origin Method

Instead of
```
@Advice.Origin Method method
```
we prefer to use
```
@Advice.Origin("#t") Class<?> declaringClass,
@Advice.Origin("#m") String methodName
```
because the former inserts a call to `Class.getMethod(...)` in transformed class. In contrast,
getting the declaring class and method name is just loading constants from constant pool, which is
a much simpler operation.
