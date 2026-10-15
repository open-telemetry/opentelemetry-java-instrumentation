/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CLIENT_CONNECTION_STATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DbConnectionPoolMetricsAssertionsTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void acceptsStablePoolAttributes() {
    assertions(Attributes.of(DB_NAMESPACE, "orders"))
        .withDatabaseAttributes(equalTo(DB_NAMESPACE, "orders"))
        .assertConnectionPoolEmitsMetrics();
  }

  @Test
  void rejectsMissingDatabaseAttributes() {
    DbConnectionPoolMetricsAssertions assertions =
        assertions(Attributes.empty()).withDatabaseAttributes(equalTo(DB_NAMESPACE, "orders"));

    assertThatThrownBy(assertions::assertConnectionPoolEmitsMetrics)
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void rejectsUnexpectedAttributes() {
    DbConnectionPoolMetricsAssertions assertions =
        assertions(Attributes.of(DB_NAMESPACE, "orders", stringKey("unexpected"), "value"))
            .withDatabaseAttributes(equalTo(DB_NAMESPACE, "orders"));

    assertThatThrownBy(assertions::assertConnectionPoolEmitsMetrics)
        .isInstanceOf(AssertionError.class);
  }

  private DbConnectionPoolMetricsAssertions assertions(Attributes databaseAttributes) {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(provider);
    LongUpDownCounter count =
        provider
            .get("pool-test")
            .upDownCounterBuilder("db.client.connection.count")
            .setUnit("{connection}")
            .setDescription(
                "The number of connections that are currently in state described by the state attribute.")
            .build();
    count.add(
        1,
        databaseAttributes.toBuilder()
            .put(DB_CLIENT_CONNECTION_POOL_NAME, "pool")
            .put(DB_CLIENT_CONNECTION_STATE, "idle")
            .build());
    count.add(
        1,
        databaseAttributes.toBuilder()
            .put(DB_CLIENT_CONNECTION_POOL_NAME, "pool")
            .put(DB_CLIENT_CONNECTION_STATE, "used")
            .build());

    InstrumentationExtension testing = mock(InstrumentationExtension.class);
    doAnswer(
            invocation -> {
              assertThat(invocation.<String>getArgument(0)).isEqualTo("pool-test");
              assertThat(invocation.<String>getArgument(1)).isEqualTo("db.client.connection.count");
              Consumer<ListAssert<MetricData>> assertion = invocation.getArgument(2);
              assertion.accept(assertThat(new ArrayList<>(reader.collectAllMetrics())));
              return null;
            })
        .when(testing)
        .waitAndAssertMetrics(anyString(), anyString(), any());

    return DbConnectionPoolMetricsAssertions.create(testing, "pool-test", "pool")
        .disableMinIdleConnections()
        .disableMaxIdleConnections()
        .disableMaxConnections()
        .disablePendingRequests()
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime();
  }
}
