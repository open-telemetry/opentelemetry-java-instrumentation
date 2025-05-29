# OpenTelemetry Kotlin Extension Instrumentation

Our Kotlin coroutine instrumentation relies on a shaded copy of the `opentelemetry-extension-kotlin`
library. This can cause conflicts when the application itself also uses
`opentelemetry-extension-kotlin`, because the shaded and unshaded versions store the OpenTelemetry
context under different keys. To resolve this issue, this instrumentation modifies the application's
copy of `opentelemetry-extension-kotlin` so that it delegates to the shaded version bundled within
the agent.
