/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.cloud.gateway.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class GatewayRouteIdConvergerTest {
  @Test
  void preservesConfiguredRouteId() {
    // given
    String routeId = "orders-route";

    // when & then
    assertThat(GatewayRouteIdConverger.converge(routeId)).isEqualTo(routeId);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "8a3a3f95-4f33-456d-b168-a1013c44d3b2",
        "org.springframework.util.AlternativeJdkIdGenerator@1234abcd"
      })
  void rejectsInvalidRouteId(String routeId) {
    assertThat(GatewayRouteIdConverger.converge(routeId)).isNull();
  }

  @Test
  void preservesUuidLikeRouteId() {
    // given
    String routeId = "ffffffff-ffff-ffff-ffff-ffff";

    // when & then
    assertThat(GatewayRouteIdConverger.converge(routeId)).isEqualTo(routeId);
  }
}
