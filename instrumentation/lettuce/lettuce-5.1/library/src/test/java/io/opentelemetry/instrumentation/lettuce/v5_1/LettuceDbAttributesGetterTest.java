/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisCommandTimeoutException;
import org.junit.jupiter.api.Test;

class LettuceDbAttributesGetterTest {

  private final LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

  @Test
  void returnsRedisErrorPrefix() {
    LettuceResponse response =
        LettuceResponse.fromErrorMessage(
            "WRONGTYPE Operation against a key holding the wrong kind of value");

    assertThat(getter.getErrorType(null, response, null)).isEqualTo("WRONGTYPE");
  }

  @Test
  void fallsBackForMessageOnlyFailure() {
    LettuceResponse response = LettuceResponse.fromErrorMessage("Connection closed");

    assertThat(getter.getErrorType(null, response, null)).isEqualTo("_OTHER");
  }

  @Test
  void preservesExceptionFallback() {
    LettuceResponse response = LettuceResponse.fromErrorMessage("Connection closed");

    assertThat(
            getter.getErrorType(
                null, response, new RedisCommandTimeoutException("Connection closed")))
        .isNull();
  }

  @Test
  void ignoresSuccessfulResponse() {
    LettuceResponse response = LettuceResponse.fromErrorMessage(null);

    assertThat(getter.getErrorType(null, response, null)).isNull();
  }

  @Test
  void ignoresMissingResponse() {
    assertThat(getter.getErrorType(null, null, null)).isNull();
  }
}
