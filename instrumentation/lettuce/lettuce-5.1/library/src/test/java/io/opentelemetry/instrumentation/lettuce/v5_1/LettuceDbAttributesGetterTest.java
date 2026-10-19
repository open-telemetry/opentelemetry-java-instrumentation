/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static org.assertj.core.api.Assertions.assertThat;

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
  void ignoresClientErrorMessage() {
    LettuceResponse response = LettuceResponse.fromErrorMessage("Connection closed");

    assertThat(getter.getErrorType(null, response, null)).isNull();
  }
}
