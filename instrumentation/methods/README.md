# Settings for the methods instrumentation

Provides a flexible way to capture telemetry at the method level in JVM applications. By weaving
instrumentation into targeted methods at runtime based on the "otel.instrumentation.methods.include"
configuration property, it measures entry and exit points, execution duration and exception
occurrences. The resulting data is automatically translated into OpenTelemetry traces.

| System property                        | Type   | Default | Description                                                                                                                                        |
| -------------------------------------- | ------ | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.methods.include` | String | None    | List of methods to include for tracing. For more information, see [Creating spans around methods with `otel.instrumentation.methods.include`][cs]. |

[cs]: https://opentelemetry.io/docs/zero-code/java/agent/annotations/#creating-spans-around-methods-with-otelinstrumentationmethodsinclude

# Declarative Configuration Example

In addition to configuring method instrumentation via system properties, you can use declarative
configuration files to specify which methods to trace. This approach allows for more flexible and
maintainable instrumentation settings.
      
Below is an example of a declarative configuration YAML file that enables tracing for a specific
method:

```yaml
file_format: "1.0-rc.1"
instrumentation/development:
  java:
    methods:
      include:
        - class: io.opentelemetry.javaagent.instrumentation.methods.MethodTest$ConfigTracedCallable
          methods:
            - name: call
              span_kind: SERVER
```

**Explanation:**

- `class`: The fully qualified name of the class containing the method to be traced.
- `methods`: A list of method configurations for the specified class.
  - `name`: The name of the method to trace.
  - `span_kind`: The kind of span to create (e.g., SERVER, CLIENT).

This configuration will instruct the OpenTelemetry Java agent to create a span around the `call`
method of the specified class, with the span kind set to SERVER.
