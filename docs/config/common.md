# Common instrumentation configuration

Common settings that apply to multiple instrumentations at once.

## Peer service name

The [peer service name](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes)
is the name of a remote service to which a connection is made. It corresponds to `service.name` in
the [Resource](https://github.com/open-telemetry/opentelemetry-specification/tree/master/specification/resource/semantic_conventions#service)
for the local service.

| System property                                    | Environment variable                               | Description |
| -------------------------------------------------- | -------------------------------------------------- | ----------- |
| `otel.instrumentation.common.peer-service-mapping` | `OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING` | Used to specify a mapping from host names or IP addresses to peer services, as a comma-separated list of `<host_or_ip>=<user_assigned_name>` pairs. The peer service is added as an attribute to a span whose host or IP address match the mapping. For example, if set to `1.2.3.4=cats-service,dogs-abcdef123.serverlessapis.com=dogs-api`, requests to `1.2.3.4` will have a `peer.service` attribute of `cats-service` and requests to `dogs-abcdef123.serverlessapis.com` will have an attribute of `dogs-api`.

## DB statement sanitization

The agent sanitizes all database queries/statements before setting the `db.statement` semantic
attribute. All values (strings, numbers) in the query string are replaced with a question mark (`?`).

Examples:

* SQL query `SELECT a from b where password="secret"` will appear
  as `SELECT a from b where password=?` in the exported span;
* Redis command `HSET map password "secret"` will appear as `HSET map password ?` in the exported
  span.

This behavior is turned on by default for all database instrumentations. Use the following property
to disable it:

| System property                                              | Environment variable                                         | Description |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ----------- |
| `otel.instrumentation.common.db-statement-sanitizer.enabled` | `OTEL_INSTRUMENTATION_COMMON_DB_STATEMENT_SANITIZER_ENABLED` | Enables the DB statement sanitization. The default value is `true`.

## Capturing HTTP request and response headers

You can configure the agent to capture predefined HTTP headers as span attributes, according to the
[semantic convention](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-request-and-response-headers).
Use the following properties to define which HTTP headers you want to capture:

| System property                                                                 | Environment variable                                                            | Description |
| ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- | ----------- |
| `otel.instrumentation.common.experimental.capture-http-headers.client.request`  | `OTEL_INSTRUMENTATION_COMMON_EXPERIMENTAL_CAPTURE_HTTP_HEADERS_CLIENT_REQUEST`  | A comma-separated list of HTTP header names. HTTP client instrumentations will capture HTTP request header values for all configured header names.
| `otel.instrumentation.common.experimental.capture-http-headers.client.response` | `OTEL_INSTRUMENTATION_COMMON_EXPERIMENTAL_CAPTURE_HTTP_HEADERS_CLIENT_RESPONSE` | A comma-separated list of HTTP header names. HTTP client instrumentations will capture HTTP response header values for all configured header names.
| `otel.instrumentation.common.experimental.capture-http-headers.server.request`  | `OTEL_INSTRUMENTATION_COMMON_EXPERIMENTAL_CAPTURE_HTTP_HEADERS_SERVER_REQUEST`  | A comma-separated list of HTTP header names. HTTP server instrumentations will capture HTTP request header values for all configured header names.
| `otel.instrumentation.common.experimental.capture-http-headers.server.response` | `OTEL_INSTRUMENTATION_COMMON_EXPERIMENTAL_CAPTURE_HTTP_HEADERS_SERVER_RESPONSE` | A comma-separated list of HTTP header names. HTTP server instrumentations will capture HTTP response header values for all configured header names.

These configuration options are supported by all HTTP client and server instrumentations.

> **Note**: The property/environment variable names listed in the table are still experimental,
> and thus are subject to change.
