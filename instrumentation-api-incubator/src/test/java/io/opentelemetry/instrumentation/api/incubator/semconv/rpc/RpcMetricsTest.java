/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RpcMetricsTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void customBucketBoundariesAreCopied(boolean client) {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(provider);

    List<Double> boundaries = new ArrayList<>(asList(0.1, 0.2, 0.3));
    OperationMetrics metrics =
        client ? RpcClientMetrics.get(boundaries) : RpcServerMetrics.get(boundaries);
    boundaries.clear();
    OperationListener listener = metrics.create(provider.get("test"));
    Context context = listener.onStart(Context.root(), Attributes.empty(), 0);
    listener.onEnd(context, Attributes.empty(), MILLISECONDS.toNanos(150));

    assertThat(reader.collectAllMetrics())
        .singleElement()
        .satisfies(
            metric ->
                assertThat(metric)
                    .hasName(client ? "rpc.client.call.duration" : "rpc.server.call.duration")
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasSum(0.15)
                                        .hasBucketBoundaries(0.1, 0.2, 0.3)
                                        .hasAttributes(Attributes.empty()))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void missingStateDoesNotRecordMetrics(boolean client) {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(provider);
    OperationListener listener =
        (client ? RpcClientMetrics.get() : RpcServerMetrics.get()).create(provider.get("test"));

    listener.onEnd(Context.root(), Attributes.empty(), MILLISECONDS.toNanos(150));

    assertThat(reader.collectAllMetrics()).isEmpty();
  }
}
