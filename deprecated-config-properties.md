# Deprecated Configuration Properties

Inventory of configuration properties (system properties / env vars / declarative
YAML keys) currently marked as deprecated in the repository. Generated from
`main` (`upstream/main`). Each entry points to the source file that reads the
old name and emits a deprecation warning, or to the CHANGELOG entry announcing
the deprecation.

Legend:

- **Stable** property (no `experimental` in name): cannot be removed before 3.0.
- **Experimental** property (contains `experimental`): may be removed in the next release.

---

## Flat system properties / environment variables

### DB query sanitization

| Deprecated property | Replacement | Source |
| --- | --- | --- |
| `otel.instrumentation.common.db-statement-sanitizer.enabled` | `otel.instrumentation.common.db.query-sanitization.enabled` | [DbConfig.java](instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/DbConfig.java#L120-L124) |
| `otel.instrumentation.jdbc.statement-sanitizer.enabled` | `otel.instrumentation.jdbc.query-sanitization.enabled` | [DbConfig.java](instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/DbConfig.java#L33-L45) |
| `otel.instrumentation.mongo.statement-sanitizer.enabled` | `otel.instrumentation.mongo.query-sanitization.enabled` | [DbConfig.java](instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/DbConfig.java#L33-L45) |
| `otel.instrumentation.r2dbc.statement-sanitizer.enabled` | `otel.instrumentation.r2dbc.query-sanitization.enabled` | [DbConfig.java](instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/DbConfig.java#L33-L45) |
| `otel.instrumentation.graphql.query-sanitizer.enabled` | `otel.instrumentation.graphql.query-sanitization.enabled` | [GraphqlSingletons.java (v20)](instrumentation/graphql-java/graphql-java-20.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/graphql/v20_0/GraphqlSingletons.java#L95-L106), [v12](instrumentation/graphql-java/graphql-java-12.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/graphql/v12_0/GraphqlSingletons.java#L84-L95) |

### DB SQL commenter

| Deprecated property | Replacement | Source |
| --- | --- | --- |
| `otel.instrumentation.common.experimental.db-sqlcommenter.enabled` | `otel.instrumentation.common.db.experimental.sqlcommenter.enabled` | [DbConfig.java](instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/DbConfig.java#L87-L95), [OpenTelemetryDriver.java](instrumentation/jdbc/library/src/main/java/io/opentelemetry/instrumentation/jdbc/OpenTelemetryDriver.java#L90-L102) |

### GraphQL

| Deprecated property | Replacement | Source |
| --- | --- | --- |
| `otel.instrumentation.graphql.add-operation-name-to-span-name.enabled` | `otel.instrumentation.graphql.operation-name-in-span-name.enabled` | [GraphqlSingletons.java (v20)](instrumentation/graphql-java/graphql-java-20.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/graphql/v20_0/GraphqlSingletons.java#L72-L82), [v12](instrumentation/graphql-java/graphql-java-12.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/graphql/v12_0/GraphqlSingletons.java#L61-L71) |

### HTTP client

| Deprecated property | Replacement | Source |
| --- | --- | --- |
| `otel.instrumentation.http.client.experimental.redact-query-parameters` | `otel.instrumentation.sanitization.url.experimental.sensitive-query-parameters` | [CommonConfig.java](instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/CommonConfig.java#L82-L88) |

### Runtime telemetry

| Deprecated property | Replacement | Source |
| --- | --- | --- |
| `otel.instrumentation.runtime-telemetry-java17.enable-all` | `otel.instrumentation.runtime-telemetry.emit-experimental-jfr-metrics` + `otel.instrumentation.runtime-telemetry.experimental.prefer-jfr` | [Internal.java](instrumentation/runtime-telemetry/library/src/main/java/io/opentelemetry/instrumentation/runtimetelemetry/internal/Internal.java#L285-L289) |
| `otel.instrumentation.runtime-telemetry-java17.enabled` | `otel.instrumentation.runtime-telemetry.emit-experimental-jfr-metrics` | [Internal.java](instrumentation/runtime-telemetry/library/src/main/java/io/opentelemetry/instrumentation/runtimetelemetry/internal/Internal.java#L312-L315) |
| `otel.instrumentation.runtime-telemetry.emit-experimental-telemetry` | `otel.instrumentation.runtime-telemetry.emit-experimental-metrics` | [Internal.java](instrumentation/runtime-telemetry/library/src/main/java/io/opentelemetry/instrumentation/runtimetelemetry/internal/Internal.java#L300-L307), [L357-L360](instrumentation/runtime-telemetry/library/src/main/java/io/opentelemetry/instrumentation/runtimetelemetry/internal/Internal.java#L357-L360) |
| `otel.instrumentation.runtime-telemetry.capture-gc-cause` | (no replacement — GC cause will always be captured in 3.0) | [Internal.java](instrumentation/runtime-telemetry/library/src/main/java/io/opentelemetry/instrumentation/runtimetelemetry/internal/Internal.java#L374-L379) |
| `otel.instrumentation.runtime-telemetry.package-emitter.enabled` | `otel.instrumentation.runtime-telemetry.experimental.package-emitter.enabled` | [JarAnalyzerInstaller.java](instrumentation/runtime-telemetry/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/runtimetelemetry/JarAnalyzerInstaller.java#L40-L46) |
| `otel.instrumentation.runtime-telemetry.package-emitter.jars-per-second` | `otel.instrumentation.runtime-telemetry.experimental.package-emitter.jars-per-second` | [JarAnalyzerInstaller.java](instrumentation/runtime-telemetry/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/runtimetelemetry/JarAnalyzerInstaller.java#L63-L69) |

### Servlet

| Deprecated property | Replacement | Source |
| --- | --- | --- |
| `otel.instrumentation.servlet.experimental.add-trace-id-request-attribute` | `otel.instrumentation.servlet.experimental.trace-id-request-attribute.enabled` | [BaseServletHelper.java](instrumentation/servlet/servlet-common/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/servlet/BaseServletHelper.java#L72-L82) |
| `otel.instrumentation.servlet.capture-request-parameters` | `otel.instrumentation.servlet.experimental.capture-request-parameters` | CHANGELOG #17113 (renamed to experimental; handled via `SPECIAL_MAPPINGS` in `declarative-config-bridge`) |
| `otel.instrumentation.servlet.add-trace-id-request-attribute` | `otel.instrumentation.servlet.experimental.trace-id-request-attribute.enabled` | CHANGELOG #17113 |

### Spring

| Deprecated property | Replacement | Source |
| --- | --- | --- |
| `otel.instrumentation.spring-web.enabled` (for `WebApplicationContextInstrumentation`) | `otel.instrumentation.spring-webmvc.enabled` | CHANGELOG Version 2.27.0 ⚠️ Breaking — module was moved |

---

## Declarative YAML configuration keys

| Deprecated declarative key | Replacement | Source |
| --- | --- | --- |
| `instrumentation/development: java: common: database: statement_sanitizer: enabled` | `instrumentation/development: java: common: db: query_sanitization: enabled` | [DbConfig.java](instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/DbConfig.java#L110-L117) |
| `instrumentation/development: java: common: database: sqlcommenter/development: enabled` | `instrumentation/development: java: common: db: sqlcommenter/development: enabled` | [DbConfig.java](instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/DbConfig.java#L76-L86) |
| `<instrumentation>.statement_sanitizer.enabled` (per instrumentation, e.g. `jdbc`, `mongo`, `r2dbc`) | `<instrumentation>.query_sanitization.enabled` | [DbConfig.java](instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/DbConfig.java#L136-L150) |
| `graphql.query_sanitizer.enabled` | `graphql.query_sanitization.enabled` | [GraphqlSingletons.java (v20)](instrumentation/graphql-java/graphql-java-20.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/graphql/v20_0/GraphqlSingletons.java#L95-L106) |

---

## Deprecated library builder settings (non-property)

These are deprecated Java API settings on library builders (not flat properties),
included here because they control configuration.

| Deprecated setting | Replacement | Source |
| --- | --- | --- |
| `OpenTelemetryAppender` `captureEventName` (logback-appender-1.0) | (no replacement — event name capture will be removed) | [OpenTelemetryAppender.java](instrumentation/logback/logback-appender-1.0/library/src/main/java/io/opentelemetry/instrumentation/logback/appender/v1_0/OpenTelemetryAppender.java#L111-L114) |
| `OpenTelemetryAppender` `captureEventName` (log4j-appender-2.17) | (no replacement — event name capture will be removed) | [OpenTelemetryAppender.java](instrumentation/log4j/log4j-appender-2.17/library/src/main/java/io/opentelemetry/instrumentation/log4j/appender/v2_17/OpenTelemetryAppender.java#L210-L213) |

---

## Deprecated instrumentation modules (library artifacts)

These entire instrumentations are deprecated in favor of a unified module; their
per-module configuration properties are deprecated along with them.

| Deprecated module | Replacement |
| --- | --- |
| `runtime-telemetry-java8` | `runtime-telemetry` — [metadata.yaml](instrumentation/runtime-telemetry/runtime-telemetry-java8/metadata.yaml) |
| `runtime-telemetry-java17` | `runtime-telemetry` — [metadata.yaml](instrumentation/runtime-telemetry/runtime-telemetry-java17/metadata.yaml) |
| `aws-lambda-events-2.2` | `aws-lambda-events-3.11` — [metadata.yaml](instrumentation/aws-lambda/aws-lambda-events-2.2/metadata.yaml) |

---

## Notes

- Properties marked as deprecated emit a `WARN`-level log at startup when the
  deprecated name is detected (see
  [config-property-stability.md](.github/agents/knowledge/config-property-stability.md)).
- Some legacy flat properties (e.g. servlet `capture-request-parameters`) are
  still accepted without a runtime warning because the rename is handled via
  `SPECIAL_MAPPINGS` in
  [declarative-config-bridge](declarative-config-bridge/).
- The `ConfigPropertiesUtil` class itself is `@Deprecated` (library-mode flat
  property fallback). Call sites that still use it are marked with
  `@SuppressWarnings("deprecation") // using deprecated config property`. Those
  sites do not represent deprecated *properties*; they represent deprecated
  *API usage* and are intentionally retained until 3.0.
