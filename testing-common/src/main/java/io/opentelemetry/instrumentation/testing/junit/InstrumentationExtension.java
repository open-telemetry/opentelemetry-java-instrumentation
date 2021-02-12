/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public abstract class InstrumentationExtension
    implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {
  private final InstrumentationTestRunner testRunner;

  protected InstrumentationExtension(InstrumentationTestRunner testRunner) {
    this.testRunner = testRunner;
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    testRunner.beforeTestClass();
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    testRunner.clearAllExportedData();
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) {
    testRunner.beforeTestClass();
  }

  public List<SpanData> spans() {
    return testRunner.getExportedSpans();
  }

  public List<MetricData> metrics() {
    return testRunner.getExportedMetrics();
  }
}
