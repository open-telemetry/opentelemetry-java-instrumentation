# Write an `InstrumentationModule` step by step

The `InstrumentationModule` is the central piece of any OpenTelemetry javaagent instrumentation. There
are many conventions that our javaagent uses, many pitfalls, and not so obvious patterns that one has to
follow when implementing a module.

Here we describe how a javaagent instrumentation can be implemented and document the main aspects
that may affect your instrumentation. In addition to this file, we suggest reading the 
`InstrumentationModule` and `TypeInstrumentation` Javadocs, as they often provide more detailed
explanations of how to use a particular method and why it works the way it does.

## The centerpiece of each javaagent instrumentation: `InstrumentationModule`

An `InstrumentationModule` describes a set of individual `TypeInstrumentation` that need to be
applied together to correctly instrument a specific library. Type instrumentations grouped in a
module share helper classes, [muzzle runtime checks](muzzle.md), and applicable classloader criteria,
and can only be enabled or disabled as a set.

The OpenTelemetry javaagent finds all modules by using Java's `ServiceLoader` API. To make your
instrumentation visible, make sure that a proper `META-INF/services/` file is present in
the javaagent jar. The easiest way to do it is using `@AutoService`:

```java

@AutoService(InstrumentationModule.class)
class MyLibraryInstrumentationModule extends InstrumentationModule {
  // ...
}
```

An `InstrumentationModule` needs to have at least one name. The user of the javaagent can
[suppress a chosen instrumentation](../suppressing-instrumentation.md) by referring to it by one of
its names. The instrumentation module names use `kebab-case`. The main instrumentation name, which
is the first one, must be the same as the gradle module name, excluding the version suffix if present.

```java
public MyLibraryInstrumentationModule() {
  super("my-library", "my-library-1.0");
}
```

For detailed information on `InstrumentationModule` names, see the
`InstrumentationModule#InstrumentationModule(String, String...)` Javadoc.

### Change the order of applying instrumentation modules using the `order()` method

To apply instrumentations in a specific order you can override the `order()`
method to specify an order, like in the following snippet:

```java
@Override
public int order() {
  return 1;
}
```

The higher the value returned by `order()` the later the instrumentation module is applied.
Default value is `0`.

### Tell the agent which classes are a part of the instrumentation by overriding the `isHelperClass()` method

The OpenTelemetry javaagent picks up helper classes used in the instrumentation/advice classes and
injects them into the application classpath. The agent can automatically find those classes that 
follow our package conventions, but it is also possible to explicitly tell which packages/classes
are supposed to be treated as helper classes by implementing `isHelperClass(String)`:

```java
@Override
public boolean isHelperClass(String className) {
  return className.startsWith("org.my.library.opentelemetry");
}
```

