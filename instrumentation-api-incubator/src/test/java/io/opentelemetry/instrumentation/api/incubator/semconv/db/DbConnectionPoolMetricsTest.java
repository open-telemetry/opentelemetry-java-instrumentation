/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics.POOL_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.Test;

class DbConnectionPoolMetricsTest {

  @Test
  @SuppressWarnings("deprecation")
  void poolNameOverridesAdditionalAttributes() {
    SdkMeterProvider meterProvider = SdkMeterProvider.builder().build();

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            meterProvider.get("test"), "argument-pool", Attributes.of(POOL_NAME, "attribute-pool"));

    assertThat(metrics.getAttributes().get(POOL_NAME)).isEqualTo("argument-pool");
  }
}
