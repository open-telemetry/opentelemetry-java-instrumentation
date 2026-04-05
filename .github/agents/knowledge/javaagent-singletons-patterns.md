# [Javaagent] Singletons Patterns

## Quick Reference

- Use when: reviewing `*Singletons` holder classes and their callers
- Review focus: private fields, accessor naming, eager initialization, static-import call sites

Javaagent modules keep shared `Instrumenter` instances and related collaborators in a dedicated
`Singletons` holder class such as `MyLibrarySingletons`.

## Rules

- Keep exported singleton-holder fields `private`. Do not expose package-private or public
  `static` fields from `*Singletons` classes.
- Initialize shared collaborators at class-load time, either with `static final` field
  initializers or in a `static {}` block.
- Use `GlobalOpenTelemetry.get()` to obtain the `OpenTelemetry` instance.
- The instrumentation name string should match the Gradle module path:
  `"io.opentelemetry.<module-name>"`.
- For exported collaborators, use lower camel case field names and give the accessor method the
  exact same name as the field. Do not prefix these accessors with `get`.
  - `instrumenter` -> `instrumenter()`
  - `helper` -> `helper()`
  - `setter` -> `setter()`
- Callers should static import singleton accessor methods and invoke them unqualified.
- Keep verb-named helper methods as verbs when they perform work instead of returning a stored
  field. This naming rule applies to field accessors.

## Preferred Pattern

```java
public final class MyLibrarySingletons {

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

## What to Flag in Review

- Exposed singleton-holder fields such as `public static final Instrumenter ...`.
- Accessor methods named `getInstrumenter()`, `getHelper()`, `getSetter()`, and similar when they
  simply return a backing field.
- Call sites that qualify singleton field-accessor calls with the holder class instead of static
  importing the accessor method.
- Mismatches between a field name and its accessor, such as `private static final Helper helper;`
  with `public static Helper getHelper()`.
