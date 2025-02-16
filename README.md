# OpenTelemetry Instrumentation for Java

[![Release](https://img.shields.io/github/v/release/open-telemetry/opentelemetry-java-instrumentation?include_prereleases&style=)](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/open-telemetry/opentelemetry-java-instrumentation/badge)](https://scorecard.dev/viewer/?uri=github.com/open-telemetry/opentelemetry-java-instrumentation)
[![Slack](https://img.shields.io/badge/slack-@cncf/otel--java-blue.svg?logo=slack)](https://cloud-native.slack.com/archives/C014L2KCTE3)

* [About](#about)
* [Getting Started](#getting-started)
* [Configuring the Agent](#configuring-the-agent)
* [Supported libraries, frameworks, and application servers](#supported-libraries-frameworks-and-application-servers)
* [Creating agent extensions](#creating-agent-extensions)
* [Manually instrumenting](#manually-instrumenting)
* [Logger MDC auto-instrumentation](#logger-mdc-mapped-diagnostic-context-auto-instrumentation)
* [Troubleshooting](#troubleshooting)
* [Contributing](#contributing)

## About

This project provides a Java agent JAR that can be attached to any Java 8+
application and dynamically injects bytecode to capture telemetry from a
number of popular libraries and frameworks.
You can export the telemetry data in a variety of formats.
You can also configure the agent and exporter via command line arguments
or environment variables. The net result is the ability to gather telemetry
data from a Java application without code changes.

This repository also publishes standalone instrumentation for several libraries (and growing)
that can be used if you prefer that over using the Java agent.
Please see the standalone library instrumentation column
on [Supported Libraries](docs/supported-libraries.md#libraries--frameworks).
If you are looking for documentation on using those.

## Getting Started

Download
the [latest version](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar).

This package includes the instrumentation agent as well as
instrumentations for all supported libraries and all available data exporters.
The package provides a completely automatic, out-of-the-box experience.

*Note: There are 2.x releases and 1.x releases. The 2.0 release included significant breaking
changes, the details of which can be found in the [release notes](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/tag/v2.0.0).
It is recommended to use the latest 2.x release which will have the latest features and improvements.
1.x will receive security patches for a limited time and will not include other bug fixes and
enhancements.*


Enable the instrumentation agent using the `-javaagent` flag to the JVM.

```
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -jar myapp.jar
```

By default, the OpenTelemetry Java agent uses the
[OTLP exporter](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp)
configured to send data to an
[OpenTelemetry collector](https://github.com/open-telemetry/opentelemetry-collector/blob/main/receiver/otlpreceiver/README.md)
at `http://localhost:4318`.

Configuration parameters are passed as Java system properties (`-D` flags) or
as environment variables. See [the configuration documentation][config-agent]
for the full list of configuration items. For example:

```
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.resource.attributes=service.name=your-service-name \
     -Dotel.traces.exporter=zipkin \
     -jar myapp.jar
```

## Configuring the Agent

### 2️⃣ **Configuration Parameters**

| Parameter                                                                                        | Description                                                 |
|--------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| `-Dotel.metrics.exporter=none`                                                                   | Disables metric collection.                                 |
| `-Dotel.logs.exporter=none`                                                                      | Disables log collection.                                    |
| `-Dotel.exporter.otlp.traces.endpoint="{host}}/v1/apiwiz-runtime-agent/api-visualiser/otel/traces"` | Specifies the OTLP (OpenTelemetry Protocol) endpoint for trace data. |
| `-Dotel.exporter.otlp.traces.headers="x-tenant={workspaceName},apikey={apikey}"`                         | Adds authentication headers for the OTLP trace exporter. |
| `-Dotel.exporter.otlp.traces.timeout=600000`                                                     | Sets the trace export timeout (600,000 ms = 10 minutes). |
| `-Dapi.compliance.tracing.traceId=traceid`                                                       | Defines the trace ID key for API compliance tracing. |
| `-Dapi.compliance.tracing.spanId=spanid`                                                         | Defines the span ID key for API compliance tracing. |
| `-Dapi.compliance.tracing.parentSpanId=parentspanid`                                             | Defines the parent span ID key. |
| `-Dapi.compliance.tracing.requestTimeStamp=request-timestamp`                                    | Defines the request timestamp key. |
| `-Dapi.compliance.tracing.responseTimeStamp=response-timestamp`                                  | Defines the response timestamp key. |
| `-Dapi.compliance.tracing.gatewayType=gateway-type`                                              | Defines the gateway type key. |
| `-Dapi.compliance.detect.api={host}}/v1/apiwiz-runtime-agent/compliance/detect`                  | Sets the API endpoint for compliance detection. |
| `-Dworkspace-id=stage-data`                                                                      | Sets the workspace ID. |
| `-Dapi.compliance.tracing.enable-tracing=true`                                                   | Enables API compliance tracing. |
| `-Dx-apikey={apikey}`                                                                            | Specifies an API key for authentication. |
| `-Dserver-ip={your_server_ip}`                                                                   | Sets the server's IP address. |

## Example

```bash
java -javaagent:/path/to/opentelemetry-javaagent.jar \
     -Dotel.metrics.exporter=none \
     -Dotel.logs.exporter=none \
     -Dotel.exporter.otlp.traces.endpoint="https://dev-api.apiwiz.io/v1/apiwiz-runtime-agent/api-visualiser/otel/traces" \
     -Dotel.exporter.otlp.traces.headers="x-tenant=stage-data,x-apikey=E5KAHRATn8kjWZbdAVaXTD7FVtCsdXuijm9dRpatlBLJ4gOplbGSMWSOlcn8x1lwyJLgzug1UVfmF%2FduXtk1oa9oG5%2BS6iDGA9zWpTjafpy4U0OT9kBiA5r%2FnDb55MzE4qfKfWXlpAqtEZrDiwtOlR1tjjzdCTEYmqPEfjYgQr7%2FWjIUBU2UQdB6zg2oQUWKBbPf1NY%2BF%2BTZiVcbhbwHibM7%2ByTSeLydksCElDo84GEb06QyoHxkXbB%2BQLWBdw9PvShGJr1e3xnIcf1MupyIPDVfB1WjwqNPSUD%2BKKqtKI%2FOSjQGR4x%2F6ov8zESd1nGQAiEKCvAW5%2FuhF5uVv0A70w%3D%3D" \
     -Dotel.exporter.otlp.traces.timeout=600000 \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/apiwiz \
     -Dspring.datasource.username=postgres \
     -Dspring.datasource.password=apiwiz \
     -Dapi.compliance.tracing.traceId=traceid \
     -Dapi.compliance.tracing.spanId=spanid \
     -Dapi.compliance.tracing.parentSpanId=parentspanid \
     -Dapi.compliance.tracing.requestTimeStamp=request-timestamp \
     -Dapi.compliance.tracing.responseTimeStamp=response-timestamp \
     -Dapi.compliance.tracing.gatewayType=gateway-type \
     -Dapi.compliance.detect.api=https://dev-api.apiwiz.io/v1/apiwiz-runtime-agent/compliance/detect \
     -Dworkspace-id=stage-data \
     -Dapi.compliance.tracing.enable-tracing=true \
     -Dx-apikey=E5KAHRATn8kjWZbdAVaXTD7FVtCsdXuijm9dRpatlBLJ4gOplbGSMWSOlcn8x1lwyJLgzug1UVfmF%2FduXtk1oa9oG5%2BS6iDGA9zWpTjafpy4U0OT9kBiA5r%2FnDb55MzE4qfKfWXlpAqtEZrDiwtOlR1tjjzdCTEYmqPEfjYgQr7%2FWjIUBU2UQdB6zg2oQUWKBbPf1NY%2BF%2BTZiVcbhbwHibM7%2ByTSeLydksCElDo84GEb06QyoHxkXbB%2BQLWBdw9PvShGJr1e3xnIcf1MupyIPDVfB1WjwqNPSUD%2BKKqtKI%2FOSjQGR4x%2F6ov8zESd1nGQAiEKCvAW5%2FuhF5uVv0A70w%3D%3D" \
     -Dserver-ip=8.8.8.8 \
     -jar my-application.jar
```

## Supported libraries, frameworks, and application servers

We support an impressively huge number
of [libraries and frameworks](docs/supported-libraries.md#libraries--frameworks) and
a majority of the most
popular [application servers](docs/supported-libraries.md#application-servers)...right out of the
box!
[Click here to see the full list](docs/supported-libraries.md) and to learn more about
[disabled instrumentation](docs/supported-libraries.md#disabled-instrumentations)
and how to [suppress unwanted instrumentation][suppress].

## Creating agent extensions

[Extensions](examples/extension/README.md) add new features and capabilities to the agent without
having to create a separate distribution or to fork this repository. For example, you can create
custom samplers or span exporters, set new defaults, and embed it all in the agent to obtain a
single jar file.

## Manually instrumenting

For most users, the out-of-the-box instrumentation is completely sufficient and nothing more has to
be done. Sometimes, however, users wish to add attributes to the otherwise automatic spans,
or they might want to manually create spans for their own custom code.

For detailed instructions, see [Manual instrumentation][manual].

## Logger MDC (Mapped Diagnostic Context) auto-instrumentation

It is possible to inject trace information like trace IDs and span IDs into your
custom application logs. For details, see [Logger MDC
auto-instrumentation](docs/logger-mdc-instrumentation.md).

## Troubleshooting

To turn on the agent's internal debug logging:

`-Dotel.javaagent.debug=true`

**Note**: These logs are extremely verbose. Enable debug logging only when needed.
Debug logging negatively impacts the performance of your application.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

Triagers ([@open-telemetry/java-instrumentation-triagers](https://github.com/orgs/open-telemetry/teams/java-instrumentation-triagers)):

- [Jonas Kunz](https://github.com/JonasKunz), Elastic
- [Sylvain Juge](https://github.com/SylvainJuge), Elastic

Approvers ([@open-telemetry/java-instrumentation-approvers](https://github.com/orgs/open-telemetry/teams/java-instrumentation-approvers)):

- [Gregor Zietlinger](https://github.com/zeitlinger), Grafana
- [Jack Berg](https://github.com/jack-berg), New Relic
- [Jason Plumb](https://github.com/breedx-splk), Splunk
- [Jay DeLuca](https://github.com/jaydeluca)
- [Jean Bisutti](https://github.com/jeanbisutti), Microsoft
- [John Watson](https://github.com/jkwatson), Cloudera
- [Steve Rao](https://github.com/steverao), Alibaba

Maintainers ([@open-telemetry/java-instrumentation-maintainers](https://github.com/orgs/open-telemetry/teams/java-instrumentation-maintainers)):

- [Lauri Tulmin](https://github.com/laurit), Splunk
- [Trask Stalnaker](https://github.com/trask), Microsoft

Emeritus maintainers:

- [Mateusz Rzeszutek](https://github.com/mateuszrzeszutek)
- [Nikita Salnikov-Tarnovski](https://github.com/iNikem)
- [Tyler Benson](https://github.com/tylerbenson)

Learn more about roles in
the [community repository](https://github.com/open-telemetry/community/blob/main/guides/contributor/membership.md).

Thanks to all the people who already contributed!

<a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/graphs/contributors">
  <img src="https://contributors-img.web.app/image?repo=open-telemetry/opentelemetry-java-instrumentation" />
</a>

[config-agent]: https://opentelemetry.io/docs/zero-code/java/agent/configuration/

[config-sdk]: https://opentelemetry.io/docs/languages/java/configuration/

[manual]: https://opentelemetry.io/docs/languages/java/instrumentation/#manual-instrumentation

[suppress]: https://opentelemetry.io/docs/zero-code/java/agent/disable/
