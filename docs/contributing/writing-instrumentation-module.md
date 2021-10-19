# Writing an `InstrumentationModule` step by step

`InstrumentationModule` is the central piece of any OpenTelemetry javaagent instrumentation. There
are many conventions that our javaagent uses, many pitfalls and not obvious patterns that one has to
follow when implementing a module.

This doc attempts to describe how a javaagent instrumentation should be implemented and document all
quirks that may affect your instrumentation. In addition to this file, we suggest reading
the `InstrumentationModule` and `TypeInstrumentation` Javadocs, as they often provide more detailed
explanations of how to use a particular method (and why it works the way it does).

## `InstrumentationModule`

An `InstrumentationModule` describes a set of individual `TypeInstrumentation`s that need to be
applied together to correctly instrument a particular library. Type instrumentations grouped in a
module share helper classes, [muzzle runtime checks](muzzle.md), have the same classloader criteria
for being applied, and get enabled/disabled together.

The OpenTelemetry javaagent finds all modules by using the Java `ServiceLoader` API. To make your
instrumentation visible you need to make sure that a proper `META-INF/services/` file is present in
the javaagent jar. The easiest way to do it is using `@AutoService`:

```java

@AutoService(InstrumentationModule.class)
class MyLibraryInstrumentationModule extends InstrumentationModule {
  // ...
}
```

An `InstrumentationModule` needs to have at least one name. The user of the javaagent can
[suppress a chosen instrumentation](../suppressing-instrumentation.md) by referring to it by one of
its names. The instrumentation module names use kebab-case. The main instrumentation name (the first
one) is supposed to be the same as the gradle module name (excluding the version suffix if it has
one).

```java
public MyLibraryInstrumentationModule() {
  super("my-library", "my-library-1.0");
}
```

For detailed information on `InstrumentationModule` names please read the
`InstrumentationModule#InstrumentationModule(String, String...)` Javadoc.

### `order()`

If you need to have instrumentations applied in a specific order (for example your custom
instrumentation enriches the built-in servlet one and thus needs to run after it) you can override
the `order()` method to specify the ordering:

```java
@Override
public int order() {
  return 1;
}
```

Higher `order()` means that the instrumentation module will be applied later. The default value is
`0`.

### `isHelperClass()`

