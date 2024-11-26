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
module share helper classes, [muzzle runtime checks](muzzle.md), and applicable class loader criteria,
and can only be enabled or disabled as a set.

The OpenTelemetry javaagent finds all modules by using Java's `ServiceLoader` API. To make your
instrumentation visible, make sure that a proper `META-INF/services/` file is present in
the javaagent jar. The easiest way to do it is using `@AutoService`:

```java

@AutoService(InstrumentationModule.class)
public class MyLibraryInstrumentationModule extends InstrumentationModule {
  // ...
}
```

An `InstrumentationModule` needs to have at least one name. The user of the javaagent can
[suppress a chosen instrumentation][suppress] by referring to it by one of
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

### Inject additional resources using the `registerHelperResources(HelperResourceBuilder)` method

Some libraries may expose SPI interfaces that you can easily implement to provide
telemetry-gathering capabilities. The OpenTelemetry javaagent is able to inject `ServiceLoader`
service provider files, but it needs to be told which ones:

```java
@Override
public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    helperResourceBuilder.register("META-INF/services/org.my.library.SpiClass");
}
```

All classes referenced by service providers defined in the `helperResourceNames()` method are treated
as helper classes: they're checked for invalid references and automatically injected into the
application class loader.

### Inject additional instrumentation helper classes manually with the `getAdditionalHelperClassNames()` method

If you don't use the [muzzle gradle plugins](muzzle.md), or are in a scenario that requires
providing the helper classes manually (for example, an unusual SPI implementation), you can
override the `getAdditionalHelperClassNames()` method to provide a list of additional helper
classes to be injected into the application class loader when the instrumentation is applied:

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
injected into the application class loader after those provided by the muzzle codegen plugin.

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

