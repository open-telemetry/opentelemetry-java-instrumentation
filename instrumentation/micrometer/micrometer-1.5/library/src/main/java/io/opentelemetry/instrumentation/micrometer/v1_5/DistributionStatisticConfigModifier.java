/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

@SuppressWarnings("OtelCanIgnoreReturnValueSuggester")
enum DistributionStatisticConfigModifier {
  DISABLE_HISTOGRAM_GAUGES {
    @Override
    DistributionStatisticConfig modify(DistributionStatisticConfig config) {
      return DistributionStatisticConfig.builder()
          // disable all percentile and slo related options
          .percentilesHistogram(null)
          .percentiles((double[]) null)
          .serviceLevelObjectives((double[]) null)
          .percentilePrecision(null)
          // and keep the rest
          .minimumExpectedValue(config.getMinimumExpectedValueAsDouble())
          .maximumExpectedValue(config.getMaximumExpectedValueAsDouble())
          .expiry(config.getExpiry())
          .bufferLength(config.getBufferLength())
          .build();
    }
  },
  IDENTITY {
    @Override
    DistributionStatisticConfig modify(DistributionStatisticConfig config) {
      return config;
    }
  };

  abstract DistributionStatisticConfig modify(DistributionStatisticConfig config);
}