For more information on package conventions, see the [muzzle docs](muzzle.md#compile-time-reference-collection).

### Inject additional resources using the `helperResourceNames()` method

Some libraries may expose SPI interfaces that you can easily implement to provide
telemetry-gathering capabilities. The OpenTelemetry javaagent is able to inject `ServiceLoader`
service provider files, but it needs to be told which ones:

```java
@Override
public List<String> helperResourceNames() {
  return singletonList("META-INF/services/org.my.library.SpiClass");
}
```

All classes referenced by service providers defined in the `helperResourceNames()` method are treated
as helper classes: they're checked for invalid references and automatically injected into the 
application classloader.

### Inject additional instrumentation helper classes manually with the `getAdditionalHelperClassNames()` method

If you don't use the [muzzle gradle plugins](muzzle.md), or are in a scenario that requires
providing the helper classes manually (for example, an unusual SPI implementation), you can
override the `getAdditionalHelperClassNames()` method to provide a list of additional helper
classes to be injected into the application classloader when the instrumentation is applied:

```java
public List<String> getAdditionalHelperClassNames() {
  return Arrays.asList(
      "org.my.library.instrumentation.SomeHelper",
      "org.my.library.instrumentation.AnotherHelper");
}
```

The order of the class names returned by this method matters: if you have several helper classes
extending one another, you'll want to return the base class first. For example, if you have a
`B extends A` class, the list should contain `A` first and `B` second. The helper classes are 
injected into the application classloader after those provided by the muzzle codegen plugin.

### Restrict the criteria for applying the instrumentation by extending the `classLoaderMatcher()` method

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

The above example skips instrumenting the application code if it does not contain the class 
introduced in the version covered by your instrumentation.

### The `typeInstrumentations()` method

As last step, an `InstrumentationModule` implementation must provide at least one
`TypeInstrumentation` implementation. A module with no type instrumentations does nothing.

```java
@Override
public List<TypeInstrumentation> typeInstrumentations() {
  return Collections.singletonList(new MyTypeInstrumentation());
}
```

## Describe the changes applied to a type using `TypeInstrumentation` class

A `TypeInstrumentation` describe the changes that need to be made to a single type. Depending
on the instrumented library, they might only make sense in conjunction with other type 
instrumentations, grouped together in a module.

```java
class MyTypeInstrumentation implements TypeInstrumentation {
  // ...
}
```

### Define which Java types should qualify for instrumentation by overriding the `typeMatcher()` method

A type instrumentation needs to declare what class (or classes) are going to be instrumented:

```java
@Override
public ElementMatcher<TypeDescription> typeMatcher() {
  return named("org.my.library.SomeClass");
}
```

### Make the agent faster by implementing `classLoaderOptimization()` method

When you need to instrument all classes that implement a particular interface, or all classes that
are annotated with a particular annotation, implement the `classLoaderOptimization()` method. 
Matching classes by their name is quite fast, but inspecting the actual bytecode (for example, 
implements, has annotation, has method, etc.) is a rather expensive operation. 

The matcher returned by the `classLoaderOptimization()` method makes the `TypeInstrumentation`
significantly faster when instrumenting applications that do not contain the library:

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

### Define the actual code transformations with the `transform(TypeTransformer)` method

The `TypeInstrumentation` method describes what transformations should be applied to the
matched type. The type interface `TypeTransformer`, implemented internally by the agent, 
defines a set of available transformations that you can apply:

* `applyAdviceToMethod(ElementMatcher<? super MethodDescription>, String)` lets you apply
  an advice class (the second parameter) to all matching methods (the first parameter). We
  suggest to make the method matchers as strict as possible: the type instrumentation should
  only instrument the code that it targets.
* `applyTransformer(AgentBuilder.Transformer)` lets you to inject an arbitrary ByteBuddy
  transformer. This is an advanced, low-level option that is not subjected to muzzle safety
  checks and helper class detection. Use it responsibly.

Consider the following example:

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

Implementations of `TypeInstrumentation` often implement advice classes as static inner
classes. These classes are referred to by name when applying advice classes to methods in
the `transform()` method.

> You might have noticed in the example above that the advice class is being referenced as follows:
> 
> ```java
> this.getClass().getName() + "$MethodAdvice"
> ```
>
> Referring to the inner class and calling `getName()` would be easier to read and understand,
> but note that this is intentional and should be maintained. Instrumentation modules are loaded
> by the agent's class loader, and this string concatenation is an optimization that prevents
> the actual advice class from being loaded into the agent's class loader.

## Use advice classes to write code that will get injected to the instrumented library classes

Advice classes aren't really classes in that they're raw pieces of code that are pasted directly into
the instrumented library class files. You should not treat them as ordinary, plain Java classes. 

Unfortunately many standard practices do not apply to Advice classes:

* If they're inner classes, they MUST be static.
* They MUST only contain static methods.
* They MUST NOT contain any state (fields) whatsoever, static constants included. Only the advice
  methods' content is copied to the instrumented code, constants are not.
* Inner advice classes defined in an `InstrumentationModule` or a `TypeInstrumentation` MUST NOT use
  anything from the outer class (loggers, constants, etc).
* Reusing code by extracting a common method and/or parent class won't work: create additional helper
  classes to store any reusable code instead.
* They SHOULD NOT contain any methods other than `@Advice`-annotated method.

Consider the following example:

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

Include the `suppress = Throwable.class` property in `@Advice`-annotated methods. Exceptions 
thrown by the advice methods get caught and handled by a special `ExceptionHandler` that the
OpenTelemetry javaagent defines. The handler makes sure to properly log all unexpected
exceptions.

The `OnMethodEnter` and `OnMethodExit` advice methods often need to share several pieces of
information. We use local variables prefixed with `otel` to pass context, scope, and other
data between both methods, like in the following example:

```java
@Advice.OnMethodEnter(suppress = Throwable.class)
public static void onEnter(@Advice.Argument(1) Object request,
                           @Advice.Local("otelContext") Context context,
                           @Advice.Local("otelScope") Scope scope) {
  // ...
}
```

For telemetry-producing instrumentations, both methods follow this pattern:

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

Notice that the example above doesn't use `Context.current()`, but a `Java8BytecodeBridge` method
instead. This is intentional: if you are instrumenting a pre-Java 8 library, inlining Java 8 default
method calls (or static methods in an interface) into that library results in a `java.lang.VerifyError`
at runtime, since Java 8 default method invocations aren't legal in Java 7 (and prior) bytecode.

Since the OpenTelemetry API has many common default/static interface methods, like `Span.current()`,
the `javaagent-instrumentation-api` artifact has a class `Java8BytecodeBridge` which provides static
methods for accessing these default methods from advice. We suggest avoiding Java 8 language features
in advice classes at all - sometimes you don't know what bytecode version is used by the instrumented class.

### Associate instrumentation classes with instrumented library classes

Sometimes there is a need to associate some instrumentation class with an instrumented library class, and
the library does not offer a way to do this. The OpenTelemetry javaagent provides `VirtualField`
for that purpose. Consider the following example:

```java
VirtualField<Runnable, Context> virtualField =
    VirtualField.get(Runnable.class, Context.class);
```

A `VirtualField` has a very similar interface to a map. It is not a simple map though: the javaagent uses many 
bytecode tweaks to optimize it. Because of this, retrieving a `VirtualField` instance is rather
limited: the `VirtualField#get()` method must receive class references as its parameters; it won't 
work with variables, method params, etc. Both the owner class and the field class must be known at
compile time for it to work.
