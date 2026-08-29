/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;

class RediscalaAttributesGetterTest {

  private static final String SELECTED_HOST = "selected-node";
  private static final int SELECTED_PORT = 6379;

  private final RediscalaAttributesGetter getter = new RediscalaAttributesGetter();

  @Test
  void requestWithoutTargetUsesSelectedEndpointOnlyForLegacySemconv() {
    RediscalaRequest request = request(null);

    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? null : SELECTED_HOST);
    assertThat(getter.getServerPort(request))
        .isEqualTo(emitStableDatabaseSemconv() ? null : SELECTED_PORT);
  }

  @Test
  void requestWithTargetUsesConfiguredTargetOnlyForStableSemconv() {
    RedisServerTarget target = RedisServerTarget.ofHostAndPort("configured-node", 6380);
    RediscalaRequest request = request(target);

    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "configured-node" : SELECTED_HOST);
    assertThat(getter.getServerPort(request))
        .isEqualTo(emitStableDatabaseSemconv() ? 6380 : SELECTED_PORT);
  }

  private static RediscalaRequest request(RedisServerTarget target) {
    RediscalaRequest request = mock(RediscalaRequest.class);
    when(request.getHost()).thenReturn(SELECTED_HOST);
    when(request.getPort()).thenReturn(SELECTED_PORT);
    when(request.getServerTarget()).thenReturn(target);
    return request;
  }
}
