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
import redis.actors.ReplyErrorException;

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

  @Test
  void errorTypeUsesServerReplyPrefix() {
    RediscalaRequest request = request(null);
    String prefixOver64Characters =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLM";

    assertThat(getter.getErrorType(request, null, new ReplyErrorException("WRONGTYPE message")))
        .isEqualTo("WRONGTYPE");
    assertThat(
            getter.getErrorType(request, null, new ReplyErrorException("MOVED 123 127.0.0.1:6379")))
        .isEqualTo("MOVED");
    assertThat(getter.getErrorType(request, null, new ReplyErrorException("CUSTOMCODE message")))
        .isEqualTo("CUSTOMCODE");
    assertThat(getter.getErrorType(request, null, new ReplyErrorException("Custom.Code! details")))
        .isEqualTo("Custom.Code!");
    assertThat(getter.getErrorType(request, null, new ReplyErrorException("lowercase details")))
        .isEqualTo("lowercase");
    assertThat(getter.getErrorType(request, null, new ReplyErrorException("ÉRRÖR details")))
        .isEqualTo("ÉRRÖR");
    assertThat(
            getter.getErrorType(
                request, null, new ReplyErrorException(prefixOver64Characters + " details")))
        .isEqualTo(prefixOver64Characters);
    assertThat(getter.getErrorType(request, null, new ReplyErrorException("CUSTOMCODE")))
        .isEqualTo("CUSTOMCODE");
    assertThat(getter.getErrorType(request, null, new ReplyErrorException("Custom\tCode details")))
        .isEqualTo("Custom\tCode");
    assertThat(getter.getErrorType(request, null, new ReplyErrorException(" message")))
        .isEqualTo("message");
    assertThat(
            getter.getErrorType(request, null, new ReplyErrorException("   Custom.Code!  details")))
        .isEqualTo("Custom.Code!");
    assertThat(getter.getErrorType(request, null, new ReplyErrorException("   CUSTOMCODE")))
        .isEqualTo("CUSTOMCODE");
    assertThat(
            getter.getErrorType(request, null, new ReplyErrorException(" \tCustom.Code! details")))
        .isEqualTo("\tCustom.Code!");
    assertThat(getter.getErrorType(request, null, new ReplyErrorException(""))).isNull();
    assertThat(getter.getErrorType(request, null, new ReplyErrorException("   "))).isNull();
    assertThat(getter.getErrorType(request, null, new ReplyErrorException(null))).isNull();
    assertThat(getter.getErrorType(request, null, new IllegalStateException("WRONGTYPE message")))
        .isNull();
    assertThat(getter.getErrorType(request, null, null)).isNull();
  }

  private static RediscalaRequest request(RedisServerTarget target) {
    RediscalaRequest request = mock(RediscalaRequest.class);
    when(request.getServerTarget()).thenReturn(target);
    return request;
  }
}
