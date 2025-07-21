# Advanced configuration options

These are not meant to be used under normal circumstances.

If you find yourself needing to use any of them, it would be great if you could drop us an issue
explaining why, so that we can try to come up with a better solution to address your need.

## Excluding specific classes from being instrumented

This can be used to completely silence spans from a given class/package.

Or as a quick workaround for an instrumentation bug, when byte code in one specific class is problematic.

This option should not be used lightly, as it can leave some instrumentation partially applied,
which could have unknown side-effects.

| System property                | Environment variable           | Purpose                                                                                            |
| ------------------------------ | ------------------------------ | -------------------------------------------------------------------------------------------------- |
| otel.javaagent.exclude-classes | OTEL_JAVAAGENT_EXCLUDE_CLASSES | Suppresses all instrumentation for specific classes, format is "my.package.MyClass,my.package2.\*" |

## Excluding specific classes loaders

This option can be used to exclude classes loaded by given class loaders from being instrumented.

| System property                      | Environment variable                 | Purpose                                                                         |
|--------------------------------------|--------------------------------------|---------------------------------------------------------------------------------|
| otel.javaagent.exclude-class-loaders | OTEL_JAVAAGENT_EXCLUDE_CLASS_LOADERS | Ignore the specified class loaders, format is "my.package.MyClass,my.package2." |

## Running application with security manager

This option can be used to let agent run with all privileges without being affected by security policy restricting some operations.

| System property                                              | Environment variable                                         | Purpose                               |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------- |
| otel.javaagent.experimental.security-manager-support.enabled | OTEL_JAVAAGENT_EXPERIMENTAL_SECURITY_MANAGER_SUPPORT_ENABLED | Grant all privileges to agent code[1] |

[1] Disclaimer: agent can provide application means for escaping security manager sandbox. Do not use
this option if your application relies on security manager to run untrusted code.

## JavaScript snippet injection

This experimental feature allows you to inject JavaScript code into HTML responses from servlet applications. The agent will look for the `</head>` tag in HTML responses, and inject the configured JavaScript snippet before the closing `</head>` tag.

This feature is designed for integrating client-side monitoring.
We plan to integrate OpenTelemetry's own client-side monitoring solution by default once it's available
(see the [browser instrumentation proposal](https://github.com/open-telemetry/community/blob/main/projects/browser-phase-1.md)).

| System property                         | Environment variable                    | Purpose                                                                                                                                                                      |
|-----------------------------------------|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| otel.experimental.javascript-snippet    | OTEL_EXPERIMENTAL_JAVASCRIPT_SNIPPET    | JavaScript code to inject into HTML responses before the closing `</head>` tag. The value should be a complete JavaScript snippet including `<script>` tags if needed, e.g. `-Dotel.experimental.javascript-snippet="<script>console.log('Hello world!');</script>"` |

**Important notes:**

- This only works with servlet-based applications currently
- The snippet is injected only into HTML responses that contain a `</head>` tag
- The agent will attempt to preserve the original character encoding of the response
- If the response already has a `Content-Length` header, it will be updated to reflect the additional content
