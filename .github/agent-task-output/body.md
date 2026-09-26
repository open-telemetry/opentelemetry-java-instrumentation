Adds schema URLs to OSHI system metrics and migrates their names, units, descriptions, and attributes only when v3-preview is enabled. Addresses #11939 independently of the JVM runtime metrics PR.

`otel.instrumentation.common.v3-preview=true` selects the published 1.44.0 system conventions. For the network packet metric, this changes:

- Name: `system.network.packets` to `system.network.packet.count`
- Unit: `{packets}` to `{packet}`
- Attributes: `device`, `direction` to `system.device`, `network.io.direction`

Without the flag, all seven metrics keep their legacy 1.19.0 shape and receive the 1.19.0 schema. The javaagent keeps its legacy scope name without the flag and uses `io.opentelemetry.oshi-5.0` in preview. Library scope and instrumentation versions are unchanged.

Optional custom process metrics remain schema-less. The deprecated caller-owned `registerObservers(Meter)` overload retains legacy behavior.
