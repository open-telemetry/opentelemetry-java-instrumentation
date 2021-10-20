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

Please also refer to some of our existing instrumentation for examples of our structure, e.g.
[aws-sdk-2.2](../../instrumentation/aws-sdk/aws-sdk-2.2).

When writing new instrumentation, create a new subfolder of `instrumentation` to correspond to the
instrumented library and the oldest version being targeted. Ideally an old version of the library is
targeted in a way that the instrumentation applies to a large range of versions, but this may be
restricted by the interception APIs provided by the library.

Within the subfolder, create three folders `library` (skip if library instrumentation is not possible),
`javaagent`, and `testing`.

For example, if we are targeting an RPC framework `yarpc` at version `1.0` we would have a tree like
this one:

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

And the top level `settings.gradle.kts` file should contain the following:

```kotlin
include("instrumentation:yarpc-1.0:javaagent")
include("instrumentation:yarpc-1.0:library")
include("instrumentation:yarpc-1.0:testing")
```

## Writing library instrumentation

Let's start with the library instrumentation. Create the `build.gradle.kts` file in the `library`
directory:

```kotlin
plugins {
  id("otel.library-instrumentation")
}
```

The `otel.library-instrumentation` gradle plugin will apply all the default settings and configure
build tooling for the library instrumentation module.

Now let's move on to the actual Java code - by convention, our library instrumentations are centered
around `*Tracing` and `*TracingBuilder` classes. These two are usually the only public classes in
the whole module; we want to keep the number of public classes/methods as small as possible.

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
the configuration/construction logic happens in the builder class, and we don't want to expose any
other way of creating a new instance other than using the builder.

The `newTracingInterceptor()` method listed in the example code will return an implementation of one
of the library's interfaces that'll actually add the telemetry. This part will look different for
every instrumented library: some of them expose interceptor/listener interfaces that can be easily
plugged into the library, some of them have a library interface that we can use to implement a
decorator that emits telemetry when used.

Let's take a closer look at the builder class next:

```java
public final class YarpcTracingBuilder {

  YarpcTracingBuilder(OpenTelemetry openTelemetry) {}

  // ...

  public YarpcTracing build() {
    // ...
  }
}
```

The builder must have a package-private constructor (so that the only way of creating a new one is
calling the `YarpcTracing#builder()` method) and a public `build()` method that will return a new,
properly configured `YarpcTracing` instance.

Aside from that, the library instrumentation builders may contain configuration knobs that allow to
customize the behavior of the instrumentation. Most of these knobs are used to configure the
underlying `Instrumenter` instance that's used to encapsulate the whole telemetry generation
process.

The `Instrumenter` class, its configuration and usage, is a rather lengthy topic that's described in
a separate document that can be found [here](using-instrumenter-api.md). In general, the `build()`
method is supposed to create a fully configured `Instrumenter` instance and pass it along
to `YarpcTracing`, which in turn can pass it to the interceptor returned
by `newTracingInterceptor()`. The actual process of configuring an `Instrumenter` and various
interfaces involved are described in the [`Instrumenter` API doc](using-instrumenter-api.md).

## Writing instrumentation tests

Once the library instrumentation is completed we can add tests to the `testing` module. Let's start
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

In general, tests in the `testing` module describe scenarios that apply to both library and
javaagent instrumentations, with the only difference being how the instrumented library is
initialized. In a library instrumentation test, there will be code calling into the instrumentation
API, while in a javaagent instrumentation test it will generally just use the underlying library's
API as is and just rely on the javaagent to apply all the necessary bytecode changes automatically.