The OpenTelemetry javaagent picks up helper classes used in the instrumentation/advice classes and
injects them into the application classpath. It can automatically find those classes that follow our
package conventions (see [muzzle docs](muzzle.md#compile-time-reference-collection) for more info on
this topic), but it is also possible to explicitly tell which packages/classes are supposed to be
treated as helper classes by implementing `isHelperClass(String)`:

```java
@Override
public boolean isHelperClass(String className) {
  return className.startsWith("org.my.library.opentelemetry");
}
```

### `helperResourceNames()`

Some libraries may expose SPI interfaces that you can easily implement to provide
telemetry-gathering capabilities. The OpenTelemetry javaagent is able to inject `ServiceLoader`
service provider files, but it needs to be told which ones explicitly:

```java
@Override
public List<String> helperResourceNames() {
  return singletonList("META-INF/services/org.my.library.SpiClass");
}
```

All classes referenced by service providers defined in the `helperResourceNames()` method will be
treated as helper classes: they'll be checked for invalid references and automatically injected into
the application classloader.

### `getAdditionalHelperClassNames()`

If you don't use the [muzzle gradle plugins](muzzle.md), or have a specific scenario that requires
providing the helper classes by hand (e.g. an unusual SPI implementation), you can override
the `getAdditionalHelperClassNames()` method to provide a list of additional helper classes that
should be injected to the application classloader when the instrumentation is applied.

```java
public List<String> getAdditionalHelperClassNames() {
  return Arrays.asList(
      "org.my.library.instrumentation.SomeHelper",
      "org.my.library.instrumentation.AnotherHelper");
}
```

The order of the class names returned by this method matters - if you have several helper classes
extending one another then you'll want to return the base class first. For example, if you have a
`B extends A` class the list should contain `A` first and `B` second.

These helper classes will be injected into the application classloader after those provided by the
muzzle codegen plugin.

### `classLoaderMatcher()`

Different versions of the same library often need completely different instrumentations:
for example, servlet 3 introduces several new async classes that need to be instrumented to produce
correct telemetry data. An `InstrumentationModule` can define additional criteria for checking
whether an instrumentation should be applied:

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  return hasClassesNamed("org.my.library.Version2Class");
}
```

The above example will skip instrumenting the application code if it does not contain the class that
was introduced in the version your instrumentation covers.

### `typeInstrumentations()`

Finally, an `InstrumentationModule` implementation needs to provide at least one
`TypeInstrumentation` implementation:

```java
@Override
public List<TypeInstrumentation> typeInstrumentations() {
  return Collections.singletonList(new MyTypeInstrumentation());
}
```

A module with no type instrumentations does nothing.

## `TypeInstrumentation`

A `TypeInstrumentation` describe the changes that need to be made to a single type. Depending on the
instrumented library, they may only make sense in conjunction with other type instrumentations
(grouped together in a module).

```java
class MyTypeInstrumentation implements TypeInstrumentation {
  // ...
}
```

### `typeMatcher()`

A type instrumentation needs to declare what class (or classes) are going to be instrumented:

```java
@Override
public ElementMatcher<TypeDescription> typeMatcher() {
  return named("org.my.library.SomeClass");
}
```

### `classLoaderOptimization()`

When you need to instrument all classes that implement a particular interface, or all classes that
are annotated with a particular annotation you should also implement the
`classLoaderOptimization()` method. Matching classes by their name is quite fast, but actually
inspecting the bytecode (e.g. implements, has annotation, has method) is a rather expensive
operation. The matcher returned by the `classLoaderOptimization()` method makes the
`TypeInstrumentation` significantly faster when instrumenting applications that do not contain the
library.

```java
@Override
public ElementMatcher<ClassLoader> classLoaderOptimization() {
  return hasClassesNamed("org.my.library.SomeInterface");
}

