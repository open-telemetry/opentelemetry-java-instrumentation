/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.dbinfo;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import org.junit.jupiter.api.Test;

class DbInfoTest {

  @Test
  void copyPreservesLegacyEndpointAndConfiguredTarget() {
    DbServerTarget target = DbServerTarget.create("h1:5432,h2:5433", null);
    DbInfo info =
        DbInfo.builder()
            .legacyServerAddress("localhost")
            .legacyServerPort(5432)
            .configuredServerTarget(target)
            .build();

    DbInfo copy = info.toBuilder().build();

    assertThat(copy).isEqualTo(info).hasSameHashCodeAs(info);
    assertThat(copy.getConfiguredServerTarget()).isSameAs(target);
    assertThat(copy.getHost()).isEqualTo("localhost");
    assertThat(copy.getPort()).isEqualTo(5432);
    assertThat(copy.toBuilder().configuredServerTarget(null).build().getConfiguredServerTarget())
        .isNull();
  }

  @Test
  void independentlyBuiltTargetsHaveValueEquality() {
    DbInfo info =
        DbInfo.builder()
            .configuredServerTarget(DbServerTarget.create("h1:5432,h2:5433", null))
            .build();

    assertThat(
            DbInfo.builder()
                .configuredServerTarget(DbServerTarget.create("h1:5432,h2:5433", null))
                .build())
        .isEqualTo(info)
        .hasSameHashCodeAs(info);
  }
}