You can use either JUnit 5 (recommended) or Spock to test the instrumentation. In any case, start by
creating an abstract class with an abstract method (let's call it simply `configure()`) that returns
the instrumented object - like a client, server, the main class of the instrumented library. Then,
depending on the chosen test library, go to the [JUnit](#junit) or [Spock](#spock) section.

After writing a test or two, go back to the `library` package and make sure it has
a `testImplementation` dependency on the `testing` submodule. Then, create a test class that extends
the abstract test class from `testing`. You should implement the abstract `configure()` method to
initialize the library using the exposed mechanism to register interceptors/listeners, perhaps a
method like `registerInterceptor`; or wrap the object with the instrumentation decorator. Make sure
that proper testing strategy is applied (depends on used test library; but both of them expose a way
to specify whether you're running a library or javaagent test). If the tests pass, library
instrumentation is working OK.

### JUnit

The `testing-common` module exposes several JUnit extensions that facilitate writing instrumentation
tests. In particular, we'll take a look at `LibraryInstrumentationExtension`
, `AgentInstrumentationExtension` and their parent class `InstrumentationExtension`. The extension
class implements several useful methods (like `waitAndAssertTraces`, `waitAndAssertMetrics`) that
you can use in your test cases to verify that the correct telemetry has been produced.

Assuming that this is your abstract test case class:

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

Aside from the `configure()` method mention in earlier paragraphs, you'll have an
additional `testing()` method that returns an `InstrumentationExtension` and is supposed to be
implemented by the extending class.

Now, the library instrumentation class will look like that:

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

You can use the JUnit's `@RegisterExtension` annotation to make sure that the instrumentation
extension gets picked up by JUnit. Then, return the same extension instance in the `testing()`
method implementation so that it's used in all test scenarios implemented in the abstract class.

### Spock

The `testing-common` module contains a few utilities that make writing Spock instrumentation tests a
bit easier. In particular, we have the `InstrumentationSpecification` base class and
the `LibraryTestTrait` and `AgentTestTrait` traits.

Your abstract test class should extend the `InstrumentationSpecification`:

```groovy
abstract class AbstractYarpcTest extends InstrumentationSpecification {

  abstract Yarpc configure(Yarpc yarpc);

  def "test something"() {
    // ...
  }
}
```

The `InstrumentationSpecification` class contains abstract methods that will be implemented by one
of our test traits in the actual test class. For example:

```groovy
class LibraryYarpcTest extends AbstractYarpcTest implements LibraryTestTrait {

  @Override
  Yarpc configure(Yarpc yarpc) {
    // register interceptor/listener etc
  }
}
```

## Writing Java agent instrumentation

Now that we have working and tested library instrumentation, we can implement the javaagent
instrumentation so that the users of the agent do not have to modify their apps to enable telemetry
for the library.

As usual, let's start with the gradle file - we need to make sure that the `javaagent` submodule has
a dependency on the `library` submodule and a test dependency on the `testing` submodule.

```kotlin
plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:yarpc-1.0:library"))

  testImplementation(project(":instrumentation:yarpc-1.0:testing"))
}
```

All javaagent instrumentation modules should also have the muzzle plugins configured - you can read
more about how to set this up properly in the [muzzle docs](muzzle.md#muzzle-check-gradle-plugin).

Javaagent instrumentation defines classes to match against to generate bytecode for. You will often
match against the class you used in the test for library instrumentation, for example the builder of
a client. And then you could match against the method that creates the builder, for example its
constructor. Agent instrumentation can inject byte code to be run after the constructor returns,
which would invoke e.g., `registerInterceptor` and initialize the instrumentation. Often, the code
inside the byte code decorator will be identical to the one in the test you wrote above - the agent
does the work for initializing the instrumentation library, so a user doesn't have to. You can find
a detailed explanation of how to implement a javaagent instrumentation
[here](writing-instrumentation-module.md).

With that written, let's add tests for the agent instrumentation. We basically want to ensure that
the instrumentation works without the user knowing about the instrumentation.

Create a test that extends the base class you wrote earlier, but do nothing in the `configure()`
method - unlike the library instrumentation, the javaagent instrumentation is supposed to work
without any explicit user code modification. Depending on the testing framework used, either use
the `AgentInstrumentationExtension` or implement the `AgentTestingTrait`, and try running tests in
this class. All tests should pass.

Note that all the tests inside the `javaagent` module will be run using the `agent-for-testing`
javaagent, with the instrumentation being loaded as an extension. This is done in order to perform
the same bytecode instrumentation as when the agent is run against a normal app. This means that the
javaagent instrumentation will be hidden inside the javaagent (loaded by the `AgentClassLoader`) and
will not be directly accessible in your test code. Please take care not to use the classes from the
javaagent instrumentation in your test code. If for some reason you need to write unit tests for the
javaagent code, please take a look at [this section](#writing-java-agent-unit-tests).

## Various instrumentation gotchas

### Instrumenting code that is not available as a maven dependency

If instrumented server or library jar isn't available in any public maven repository you can create
a module with stub classes that'll define only the methods that you need to write the
instrumentation. Methods in these stub classes can just `throw new UnsupportedOperationException()`;
these classes are only used to compile the advice classes and won't be packaged into the agent.
During runtime real classes from instrumented server or library will be used.

First, create a module called `compile-stub` and add a `build.gradle.kts` file with the following
content:

```kotlin
plugins {
  id("otel.java-conventions")
}
```

Now, in the `javaagent` module add a `compileOnly` dependency to the newly created stub module:

```kotlin
compileOnly(project(":instrumentation:xxx:compile-stub"))
```

Now you can use your stub classes inside the javaagent instrumentation.

## Writing Java agent unit tests

As mentioned before, tests in the `javaagent` module cannot access the javaagent instrumentation
classes directly because of classloader reasons.

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
