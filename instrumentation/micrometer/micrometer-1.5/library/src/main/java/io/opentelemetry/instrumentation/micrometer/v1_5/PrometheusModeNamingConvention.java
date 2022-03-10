/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import javax.annotation.Nullable;

enum PrometheusModeNamingConvention implements NamingConvention {
  INSTANCE;

  @Override
  public String name(String name, Meter.Type type, @Nullable String baseUnit) {
    if (type == Meter.Type.COUNTER
        || type == Meter.Type.DISTRIBUTION_SUMMARY
        || type == Meter.Type.GAUGE) {
      if (baseUnit != null && !name.endsWith("." + baseUnit)) {
        name = name + "." + baseUnit;
      }
    }

    if (type == Meter.Type.LONG_TASK_TIMER || type == Meter.Type.TIMER) {
      if (!name.endsWith(".seconds")) {
        name = name + ".seconds";
      }
    }

    return name;
  }
}
