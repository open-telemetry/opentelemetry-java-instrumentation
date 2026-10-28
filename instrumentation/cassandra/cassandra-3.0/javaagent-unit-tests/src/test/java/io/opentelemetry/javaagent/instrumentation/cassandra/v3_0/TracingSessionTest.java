/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_QUERY_IDEMPOTENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.CASSANDRA;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Configuration;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TracingSessionTest {

  private static InMemorySpanExporter exporter;
  private static SdkTracerProvider tracerProvider;
  private static OpenTelemetrySdk openTelemetry;

  @BeforeAll
  static void setUp() {
    GlobalOpenTelemetry.resetForTest();
    exporter = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    GlobalOpenTelemetry.set(openTelemetry);
  }

  @AfterAll
  static void tearDown() {
    openTelemetry.close();
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void failureWithoutCoordinatorUsesConfiguredTarget() {
    Cluster.Builder builder = mock(Cluster.Builder.class);
    Cluster configuredCluster = mock(Cluster.class);
    CassandraServerTarget.capture(builder, new Object[] {"LOCALHOST"});
    CassandraServerTarget.store(builder, configuredCluster, 4242);

    Configuration configuration = mock(Configuration.class);
    QueryOptions queryOptions = mock(QueryOptions.class);
    when(configuredCluster.getConfiguration()).thenReturn(configuration);
    when(configuration.getQueryOptions()).thenReturn(queryOptions);
    when(queryOptions.getConsistencyLevel()).thenReturn(ConsistencyLevel.LOCAL_ONE);
    when(queryOptions.getFetchSize()).thenReturn(5000);

    Session delegate = mock(Session.class);
    when(delegate.getCluster()).thenReturn(configuredCluster);
    RuntimeException failure = new RuntimeException("failed before execution");
    when(delegate.execute("invalid")).thenThrow(failure);
    Session session = new TracingSession(delegate);

    assertThatThrownBy(() -> session.execute("invalid")).isSameAs(failure);

    assertThat(exporter.getFinishedSpanItems())
        .satisfiesExactly(
            span ->
                assertThat(span)
                    .hasName("LOCALHOST:4242")
                    .hasKind(SpanKind.CLIENT)
                    .hasNoParent()
                    .hasStatus(StatusData.error())
                    .hasException(failure)
                    .hasAttributesSatisfyingExactly(
                        equalTo(SERVER_ADDRESS, "LOCALHOST"),
                        equalTo(SERVER_PORT, 4242L),
                        equalTo(DB_SYSTEM_NAME, CASSANDRA),
                        equalTo(DB_QUERY_TEXT, "invalid"),
                        equalTo(CASSANDRA_CONSISTENCY_LEVEL, "LOCAL_ONE"),
                        equalTo(CASSANDRA_QUERY_IDEMPOTENT, false),
                        equalTo(CASSANDRA_PAGE_SIZE, 5000),
                        equalTo(ERROR_TYPE, RuntimeException.class.getName())));
  }
}
