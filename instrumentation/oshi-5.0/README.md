# OSHI Instrumentation

System metrics follow the [1.45.0 system semantic conventions](https://github.com/open-telemetry/semantic-conventions/blob/v1.45.0/docs/system/system-metrics.md).

## Using OSHI with OpenTelemetry Java agent

Download the oshi-core jar from
<https://central.sonatype.com/artifact/com.github.oshi/oshi-core> and place it
on the class path. OpenTelemetry Java agent uses the system class loader to
load classes from the oshi-core jar that are used for the metrics.
