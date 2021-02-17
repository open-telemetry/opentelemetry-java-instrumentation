/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;

/**
 * This interface defines a common set of operations for interaction with OpenTelemetry SDK and
 * traces & metrics exporters.
 *
 * @see LibraryTestRunner
 * @see AgentTestRunner
 */
public interface InstrumentationTestRunner {
  void beforeTestClass();

  void afterTestClass();

  void clearAllExportedData();

  List<SpanData> getExportedSpans();

  List<MetricData> getExportedMetrics();

  boolean forceFlushCalled();
}
