/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics.POOL_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DbConnectionPoolMetricsTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  @SuppressWarnings("deprecation")
  void poolNameOverridesAdditionalAttributes() {
    SdkMeterProvider meterProvider = SdkMeterProvider.builder().build();
    cleanup.deferCleanup(meterProvider);

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            meterProvider.get("test"), "argument-pool", Attributes.of(POOL_NAME, "attribute-pool"));

    assertThat(metrics.getAttributes().get(POOL_NAME)).isEqualTo("argument-pool");
  }
}
