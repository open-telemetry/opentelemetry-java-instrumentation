/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationLongGauge138;

final class ApplicationLongGauge140Incubator extends ApplicationLongGauge138
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongGauge {

  ApplicationLongGauge140Incubator(LongGauge agentLongGauge) {
    super(agentLongGauge);
  }
}
