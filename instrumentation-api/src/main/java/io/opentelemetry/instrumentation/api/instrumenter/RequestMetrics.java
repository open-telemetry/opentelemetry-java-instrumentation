/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.metrics.Meter;

/**
 * A factory for creating a {@link RequestListener} instance that records operation metrics.
 *
 * @deprecated Use {@link OperationMetrics} instead.
 */
@Deprecated
public interface RequestMetrics extends OperationMetrics {

  @Override
  RequestListener create(Meter meter);
}
