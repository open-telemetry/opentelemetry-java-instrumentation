# [Javaagent] Singletons Patterns

## Quick Reference

- Use when: reviewing `*Singletons` holder classes and their callers
- Review focus: field/accessor naming, eager initialization, singleton accessor call sites

Javaagent modules keep shared `Instrumenter` instances and related collaborators in a dedicated
`Singletons` holder class such as `MyLibrarySingletons`.

## Rules

- Initialize shared collaborators at class-load time, either with `static final` field
  initializers or in a `static {}` block.
- Use `GlobalOpenTelemetry.get()` to obtain the `OpenTelemetry` instance.
- The instrumentation name string should match the Gradle module path:
  `"io.opentelemetry.<module-name>"`.
- For exported collaborators, keep the field `private`, use a lower camel case field name, and
  give the accessor method the exact same name as the field. Do not prefix these accessors with
  `get`.
  - `instrumenter` -> `instrumenter()`
  - `helper` -> `helper()`
  - `setter` -> `setter()`
- For exported uppercase constant-like fields that represent stable identifiers, immutable
  descriptors, semantic keys/handles such as `VirtualField` and `ContextKey`, or immutable value
  constants such as strings, booleans, and fixed timeout/interval values, it is acceptable to
  expose them as `public static final` fields with no accessor.
  - `CONTEXT` stays `CONTEXT`
  - `REQUEST_INFO` stays `REQUEST_INFO`
  - `RESPONSE_STATUS` stays `RESPONSE_STATUS`
- Callers should static import only exported singleton accessors and uppercase constant-like
  fields, and use those members unqualified: accessors for lower camel collaborators, fields for
  uppercase constant-like members.
- Keep verb-named helper methods as verbs when they perform work instead of returning a stored
  field. These methods are not singleton accessors and should not be static imported under this
  rule.

## Preferred Pattern

```java
public class MyLibrarySingletons {

  private static final Instrumenter<Request, Response> instrumenter =
      JavaagentHttpServerInstrumenters.create(...);

  private static final Helper helper = new Helper();

  public static Instrumenter<Request, Response> instrumenter() {
    return instrumenter;
  }

  public static Helper helper() {
    return helper;
  }

  private MyLibrarySingletons() {}
}
```

Uppercase field exception:

```java
public class MyLibrarySingletons {

  public static final VirtualField<Request, Context> REQUEST_CONTEXT =
      VirtualField.find(Request.class, Context.class);

  private MyLibrarySingletons() {}
}
```

Caller:

```java
import static io.opentelemetry.javaagent.instrumentation.example.MyLibrarySingletons.helper;
import static io.opentelemetry.javaagent.instrumentation.example.MyLibrarySingletons.instrumenter;

class MyInstrumentation implements TypeInstrumentation {
  void doSomething(Request request) {
    if (instrumenter().shouldStart(parentContext, request)) {
      helper().beforeStart(request);
    }
  }
}
```

Caller for uppercase field:

```java
import static io.opentelemetry.javaagent.instrumentation.example.MyLibrarySingletons.REQUEST_CONTEXT;

class MyInstrumentation implements TypeInstrumentation {
  void doSomething(Request request, Context context) {
    REQUEST_CONTEXT.set(request, context);
  }
}
```

## What to Flag in Review

- Exposed lower camel collaborator fields such as `public static final Instrumenter ...`.
- Private + accessor wrappers around uppercase constant-like fields when a direct `public static
  final` field would be clearer and matches the naming guidance, including semantic keys/handles
  and immutable value constants.
- Accessor methods named `getInstrumenter()`, `getHelper()`, `getSetter()`, and similar when they
  simply return a backing field.
- Call sites that qualify singleton accessor or uppercase constant-like field usage with the holder
  class instead of static importing the accessor or field.
- Static imports of non-accessor helper methods on a `*Singletons` class when the method performs
  work instead of returning a stored singleton or constant.
- Mismatches between a field name and its accessor, such as `private static final Helper helper;`
  with `public static Helper getHelper()`.