### Define instrumented types with the `typeInstrumentations()` method

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
public class MyTypeInstrumentation implements TypeInstrumentation {
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

This method describes what transformations should be applied to the
matched type. The interface `TypeTransformer`, implemented internally by the agent,
defines a set of available transformations that you can apply:

- `applyAdviceToMethod(ElementMatcher<? super MethodDescription>, String)` lets you apply
  an advice class (the second parameter) to all matching methods (the first parameter). We
  suggest to make the method matchers as strict as possible: the type instrumentation should
  only instrument the code that it targets.
- `applyTransformer(AgentBuilder.Transformer)` lets you to inject an arbitrary ByteBuddy
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

Implementations of `TypeInstrumentation` often embed advice classes as static inner
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

Unfortunately many standard practices do not apply to advice classes:

- If they're inner classes, they MUST be static.
- They MUST only contain static methods.
- They MUST NOT contain any state (fields) whatsoever, static constants included. Only the advice
  methods' content is copied to the instrumented code, constants are not.
- Inner advice classes defined in an `InstrumentationModule` or a `TypeInstrumentation` MUST NOT use
  anything from the outer class (loggers, constants, etc).
- Reusing code by extracting a common method and/or parent class won't work: create additional helper
  classes to store any reusable code instead.
- They SHOULD NOT contain any methods other than `@Advice`-annotated method.

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
the `javaagent-extension-api` artifact has a class `Java8BytecodeBridge` which provides static
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

Use of `VirtualField` requires the `muzzle-generation` gradle plugin. Failing to use the plugin will result in
ClassNotFoundException when trying to access the field.

### Avoid using @Advice.Origin Method

You shouldn't use ByteBuddy's @Advice.Origin Method method, as it
inserts a call to `Class.getMethod(...)` in a transformed method.

Instead, get the declaring class and method name, as loading
constants from a constant pool is a much simpler operation.

For example:

```
@Advice.Origin("#t") Class<?> declaringClass,
@Advice.Origin("#m") String methodName
```

[suppress]: https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/#suppressing-specific-auto-instrumentation

## Use non-inlined advice code with `invokedynamic`

Using non-inlined advice code is possible thanks to the `invokedynamic` instruction, this strategy
is referred as "indy" in reference to this. By extension "indy modules" are the instrumentation
modules using this instrumentation strategy.

The most common way to instrument code with ByteBuddy relies on inlining, this strategy will be
referred as "inlined" strategy as opposed to "indy".

For inlined advices, the advice code is directly copied into the instrumented method.
In addition, all helper classes are injected into the classloader of the instrumented classes.

For indy, advice classes are not inlined. Instead, they are loaded alongside all helper classes
into a special `InstrumentationModuleClassloader`, which sees the classes from both the instrumented
application classloader and the agent classloader.
The instrumented classes call the advice classes residing in the `InstrumentationModuleClassloader` via
invokedynamic bytecode instructions.

Using indy instrumentation has these advantages:

- allows instrumentations to have breakpoints set in them and be debugged using standard debugging techniques
- provides clean isolation of instrumentation advice from the application and other instrumentations
- allows advice classes to contain static fields and methods which can be accessed from the advice entry points - in fact generally good development practices are enabled (whereas inlined advices are [restricted in how they can be implemented](#use-advice-classes-to-write-code-that-will-get-injected-to-the-instrumented-library-classes))

### Indy modules and transition

Making an instrumentation "indy" compatible (or native "indy") is not as straightforward as making it "inlined".
However, ByteBuddy provides a set of tools and APIs that are mentioned below to make the process as smooth as possible.

Due to the changes needed on most of the instrumentation modules the migration can't be achieved in a single step,
we thus have to implement it in two steps:

- `InstrumentationModule#isIndyModule` implementation return `true` (and changes needed to make it indy compatible)
- set `inlined = false` on advice methods annotated with `@Advice.OnMethodEnter` or `@Advice.OnMethodExit`

The `otel.javaagent.experimental.indy` (default `false`) configuration option allows to opt-in for
using "indy". When set to `true`, the `io.opentelemetry.javaagent.tooling.instrumentation.indy.AdviceTransformer`
will transform advices automatically to make them "indy native". Using this option is temporary and will
be removed once all the instrumentations are "indy native".

This configuration is automatically enabled in CI with `testIndy*` checks or when the `-PtestIndy=true` parameter is added to gradle.

In order to preserve compatibility with both instrumentation strategies, we have to omit the `inlined = false`
from the advice method annotations.

We have three sets of instrumentation modules:
- "inlined only": only compatible with "inlined", `isIndyModule` returns `false`.
- "indy compatible": compatible with both "indy" and "inlined", do not override `isIndyModule`, advices are modified with `AdviceTransformer` to be made "indy native" or "inlined" at runtime.
- "indy native": only compatible with "indy" `isIndyModule` returns `true`.

The first step of the migration is to move all the "inlined only" to the "indy compatible" category
by refactoring them with the limitations described below.

Once everything is "indy compatible", we can evaluate changing the default value of `otel.javaagent.experimental.indy`
to `true` and make it non-experimental.

### Shared classes and common classloader

By default, all the advices of an instrumentation module will be loaded into isolated classloaders,
one per instrumentation module. Some instrumentations require to use a common classloader in order
to preserve the semantics of `static` fields and to share classes.

In order to load multiple `InstrumentationModule` implementations in the same classloader, you need to
override the `ExperimentalInstrumentationModule#getModuleGroup` to return an identical value.

### Classes injected in application classloader

Injecting classes in the application classloader is possible by implementing the
`ExperimentalInstrumentationModule#injectedClassNames` method. All the class names listed by the
returned value will be loaded in the application classloader instead of the agent or instrumentation
module classloader.

This allows for example to access package-private methods that would not be accessible otherwise.

### Advice local variables

With inlined advices, declaring an advice method argument with `@Advice.Local` allows defining
a variable that is local to the advice execution for communication between the enter and exit advices.

When advices are not inlined, usage of `@Advice.Local` is not possible. It is however possible to
return a value from the enter advice and get the value in the exit advice with a parameter annotated
with `@Advice.Enter`, for example:

```java
@Advice.OnMethodEnter(suppress = Throwable.class, inlined = false)
public static Object onEnter(@Advice.Argument(1) Object request) {
  return "enterValue";
}

@Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inlined = false)
public static void onExit(@Advice.Argument(1) Object request,
                          @Advice.Enter Object enterValue) {
  // do something with enterValue
}
```

### Modifying method arguments

With inlined advices, using the `@Advice.Argument` annotation on method parameter with `readOnly = false`
allows modifying instrumented method arguments.

When using non-inlined advices, reading the argument values is still done with `@Advice.Argument`
annotated parameters, however modifying the values is done through the advice method return value
and `@Advice.AssignReturned.ToArguments` annotation:

```java
@Advice.OnMethodEnter(suppress = Throwable.class, inlined = false)
@Advice.AssignReturned.ToArguments(@ToArgument(1))
public static Object onEnter(@Advice.Argument(1) Object request) {
  return "hello";
}
```

It is possible to modify multiple arguments at once by using an array, see usages of
`@Advice.AssignReturned.ToArguments` for detailed examples.

### Modifying method return value

With inlined advices, using the `@Advice.Return` annotation on method parameter with `readOnly = false`
allows modifying instrumented method return value on exit advice.

When using non-inlined advices, reading the original return value is still done with the `@Advice.Return`
annotated parameter, however modifying the value is done through the advice method return value
and `@Advice.AssignReturned.ToReturned`.

```java
@Advice.OnMethodExit(suppress = Throwable.class, inlined = false)
@Advice.AssignReturned.ToReturned
public static Object onExit(@Advice.Return Object returnValue) {
  return "hello";
}
```

### Writing to internal class fields

With inlined advices, using the `@Advice.FieldValue(value = "fieldName", readOnly = false)` annotation
on advice method parameters allows modifying the `fieldName` field of the instrumented class.

When using non-inlined advices, reading the original field value is still done with the `@Advice.FieldValue`
annotated parameter, however modifying the value is done through the advice method return value
and `@Advice.AssignReturned.ToFields` annotation.

```java
@Advice.OnMethodEnter(suppress = Throwable.class, inlined = false)
@Advice.AssignReturned.ToFields(@ToField("fieldName"))
public static Object onEnter(@Advice.FieldValue("fieldName") Object originalFieldValue) {
  return "newFieldValue";
}
```

It is possible to modify multiple fields at once by using an array, see usages of
`@Advice.AssignReturned.ToFields` for detailed examples.
