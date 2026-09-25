The Java agent and Spring Boot starter no longer bundle the contrib `RuleBasedRoutingSampler`. Migrate declarative configurations from `rule_based_routing` to the SDK incubator's `composite/development` `rule_based` sampler:

```yaml
sampler:
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

Existing `linksbased_parentbased_always_on` configurations continue to work.

Fixes #20239
