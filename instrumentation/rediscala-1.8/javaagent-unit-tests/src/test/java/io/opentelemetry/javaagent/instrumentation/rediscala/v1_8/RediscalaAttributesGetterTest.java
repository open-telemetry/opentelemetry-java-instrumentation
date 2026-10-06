/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RediscalaAttributesGetterTest {

  private static final String SELECTED_HOST = "selected-node";
  private static final int SELECTED_PORT = 6379;

  private final RediscalaAttributesGetter getter = new RediscalaAttributesGetter();

  @Test
  void requestWithoutTargetDoesNotUseSelectedAddressAsServer() {
    RediscalaRequest request = request(null, SELECTED_PORT);

    assertThat(getter.getServerAddress(request)).isEqualTo(null);
    assertThat(getter.getServerPort(request)).isEqualTo(null);
    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {6379, 6381})
  void requestWithTargetUsesConfiguredAddress(int selectedPort) {
    RedisServerTarget target = RedisServerTarget.ofHostAndPort("configured-node", 6380);
    RediscalaRequest request = request(target, selectedPort);

    assertThat(getter.getServerAddress(request)).isEqualTo("configured-node");
    assertThat(getter.getServerPort(request)).isEqualTo(6380);
    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  private static RediscalaRequest request(RedisServerTarget target, int selectedPort) {
    RediscalaRequest request = mock(RediscalaRequest.class);
    when(request.getHost()).thenReturn(SELECTED_HOST);
    when(request.getPort()).thenReturn(selectedPort);
    when(request.getServerTarget()).thenReturn(target);
    return request;
  }
}
