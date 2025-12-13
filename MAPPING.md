# Declarative Config to System Property Mapping

This table documents the conversion from system properties (upstream/main) to declarative config paths (HEAD).

| Declarative Config Path (HEAD) | System Property (upstream/main) |
|-------------------------------|--------------------------------|
| `general`, `db`, `statement_sanitizer`, `enabled` | `otel.instrumentation.common.db-statement-sanitizer.enabled` |
| `general`, `http`, `client`, `emit_experimental_telemetry` | `otel.instrumentation.http.client.emit-experimental-telemetry` |
| `general`, `http`, `client`, `redact_query_parameters/development` | `otel.instrumentation.http.client.experimental.redact-query-parameters` |
| `general`, `http`, `client`, `request_captured_headers` | `otel.instrumentation.http.client.capture-request-headers` |
| `general`, `http`, `client`, `response_captured_headers` | `otel.instrumentation.http.client.capture-response-headers` |
| `general`, `http`, `known_methods` | `otel.instrumentation.http.known-methods` |
| `general`, `http`, `server`, `emit_experimental_telemetry` | `otel.instrumentation.http.server.emit-experimental-telemetry` |
| `general`, `http`, `server`, `request_captured_headers` | `otel.instrumentation.http.server.capture-request-headers` |
| `general`, `http`, `server`, `response_captured_headers` | `otel.instrumentation.http.server.capture-response-headers` |
| `general`, `peer`, `service_mapping` | `otel.instrumentation.common.peer-service-mapping` |
| `java`, `agent`, `indy/development` | `otel.javaagent.experimental.indy` |
| `java`, `apache_elasticjob`, `experimental_span_attributes` | `otel.instrumentation.apache-elasticjob.experimental-span-attributes` |
| `java`, `aws_lambda`, `flush_timeout` | `otel.instrumentation.aws-lambda.flush-timeout` |
| `java`, `common`, `controller_telemetry/development`, `enabled` | `otel.instrumentation.common.experimental.controller-telemetry.enabled` |
| `java`, `common`, `db`, `sqlcommenter/development` | `otel.instrumentation.common.experimental.db-sqlcommenter.enabled` |
| `java`, `common`, `db`, `statement_sanitizer`, `enabled` | `otel.instrumentation.common.db-statement-sanitizer.enabled` |
| `java`, `common`, `default_enabled` | `otel.instrumentation.common.default-enabled` |
| `java`, `common`, `enduser`, `id`, `enabled` | `otel.instrumentation.common.enduser.id.enabled` |
| `java`, `common`, `enduser`, `role`, `enabled` | `otel.instrumentation.common.enduser.role.enabled` |
| `java`, `common`, `enduser`, `scope`, `enabled` | `otel.instrumentation.common.enduser.scope.enabled` |
| `java`, `common`, `logging`, `span_id` | `otel.instrumentation.common.logging.span-id` |
| `java`, `common`, `logging`, `trace_flags` | `otel.instrumentation.common.logging.trace-flags` |
| `java`, `common`, `logging`, `trace_id` | `otel.instrumentation.common.logging.trace-id` |
| `java`, `common`, `view_telemetry/development`, `enabled` | `otel.instrumentation.common.experimental.view-telemetry.enabled` |
| `java`, `elasticsearch`, `capture_search_query` | `otel.instrumentation.elasticsearch.capture-search-query` |
| `java`, `executors`, `include` | `otel.instrumentation.executors.include` |
| `java`, `executors`, `include_all` | `otel.instrumentation.executors.include-all` |
| `java`, `external_annotations`, `exclude_methods` | `otel.instrumentation.external-annotations.exclude-methods` |
| `java`, `external_annotations`, `include` | `otel.instrumentation.external-annotations.include` |
| `java`, `genai`, `capture_message_content` | `otel.instrumentation.genai.capture-message-content` |
| `java`, `graphql`, `add_operation_name_to_span_name`, `enabled` | `otel.instrumentation.graphql.add-operation-name-to-span-name.enabled` |
| `java`, `graphql`, `capture_query` | `otel.instrumentation.graphql.capture-query` |
| `java`, `graphql`, `query_sanitizer`, `enabled` | `otel.instrumentation.graphql.query-sanitizer.enabled` |
| `java`, `grpc`, `capture_metadata`, `client`, `request` | `otel.instrumentation.grpc.capture-metadata.client.request` |
| `java`, `grpc`, `capture_metadata`, `server`, `request` | `otel.instrumentation.grpc.capture-metadata.server.request` |
| `java`, `grpc`, `emit_message_events` | `otel.instrumentation.grpc.emit-message-events` |
| `java`, `grpc`, `experimental_span_attributes` | `otel.instrumentation.grpc.experimental-span-attributes` |
| `java`, `guava`, `experimental_span_attributes` | `otel.instrumentation.guava.experimental-span-attributes` |
| `java`, `hibernate`, `experimental_span_attributes` | `otel.instrumentation.hibernate.experimental-span-attributes` |
| `java`, `http`, `client`, `emit_experimental_telemetry` | `otel.instrumentation.http.client.emit-experimental-telemetry` |
| `java`, `http`, `client`, `emit_experimental_telemetry` | `otel.instrumentation.http.client.emit-experimental-telemetry` |
| `java`, `http`, `known_methods` | `otel.instrumentation.http.known-methods` |
| `java`, `hystrix`, `experimental_span_attributes` | `otel.instrumentation.hystrix.experimental-span-attributes` |
| `java`, `jaxrs`, `experimental_span_attributes` | `otel.instrumentation.jaxrs.experimental-span-attributes` |
| `java`, `jdbc`, `datasource`, `enabled` | `otel.instrumentation.jdbc.datasource.enabled` |
| `java`, `jdbc`, `experimental`, `capture_query_parameters` | `otel.instrumentation.jdbc.experimental.capture-query-parameters` |
| `java`, `jdbc`, `experimental`, `sqlcommenter`, `enabled` | `otel.instrumentation.jdbc.experimental.sqlcommenter.enabled` |
| `java`, `jdbc`, `experimental`, `transaction`, `enabled` | `otel.instrumentation.jdbc.experimental.transaction.enabled` |
| `java`, `jdbc`, `sqlcommenter`, `enabled` | `otel.instrumentation.jdbc.experimental.sqlcommenter.enabled` |
| `java`, `jdbc`, `statement_sanitizer`, `enabled` | `otel.instrumentation.jdbc.statement-sanitizer.enabled` |
| `java`, `jdbc`, `transaction`, `enabled` | `otel.instrumentation.jdbc.experimental.transaction.enabled` |
| `java`, `jmx`, `config` | `otel.jmx.config` |
| `java`, `jmx`, `discovery_delay` | `otel.jmx.discovery.delay` |
| `java`, `jmx`, `enabled` | `otel.jmx.enabled` |
| `java`, `jmx`, `target_system` | `otel.jmx.target.system` |
| `java`, `kafka`, `experimental_span_attributes` | `otel.instrumentation.kafka.experimental-span-attributes` |
| `java`, `kafka`, `producer_propagation`, `enabled` | `otel.instrumentation.kafka.producer-propagation.enabled` |
| `java`, `log4j-appender`, `experimental`, `capture_code_attributes` | `otel.instrumentation.log4j-appender.experimental.capture-code-attributes` |
| `java`, `log4j-appender`, `experimental`, `capture_event_name` | `otel.instrumentation.log4j-appender.experimental.capture-event-name` |
| `java`, `log4j-appender`, `experimental`, `capture_map_message_attributes` | `otel.instrumentation.log4j-appender.experimental.capture-map-message-attributes` |
| `java`, `log4j-appender`, `experimental`, `capture_marker_attribute` | `otel.instrumentation.log4j-appender.experimental.capture-marker-attribute` |
| `java`, `log4j-appender`, `experimental`, `capture_mdc_attributes` | `otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes` |
| `java`, `log4j_appender`, `experimental_log_attributes` | `otel.instrumentation.log4j-appender.experimental-log-attributes` |
| `java`, `logback_appender`, `experimental`, `capture_arguments` | `otel.instrumentation.logback-appender.experimental.capture-arguments` |
| `java`, `logback_appender`, `experimental`, `capture_code_attributes` | `otel.instrumentation.logback-appender.experimental.capture-code-attributes` |
| `java`, `logback_appender`, `experimental`, `capture_event_name` | `otel.instrumentation.logback-appender.experimental.capture-event-name` |
| `java`, `logback_appender`, `experimental`, `capture_key_value_pair_attributes` | `otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes` |
| `java`, `logback_appender`, `experimental`, `capture_logger_context_attributes` | `otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes` |
| `java`, `logback_appender`, `experimental`, `capture_logstash_marker_attributes` | `otel.instrumentation.logback-appender.experimental.capture-logstash-marker-attributes` |
| `java`, `logback_appender`, `experimental`, `capture_logstash_structured_arguments` | `otel.instrumentation.logback-appender.experimental.capture-logstash-structured-arguments` |
| `java`, `logback_appender`, `experimental`, `capture_marker_attribute` | `otel.instrumentation.logback-appender.experimental.capture-marker-attribute` |
| `java`, `logback_appender`, `experimental`, `capture_mdc_attributes` | `otel.instrumentation.logback-appender.experimental.capture-mdc-attributes` |
| `java`, `logback_appender`, `experimental`, `capture_template` | `otel.instrumentation.logback-appender.experimental.capture-template` |
| `java`, `logback_appender`, `experimental_log_attributes` | `otel.instrumentation.logback-appender.experimental-log-attributes` |
| `java`, `logback_mdc`, `add_baggage` | `otel.instrumentation.logback-mdc.add-baggage` |
| `java`, `messaging`, `capture_headers/development` | `otel.instrumentation.messaging.experimental.capture-headers` |
| `java`, `messaging`, `receive_telemetry/development`, `enabled` | `otel.instrumentation.messaging.experimental.receive-telemetry.enabled` |
| `java`, `methods`, `include` | `otel.instrumentation.methods.include` |
| `java`, `micrometer`, `base_time_unit` | `otel.instrumentation.micrometer.base-time-unit` |
| `java`, `micrometer`, `histogram_gauges`, `enabled` | `otel.instrumentation.micrometer.histogram-gauges.enabled` |
| `java`, `micrometer`, `prometheus_mode`, `enabled` | `otel.instrumentation.micrometer.prometheus-mode.enabled` |
| `java`, `mongo`, `statement_sanitizer`, `enabled` | `otel.instrumentation.mongo.statement-sanitizer.enabled` |
| `java`, `netty`, `connection_telemetry`, `enabled` | `otel.instrumentation.netty.connection-telemetry.enabled` |
| `java`, `netty`, `ssl_telemetry`, `enabled` | `otel.instrumentation.netty.ssl-telemetry.enabled` |
| `java`, `opentelemetry-instrumentation-annotations`, `exclude_methods` | `otel.instrumentation.opentelemetry-instrumentation-annotations.exclude-methods` |
| `java`, `oshi`, `enabled` | `otel.instrumentation.oshi.enabled` |
| `java`, `powerjob`, `experimental_span_attributes` | `otel.instrumentation.powerjob.experimental-span-attributes` |
| `java`, `pulsar`, `experimental_span_attributes` | `otel.instrumentation.pulsar.experimental-span-attributes` |
| `java`, `quartz`, `experimental_span_attributes` | `otel.instrumentation.quartz.experimental-span-attributes` |
| `java`, `r2dbc`, `experimental`, `sqlcommenter`, `enabled` | `otel.instrumentation.r2dbc.experimental.sqlcommenter.enabled` |
| `java`, `r2dbc`, `statement_sanitizer`, `enabled` | `otel.instrumentation.r2dbc.statement-sanitizer.enabled` |
| `java`, `rabbitmq`, `experimental_span_attributes` | `otel.instrumentation.rabbitmq.experimental-span-attributes` |
| `java`, `reactor`, `experimental_span_attributes` | `otel.instrumentation.reactor.experimental-span-attributes` |
| `java`, `runtime_telemetry`, `capture_gc_cause` | `otel.instrumentation.runtime-telemetry.capture-gc-cause` |
| `java`, `runtime_telemetry`, `emit_experimental_telemetry` | `otel.instrumentation.runtime-telemetry.emit-experimental-telemetry` |
| `java`, `runtime_telemetry`, `enabled` | `otel.instrumentation.runtime-telemetry.enabled` |
| `java`, `rxjava`, `experimental_span_attributes` | `otel.instrumentation.rxjava.experimental-span-attributes` |
| `java`, `servlet`, `experimental`, `add_trace_id_request_attribute` | `otel.instrumentation.servlet.experimental.add-trace-id-request-attribute` |
| `java`, `servlet`, `experimental`, `capture_request_parameters` | `otel.instrumentation.servlet.experimental.capture-request-parameters` |
| `java`, `spring_batch`, `chunk/development`, `new_trace` | `otel.instrumentation.spring-batch.experimental.chunk.new-trace` |
| `java`, `spring_batch`, `item`, `enabled` | `otel.instrumentation.spring-batch.item.enabled` |
| `java`, `spring_scheduling`, `experimental_span_attributes` | `otel.instrumentation.spring-scheduling.experimental-span-attributes` |
| `java`, `spring_security`, `enduser`, `role`, `granted_authority_prefix` | `otel.instrumentation.spring-security.enduser.role.granted-authority-prefix` |
| `java`, `spring_security`, `enduser`, `scope`, `granted_authority_prefix` | `otel.instrumentation.spring-security.enduser.scope.granted-authority-prefix` |
| `java`, `spymemcached`, `experimental_span_attributes` | `otel.instrumentation.spymemcached.experimental-span-attributes` |
| `java`, `twilio`, `experimental_span_attributes` | `otel.instrumentation.twilio.experimental-span-attributes` |
| `java`, `xxl_job`, `experimental_span_attributes` | `otel.instrumentation.xxl-job.experimental-span-attributes` |
