/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableMeasurement;

public interface ObservableMeasurementWrapper<T extends ObservableMeasurement> {

  T unwrap();
}
