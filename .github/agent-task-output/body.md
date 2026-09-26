Deprecate the Java agent and Spring Boot starter's bundled `io.opentelemetry.contrib:opentelemetry-samplers` dependency and its two supported sampler configurations ahead of their removal from these distributions in 3.0:

- Declarative `rule_based_routing`
- Flat `otel.traces.sampler=linksbased_parentbased_always_on`

For declarative `rule_based_routing`, consider the SDK incubator's `composite/development` `rule_based` sampler:

```yaml
tracer_provider:
  sampler:
    parent_based:
      root:
        composite/development:
          rule_based:
            rules:
              - span_kinds: [server]
                attribute_patterns:
                  key: url.path
                  included: [/actuator*]
                sampler:
                  always_off:
              - sampler:
                  always_on:
```

Its rule and fallback schema and matching behavior differ, so check the [contrib migration discussion](https://github.com/open-telemetry/opentelemetry-java-contrib/issues/3099) before switching.

Outside v3-preview, both legacy names continue to work unchanged and now log a deprecation warning during SDK initialization. With `otel.instrumentation.common.v3-preview=true`, selecting either sampler fails SDK initialization with a configuration error. There is no replacement for `linksbased_parentbased_always_on`.

This does not deprecate the contrib library itself.

Related to #20239.
