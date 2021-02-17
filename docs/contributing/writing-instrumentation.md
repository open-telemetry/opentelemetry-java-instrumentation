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

Please also refer to some of our existing instrumentation for examples of our structure, for example,
[aws-sdk-2.2](../../instrumentation/aws-sdk/aws-sdk-2.2).

When writing new instrumentation, create a new subfolder of `instrumentation` to correspond to the
instrumented library and the oldest version being targeted. Ideally an old version of the library is
targeted in a way that the instrumentation applies to a large range of versions, but this may be
restricted by the interception APIs provided by the library.

Within the subfolder, create three folders `library` (skip if library instrumentation is not possible),
`javaagent`, and `testing`.

For example, if we are targeting an RPC framework `yarpc` at version `1.0` we would have a tree like

```
instrumentation ->
    ...
    yarpc-1.0 ->
        javaagent
            yarpc-1.0-javaagent.gradle
        library
            yarpc-1.0-library.gradle
        testing
            yarpc-1.0-testing.gradle
```

and in the top level `settings.gradle`

```groovy

include 'instrumentation:yarpc-1.0:javaagent'
include 'instrumentation:yarpc-1.0:library'
include 'instrumentation:yarpc-1.0:testing'
```

## Writing library instrumentation

Begin by writing the instrumentation for the library in `library`. This generally involves defining a
`Tracer` and using the typed tracers in our `instrumentation-common` library to create and annotate
spans as part of the implementation of an interceptor for the library. The module should generally
only depend on the OpenTelemetry API, `instrumentation-common`, and the instrumented library itself.
[instrumentation-library.gradle](../../gradle/instrumentation-library.gradle) needs to be applied to
configure build tooling for the library.

## Writing instrumentation tests

Once the instrumentation is completed, we add tests to the `testing` module. Tests will
generally apply to both library and agent instrumentation, with the only difference being how a client
or server is initialized. In a library test, there will be code calling into the instrumentation API,
while in an agent test, it will generally just use the underlying library's API as is. Create tests in an
abstract class with an abstract method that returns an instrumented object like a client. The class
should itself extend from `InstrumentationSpecification` to be recognized by Spock and include helper
methods for assertions.

After writing a test or two, go back to the `library` package, make sure it has a test dependency on the
`testing` submodule and add a test that inherits from the abstract test class. You should implement
the method to initialize the client using the library's mechanism to register interceptors, perhaps
a method like `registerInterceptor` or wrapping the result of a library factory when delegating. The
test should implement the `LibraryTestTrait` trait for common setup logic. If the tests pass,
library instrumentation is working OK.

## Writing Java agent instrumentation

Now that we have working instrumentation, we can implement agent instrumentation so users of the agent
do not have to modify their apps to use it. Make sure the `javaagent` submodule has a dependency on the
`library` submodule and a test dependency on the `testing` submodule. Agent instrumentation defines
classes to match against to generate bytecode for. You will often match against the class you used
in the test for library instrumentation, for example the builder of a client. And then you could
match against the method that creates the builder, for example its constructor. Agent instrumentation
can inject byte code to be run after the constructor returns, which would invoke e.g.,
`registerInterceptor` and initialize the instrumentation. Often, the code inside the byte code
decorator will be identical to the one in the test you wrote above - the agent does the work for
initializing the instrumentation library, so a user doesn't have to.

With that written, let's add tests for the agent instrumentation. We basically want to ensure that
the instrumentation works without the user knowing about the instrumentation. Add a test that extends
the base class you wrote earlier, but in this, create a client using none of the APIs in our project,
only the ones offered by the library. Implement the `AgentTestTrait` trait for common setup logic,
and try running. All the tests should pass for agent instrumentation too.

Note that all the tests inside the `javaagent` module will be run using the shaded `-javaagent`
in order to perform the same bytecode instrumentation as when the agent is run against a normal app.
This means that the javaagent instrumentation will be inside the javaagent (inside of the
`AgentClassLoader`) and will not be directly accessible to your test code. See the next section in
case you need to write unit tests that directly access the javaagent instrumentation.

## Writing Java agent unit tests

As mentioned above, tests in the `javaagent` module cannot access the javaagent instrumentation
classes directly.

Ideally javaagent instrumentation is just a thin wrapper over library instrumentation, and so there
is no need to write unit tests that directly access the javaagent instrumentation classes.

If you still want to write a unit test against javaagent instrumentation, add another module
named `javaagent-unittests`. Continuing with the example above:

```
instrumentation ->
    ...
    yarpc-1.0 ->
        javaagent
            yarpc-1.0-javaagent.gradle
        javaagent-unittest
            yarpc-1.0-javaagent-unittest.gradle
        ...
```

## Java agent instrumentation gotchas

### Calling Java 8 default methods from advice

If you are instrumenting a pre-Java 8 library, then inlining Java 8 default method calls into that
library will result in a `java.lang.VerifyError` at runtime, since Java 8 default method invocations
are not legal in Java 7 (and prior) bytecode.

Because OpenTelemetry API has many common default methods (e.g. `Span.current()`),
the `javaagent-api` artifact has a class `Java8BytecodeBridge` which provides static methods
for accessing these default methods from advice.

### Why hard code advice class names?

Implementations of `TypeInstrumentation` will often implement advice classes as static inner classes.
These classes are referred to by name in the mappings from method descriptor to advice class,
typically in the `transform()` method.

For instance, this `MyInstrumentationModule` defines a single advice that matches
on a single `execute` method:

```
transformers.put(
  isMethod().and(named("execute")),
  MyInstrumentationModule.class.getName() + "$WonderfulAdvice");
```

Simply referring to the inner class and
calling `getName()` would be easier to read and understand than
this odd mix of string concatenation...but please NOTE:  **this is intentional**
and should be maintained.

Instrumentation modules are loaded by the agent's classloader, and this
string concatenation is an optimization that prevents the actual advice class
from being loaded.

### Instrumenting code that is not available as a maven dependency

If instrumented server or library jar isn't available from a maven repository you can create a
module with stub classes that define only the methods that you need for writing the integration.
Methods in stub class can just `throw new UnsupportedOperationException()` these classes are only
used to compile the advice classes and won't be packaged into agent. During runtime real classes
from instrumented server or library will be used.

Create a module called `compile-stub` and add `compile-stub.gradle` with following content
```
apply from: "$rootDir/gradle/java.gradle"
```
In javaagent module add compile only dependency with
```
compileOnly project(':instrumentation:xxx:compile-stub')
```
