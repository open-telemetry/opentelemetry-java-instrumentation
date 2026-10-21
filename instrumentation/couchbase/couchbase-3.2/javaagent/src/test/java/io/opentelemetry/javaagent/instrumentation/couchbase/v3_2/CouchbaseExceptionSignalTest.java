/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static com.couchbase.client.java.ClusterOptions.clusterOptions;
import static com.couchbase.client.java.kv.GetOptions.getOptions;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeName;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.javaagent.instrumentation.couchbase.v3_2.ExperimentalTestHelper.EXPERIMENTAL_TELEMETRY;
import static io.opentelemetry.javaagent.instrumentation.couchbase.v3_2.ExperimentalTestHelper.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.couchbase.client.core.error.UnambiguousTimeoutException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.env.ClusterEnvironment;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SchemaUrls;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CouchbaseExceptionSignalTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void recordsFailedOperationWithConfiguredExceptionSignal() {
    assumeTrue(testLatestDeps());

    int port = PortUtils.findOpenPort();
    ClusterEnvironment environment = ClusterEnvironment.builder().build();
    cleanup.deferCleanup(environment::shutdown);
    Cluster cluster =
        Cluster.connect(
            "couchbase://127.0.0.1:" + port,
            clusterOptions("user", "password").environment(environment));
    cleanup.deferCleanup(cluster::disconnect);
    Collection collection = cluster.bucket("test").defaultCollection();

    Throwable error =
        testing.runWithSpan(
            "parent",
            () ->
                collection
                    .async()
                    .get("id", getOptions().timeout(Duration.ofMillis(100)))
                    .handle((result, failure) -> failure)
                    .join());
    assertThat(error)
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(UnambiguousTimeoutException.class);

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("get _default")
                        .hasKind(CLIENT)
                        .satisfies(hasScopeName("io.opentelemetry.couchbase-3.2"))
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(emitExceptionAsSpanEvents() ? error : null)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "couchbase"),
                            equalTo(DB_NAMESPACE, "test"),
                            equalTo(DB_COLLECTION_NAME, "_default"),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(ERROR_TYPE, UnambiguousTimeoutException.class.getName()),
                            equalTo(stringKey("db.couchbase.document_id"), experimental("id")),
                            equalTo(stringKey("db.couchbase.scope"), experimental("_default")),
                            satisfies(
                                longKey("db.couchbase.retries"),
                                val -> {
                                  if (EXPERIMENTAL_TELEMETRY) {
                                    val.isNotNegative();
                                  } else {
                                    val.isNull();
                                  }
                                }),
                            equalTo(stringKey("db.couchbase.service"), experimental("kv")),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, port))));

    if (emitExceptionAsLogs()) {
      SpanData couchbaseSpan =
          testing.spans().stream()
              .filter(span -> span.getKind() == CLIENT)
              .findFirst()
              .orElseThrow(() -> new AssertionError("Missing Couchbase span"));
      testing.waitAndAssertLogRecords(
          log ->
              log.hasSeverity(Severity.WARN)
                  .hasEventName("db.client.operation.exception")
                  .hasException(error)
                  .hasTotalAttributeCount(3)
                  .hasSpanContext(couchbaseSpan.getSpanContext())
                  .hasInstrumentationScope(
                      InstrumentationScopeInfo.builder("io.opentelemetry.couchbase-3.2")
                          .setSchemaUrl(SchemaUrls.V1_44_0)
                          .build()));
    } else {
      assertThat(testing.logRecords()).isEmpty();
    }
  }
}
