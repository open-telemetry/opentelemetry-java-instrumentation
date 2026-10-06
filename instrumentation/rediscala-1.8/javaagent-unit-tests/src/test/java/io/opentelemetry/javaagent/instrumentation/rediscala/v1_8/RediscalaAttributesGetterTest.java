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

class RediscalaAttributesGetterTest {

  private final RediscalaAttributesGetter getter = new RediscalaAttributesGetter();

  @Test
  void requestWithoutTargetDoesNotUseSelectedAddressAsServer() {
    RediscalaRequest request = request(null);

    assertThat(getter.getServerAddress(request)).isEqualTo(null);
    assertThat(getter.getServerPort(request)).isEqualTo(null);
    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @Test
  void requestWithTargetUsesConfiguredAddress() {
    RedisServerTarget target = RedisServerTarget.ofHostAndPort("configured-node", 6380);
    RediscalaRequest request = request(target);

    assertThat(getter.getServerAddress(request)).isEqualTo("configured-node");
    assertThat(getter.getServerPort(request)).isEqualTo(6380);
    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  private static RediscalaRequest request(RedisServerTarget target) {
    RediscalaRequest request = mock(RediscalaRequest.class);
    when(request.getServerTarget()).thenReturn(target);
    return request;
  }
}
