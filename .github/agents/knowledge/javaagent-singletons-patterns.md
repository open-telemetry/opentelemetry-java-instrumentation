# [Javaagent] Singletons Patterns

Consult this article when creating a holder for javaagent collaborators or
changing its accessors and callers. The examples distinguish stored
collaborators from constants and methods that compute values.

Javaagent modules keep shared `Instrumenter` instances and related collaborators in a dedicated
`Singletons` holder class such as `MyLibrarySingletons`. Some modules also use focused helper
holders such as `*ServerSpanNaming` for shared span-name or route-name collaborators; apply the
same accessor and call-site rules when these classes expose stored singleton fields.

## Rules

- Initialize shared collaborators at class-load time, either with `static final` field
  initializers or in a `static {}` block.
- Use `GlobalOpenTelemetry.get()` to obtain the `OpenTelemetry` instance.
- The instrumentation name string should match the Gradle module path:
  `"io.opentelemetry.<module-name>"`.
- For exported collaborators, keep the field `private`, use a lower camel case field name, and
  give the accessor method the exact same name as the field. Do not prefix these accessors with
  `get`. This rule applies only to zero-arg methods that directly return a stored singleton
  field. It applies regardless of whether the holder class is named `*Singletons`,
  `*ServerSpanNaming`, or another focused holder name. Methods that take arguments or compute a
  value are not singleton accessors and keep their normal names (including `get*` when
  appropriate).
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
- A `static final VirtualField` field must be `SCREAMING_SNAKE_CASE` whether the field is `private`
  or `public`. Visibility only decides whether the field is exposed directly (per the previous
  bullet) or kept private and used only inside the holder class — it never justifies a lower camel
  case name. This also applies when the value is created by a runtime factory method such as
  `VirtualField.find(...)`; the runtime-created origin does not make it a collaborator object. This
  does not apply to non-static `VirtualField` fields, such as one passed into a constructor and
  stored as an instance field. Other semantic key/handle types (`ContextKey`, `AttributeKey`,
  `MethodHandle`, `Pattern`) are good candidates for the same treatment, but this repository does not
  yet require it for them.
- Callers should static import only exported singleton accessors and uppercase constant-like
  fields, and use those members unqualified: accessors for lower camel collaborators, fields for
  uppercase constant-like members. This includes route/span naming accessors such as
  `serverSpanName()` when they simply return a stored `HttpServerRouteGetter` or similar
  collaborator.
- Keep verb-named helper methods as verbs when they perform work instead of returning a stored
  field. These methods are not singleton accessors and should not be static imported under this
  rule.
- Methods on a `*Singletons` class that take arguments (for example `addressAndPort(client)` or
  `getAddressAndPort(client)`) are not singleton accessors. Do not apply the field-style
  accessor naming rule to them; retain their `get*` prefix when appropriate.

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
