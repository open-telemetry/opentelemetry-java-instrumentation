/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OperationMetricsUtilTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void noWarning() {
    AtomicBoolean warning = new AtomicBoolean(false);
    AtomicBoolean factoryInvoked = new AtomicBoolean(false);
    OperationMetrics operationMetrics =
        OperationMetricsUtil.create(
            "test metrics",
            meter -> {
              factoryInvoked.set(true);
              return OperationMetricsUtil.NOOP_OPERATION_LISTENER;
            },
            (s, doubleHistogramBuilder) -> warning.set(true));
    operationMetrics.create(testing.getOpenTelemetry().getMeter("test"));

    assertThat(warning).isFalse();
    assertThat(factoryInvoked).isTrue();
  }

  @Test
  void noWarningWithNoopMetrics() {
    AtomicBoolean warning = new AtomicBoolean(false);
    AtomicBoolean factoryInvoked = new AtomicBoolean(false);
    OperationMetrics operationMetrics =
        OperationMetricsUtil.create(
            "test metrics",
            meter -> {
              factoryInvoked.set(true);
              return OperationMetricsUtil.NOOP_OPERATION_LISTENER;
            },
            (s, doubleHistogramBuilder) -> warning.set(true));
    OperationListener listener = operationMetrics.create(MeterProvider.noop().get("test"));

    assertThat(listener).isSameAs(OperationMetricsUtil.NOOP_OPERATION_LISTENER);
    assertThat(warning).isFalse();
    assertThat(factoryInvoked).isFalse();
  }

  @Test
  void noMetricsWithSdkWithoutReaders() {
    AtomicBoolean factoryInvoked = new AtomicBoolean(false);
    OperationMetrics operationMetrics =
        OperationMetricsUtil.create(
            "test metrics",
            meter -> {
              factoryInvoked.set(true);
              return OperationMetricsUtil.NOOP_OPERATION_LISTENER;
            });
    try (SdkMeterProvider meterProvider = SdkMeterProvider.builder().build()) {
      assertThat(operationMetrics.create(meterProvider.get("test")))
          .isSameAs(OperationMetricsUtil.NOOP_OPERATION_LISTENER);
    }
    assertThat(factoryInvoked).isFalse();
  }

  @Test
  void noopHistogramDoesNotDisableCustomMeter() {
    AtomicBoolean factoryInvoked = new AtomicBoolean(false);
    Meter customMeter = mock(Meter.class);
    when(customMeter.histogramBuilder("compatibility-test"))
        .thenReturn(MeterProvider.noop().get("test").histogramBuilder("compatibility-test"));
    OperationMetrics operationMetrics =
        OperationMetricsUtil.create(
            "test metrics",
            meter -> {
              factoryInvoked.set(true);
              return OperationMetricsUtil.NOOP_OPERATION_LISTENER;
            });

    operationMetrics.create(customMeter);

    assertThat(factoryInvoked).isTrue();
  }

  @Test
  void warning() {
    AtomicBoolean warning = new AtomicBoolean(false);
    OperationMetrics operationMetrics =
        OperationMetricsUtil.create(
            "test metrics", meter -> null, (s, doubleHistogramBuilder) -> warning.set(true));
    Meter defaultMeter = MeterProvider.noop().get("test");
    Meter meter =
        (Meter)
            Proxy.newProxyInstance(
                Meter.class.getClassLoader(),
                new Class<?>[] {Meter.class},
                (proxy, method, args) -> {
                  if ("histogramBuilder".equals(method.getName())) {
                    // proxy the histogram builder so that the builder instance does not implement
                    // ExtendedDoubleHistogramBuilder which will trigger the warning
                    return proxyDoubleHistogramBuilder(defaultMeter);
                  }
                  return method.invoke(defaultMeter, args);
                });
    operationMetrics.create(meter);

    assertThat(warning).isTrue();
  }

  private static DoubleHistogramBuilder proxyDoubleHistogramBuilder(Meter meter) {
    return (DoubleHistogramBuilder)
        Proxy.newProxyInstance(
            DoubleHistogramBuilder.class.getClassLoader(),
            new Class<?>[] {DoubleHistogramBuilder.class},
            (proxy1, method1, args1) -> meter.histogramBuilder((String) args1[0]));
  }
}
