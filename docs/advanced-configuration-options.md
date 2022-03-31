# Advanced configuration options

These are not meant to be used under normal circumstances.

If you find yourself needing to use any of them, it would be great if you could drop us an issue
explaining why, so that we can try to come up with a better solution to address your need.

## Excluding specific classes from being instrumented

This can be used to completely silence spans from a given class/package.

Or as a quick workaround for an instrumentation bug, when byte code in one specific class is problematic.

This option should not be used lightly, as it can leave some instrumentation partially applied,
which could have unknown side-effects.

| System property                | Environment variable           | Purpose                                                                                           |
|--------------------------------|--------------------------------|---------------------------------------------------------------------------------------------------|
| otel.javaagent.exclude-classes | OTEL_JAVAAGENT_EXCLUDE_CLASSES | Suppresses all instrumentation for specific classes, format is "my.package.MyClass,my.package2.*" |