@Override
public ElementMatcher<? super TypeDescription> typeMatcher() {
  return implementsInterface(named("org.my.library.SomeInterface"));
}
```

### `transform(TypeTransformer)`

The last `TypeInstrumentation` method describes what transformations should be applied to the
matched type. Type `TypeTransformer` interface (implemented internally by the agent) defines a set
of available transformations that you can apply:

* Calling `applyAdviceToMethod(ElementMatcher<? super MethodDescription>, String)` allows you to
  apply an advice class (the second parameter) to all matching methods (the first parameter). It is
  suggested to make the method matchers as strict as possible - the type instrumentation should only
  instrument the code that it's supposed to, not more.
* `applyTransformer(AgentBuilder.Transformer)` allows you to inject an arbitrary ByteBuddy
  transformer. This is an advanced, low-level option that will not be subjected to muzzle safety
  checks and helper class detection - use it responsibly.

```java
@Override
public void transform(TypeTransformer transformer) {
  transformer.applyAdviceToMethod(
    isPublic()
        .and(named("someMethod"))
        .and(takesArguments(2))
        .and(takesArgument(0, String.class))
        .and(takesArgument(1, named("org.my.library.MyLibraryClass"))),
    this.getClass().getName() + "$MethodAdvice");
}
```

For matching built-in Java types you can use the `takesArgument(0, String.class)` form. Classes
originating from the instrumented library need to be matched using the `named()` matcher.

Implementations of `TypeInstrumentation` will often implement advice classes as static inner
classes. These classes are referred to by name when applying advice classes to methods in
the `transform()` method.

You probably noticed in the example above that the advice class is being referenced in a slightly
peculiar way:

```java
this.getClass().getName() + "$MethodAdvice"
```

Simply referring to the inner class and calling `getName()` would be easier to read and understand
than this odd mix of string concatenation, but please note that **this is intentional**
and should be maintained.

Instrumentation modules are loaded by the agent's class loader, and this string concatenation is an
optimization that prevents the actual advice class from being loaded into the agent's class loader.

## Advice classes

Advice classes are not really "classes", they're raw pieces of code that will be pasted directly into
the instrumented library class files. You should not treat them as ordinary, plain Java classes -
unfortunately many standard practices do not apply to them:

* if they're inner classes they MUST be static;
* they MUST only contain static methods;
* they MUST NOT contain any state (fields) whatsoever - static constants included! Only the advice
  methods' content is copied to the instrumented code, the constants are not;
* inner advice classes defined in an `InstrumentationModule` or a `TypeInstrumentation` MUST NOT use
  anything from the outer class (loggers, constants, etc);
* reusing code by extracting a common method and/or parent class will most likely not work properly:
  instead you can create additional helper classes to store any reusable code;
* they SHOULD NOT contain any methods other than `@Advice`-annotated method.

```java
@SuppressWarnings("unused")
public static class MethodAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(/* ... */) {
    // ...
  }

  @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
  public static void onExit(/* ... */) {
    // ...
  }
}
```

It is important to include the `suppress = Throwable.class` property in `@Advice`-annotated methods.
Exceptions thrown by the advice methods will get caught and handled by a special `ExceptionHandler`
that OpenTelemetry javaagent defines. The handler makes sure to properly log all unexpected
exceptions.

The `OnMethodEnter` and `OnMethodExit` advice methods often need to share several pieces of
information. We use local variables prefixed with `otel` to pass context, scope (and sometimes more)
between those methods.

```java
@Advice.OnMethodEnter(suppress = Throwable.class)
public static void onEnter(@Advice.Argument(1) Object request,
                           @Advice.Local("otelContext") Context context,
                           @Advice.Local("otelScope") Scope scope) {
  // ...
}
```

Usually, for telemetry-producing instrumentations those two methods follow the pattern below:

```java
@Advice.OnMethodEnter(suppress = Throwable.class)
public static void onEnter(@Advice.Argument(1) Object request,
                           @Advice.Local("otelContext") Context context,
                           @Advice.Local("otelScope") Scope scope) {
  Context parentContext = Java8BytecodeBridge.currentContext();

  if (!instrumenter().shouldStart(parentContext, request)) {
    return;
  }

  context = instrumenter().start(parentContext, request);
  scope = context.makeCurrent();
}

@Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
public static void onExit(@Advice.Argument(1) Object request,
                          @Advice.Return Object response,
                          @Advice.Thrown Throwable exception,
                          @Advice.Local("otelContext") Context context,
                          @Advice.Local("otelScope") Scope scope) {
  if (scope == null) {
    return;
  }
  scope.close();
  instrumenter().end(context, request, response, exception);
}
```

You may have noticed that the example above does not use `Context.current()`, but a
`Java8BytecodeBridge` method. This is intentional: if you are instrumenting a pre-Java 8 library,
then inlining Java 8 default method calls (or static methods in an interface) into that library
will result in a `java.lang.VerifyError` at runtime, since Java 8 default method invocations are not
legal in Java 7 (and prior) bytecode.
Because OpenTelemetry API has many common default/static interface methods (e.g. `Span.current()`),
the `javaagent-instrumentation-api` artifact has a class `Java8BytecodeBridge` which provides static methods
for accessing these default methods from advice.
In fact, we suggest avoiding Java 8 language features in advice classes at all - sometimes you don't
know what bytecode version is used by the instrumented class.

Sometimes there is a need to associate some context class with an instrumented library class, and
the library does not offer a way to do this. The OpenTelemetry javaagent provides the
`VirtualField` for that purpose:

```java
VirtualField<Runnable, Context> virtualField =
    VirtualField.get(Runnable.class, Context.class);
```

A `VirtualField` is conceptually very similar to a map. It is not a simple map though:
the javaagent uses a lot of bytecode modification magic to make this optimal. Because of this,
retrieving a `VirtualField` instance is rather limited: the `VirtualField#get()`
MUST receive class references as its parameters - it won't work with variables, method params etc.
Both the key class and the context class must be known at compile time for it to work.
