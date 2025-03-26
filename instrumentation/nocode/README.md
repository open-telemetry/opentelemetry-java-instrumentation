# "nocode" instrumentation

Sometimes, you need to apply custom instrumentation to code you don't control/can't edit
(e.g., for a third-party app).  This module provides a way to do that, controlling many
behaviors of instrumentation available through the trace api.

# Usage

Set `OTEL_JAVA_INSTRUMENTATION_NOCODE_YML_FILE=/path/to/your.yml`, where the yml describes
what methods you want to instrument and how:

```
- class: myapp.BusinessObject
  method: update
  spanName: this.getName()
  attributes:
    - key: "business.context"
      value: this.getDetails().get("context")

- class: mycustom.SpecialClient
  method: doRequest
  spanKind: CLIENT
  spanStatus: 'returnValue.code() > 3 ? "OK" : "ERROR"'
  attributes:
    - key: "special.header"
      value: 'param0.headers().get("special-header").substring(5)'
```

Expressions are written in [JEXL](https://commons.apache.org/proper/commons-jexl/reference/syntax.html) and may use
the following variables:
  - `this` - which may be null for a static method
  - `param0` through `paramN` where 0 indexes the first parameter to the method
  - `returnValue` which is only defined for `spanStatus` and may be null (if an exception is thrown or the method returns void)
  - `error` which is only defined for `spanStatus` and is the `Throwable` thrown by the method invocation (or null if a normal return)

# See also

If you don't need this much control over span creation, you might find
[methods instrumentation](../methods/README.md) a simpler way to get started.

# Safety

Please be aware of all side effects of statements you write in "nocode" instrumentation.
Avoid calling methods that permute state, interact with threads or locks, or might
have signigicant performance impact.  Additionally, be aware that code under
active development might have its class or method names change, breaking instrumentation
created in this way.
