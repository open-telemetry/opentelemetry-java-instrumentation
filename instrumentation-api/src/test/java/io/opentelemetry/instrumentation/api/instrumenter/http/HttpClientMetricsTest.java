/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.RequestListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.DoubleSummaryPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class HttpClientMetricsTest {

  @Test
  void collectsMetrics() {
    SdkMeterProvider meterProvider = SdkMeterProvider.builder().build();

    RequestListener listener = HttpClientMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put("http.method", "GET")
            .put("http.host", "host")
            .put("http.scheme", "https")
            .put("net.host.name", "localhost")
            .put("net.host.port", 1234)
            .put("rpc.service", "unused")
            .put("rpc.method", "unused")
            .build();

    // Currently ignored.
    Attributes responseAttributes =
        Attributes.builder()
            .put("http.flavor", "2.0")
            .put("http.server_name", "server")
            .put("http.status_code", 200)
            .build();

    Context context1 = listener.start(Context.current(), requestAttributes);

    Collection<MetricData> metrics = meterProvider.collectAllMetrics();
    assertThat(metrics).isEmpty();

    Context context2 = listener.start(Context.current(), requestAttributes);

    metrics = meterProvider.collectAllMetrics();
    assertThat(metrics).isEmpty();

    listener.end(context1, responseAttributes);

    metrics = meterProvider.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics)
        .anySatisfy(
            metric -> {
              assertThat(metric.getName()).isEqualTo("http.client.duration");
              assertThat(metric.getDoubleSummaryData().getPoints()).hasSize(1);
              DoubleSummaryPointData data =
                  metric.getDoubleSummaryData().getPoints().stream().findFirst().get();
              assertThat(data.getAttributes().asMap())
                  .containsOnly(
                      entry(stringKey("http.host"), "host"),
                      entry(stringKey("http.method"), "GET"),
                      entry(stringKey("http.scheme"), "https"),
                      entry(stringKey("net.host.name"), "localhost"),
                      entry(stringKey("net.host.port"), "1234"));
              assertThat(data.getPercentileValues()).isNotEmpty();
            });

    listener.end(context2, responseAttributes);

    metrics = meterProvider.collectAllMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics)
        .anySatisfy(
            metric -> {
              assertThat(metric.getName()).isEqualTo("http.client.duration");
              assertThat(metric.getDoubleSummaryData().getPoints()).hasSize(1);
              DoubleSummaryPointData data =
                  metric.getDoubleSummaryData().getPoints().stream().findFirst().get();
              assertThat(data.getPercentileValues()).isNotEmpty();
            });
  }
}
