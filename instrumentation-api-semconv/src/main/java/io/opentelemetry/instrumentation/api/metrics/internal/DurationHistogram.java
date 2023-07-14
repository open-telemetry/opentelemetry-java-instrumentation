/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.metrics.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.context.Context;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A Histogram instrument that records durations.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ThreadSafe
public final class DurationHistogram {

  private final DoubleHistogram delegate;

  DurationHistogram(DoubleHistogram delegate) {
    this.delegate = delegate;
  }

  public void record(long value, TimeUnit unit) {
    delegate.record(DurationHistogramFactory.toUnit(value, unit));
  }

  public void record(long value, TimeUnit unit, Attributes attributes) {
    delegate.record(DurationHistogramFactory.toUnit(value, unit), attributes);
  }

  public void record(long value, TimeUnit unit, Attributes attributes, Context context) {
    delegate.record(DurationHistogramFactory.toUnit(value, unit), attributes, context);
  }
}
