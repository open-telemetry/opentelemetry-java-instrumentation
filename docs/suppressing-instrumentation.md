## Suppressing specific auto-instrumentation

You can suppress auto-instrumentation of specific libraries by using
`-Dota.integration.[id].enabled=true`.

where `id` is the instrumentation `id`:

[TODO add table here with all instrumentation ids]

### Even more fine-grained control

You can also exclude specific classes from being instrumented.

This can be useful to completely silence spans from a given class/package.

Or as a quick workaround for an instrumentation bug, when byte code in one specific class is problematic.

This option should not be used lightly, as it can leave some instrumentation partially applied,
which could have unknown side-effects.

If you find yourself needing to use this, it would be great if you could drop us an issue explaining why,
so that we can try to come up with a better solution to address your need.

| System property       | Environment variable  | Purpose                                                                                           |
|-----------------------|-----------------------|---------------------------------------------------------------------------------------------------|
| trace.classes.exclude | TRACE_CLASSES_EXCLUDE | Suppresses all instrumentation for specific classes, format is "my.package.MyClass,my.package2.*" |
