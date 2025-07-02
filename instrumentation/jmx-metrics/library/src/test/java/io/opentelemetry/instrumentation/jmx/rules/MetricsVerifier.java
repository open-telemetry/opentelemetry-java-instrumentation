/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.JmxAssertj.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.jmx.rules.assertions.MetricAssert;
import io.opentelemetry.proto.metrics.v1.Metric;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MetricsVerifier {

  private final Map<String, Consumer<Metric>> assertions = new HashMap<>();
  private boolean strictMode = true;

  private MetricsVerifier() {}

  /**
   * Create instance of MetricsVerifier configured to fail verification if any metric was not
   * verified because there is no assertion defined for it. This behavior can be changed by calling
   * {@link #disableStrictMode()} method.
   *
   * @return new instance of MetricsVerifier
   * @see #disableStrictMode()
   */
  public static MetricsVerifier create() {
    return new MetricsVerifier();
  }

  /**
   * Disable strict checks of metric assertions. It means that all metrics checks added after
   * calling this method will not enforce asserting all metric properties and will not detect
   * duplicate property assertions. Also, there will be no error reported if any of metrics was
   * skipped because no assertion was added for it.
   *
   * @return this
   * @see #verify(List)
   * @see #add(String, Consumer)
   */
  @CanIgnoreReturnValue
  public MetricsVerifier disableStrictMode() {
    strictMode = false;
    return this;
  }

  /**
   * Add assertion for given metric
   *
   * @param metricName name of metric to be verified by provided assertion
   * @param assertion an assertion to verify properties of the metric
   * @return this
   */
  @CanIgnoreReturnValue
  public MetricsVerifier add(String metricName, Consumer<MetricAssert> assertion) {
    if (assertions.containsKey(metricName)) {
      throw new IllegalArgumentException("Duplicate assertion for metric " + metricName);
    }
    assertions.put(
        metricName,
        metric -> {
          MetricAssert metricAssert = assertThat(metric);
          metricAssert.setStrict(strictMode);
          assertion.accept(metricAssert);
          metricAssert.strictCheck();
        });
    return this;
  }

  /**
   * Execute all defined assertions against provided list of metrics. Error is reported if any of
   * defined assertions failed. Error is also reported if any of expected metrics was not present in
   * the metrics list, unless strict mode is disabled with {@link #disableStrictMode()}.
   *
   * @param metrics list of metrics to be verified
   * @see #add(String, Consumer)
   * @see #disableStrictMode()
   */
  public void verify(List<Metric> metrics) {
    verifyAllExpectedMetricsWereReceived(metrics);

    Set<String> unverifiedMetrics = new HashSet<>();

    for (Metric metric : metrics) {
      String metricName = metric.getName();
      Consumer<Metric> assertion = assertions.get(metricName);

      if (assertion != null) {
        assertion.accept(metric);
      } else {
        unverifiedMetrics.add(metricName);
      }
    }

    if (strictMode && !unverifiedMetrics.isEmpty()) {
      fail("Metrics received but not verified because no assertion exists: " + unverifiedMetrics);
    }
  }

  private void verifyAllExpectedMetricsWereReceived(List<Metric> metrics) {
    Set<String> receivedMetricNames =
        metrics.stream().map(Metric::getName).collect(Collectors.toSet());
    Set<String> assertionNames = new HashSet<>(assertions.keySet());

    assertionNames.removeAll(receivedMetricNames);
    if (!assertionNames.isEmpty()) {
      fail(
          "Metrics expected but not received: "
              + assertionNames
              + "\nReceived only: "
              + receivedMetricNames);
    }
  }
}
