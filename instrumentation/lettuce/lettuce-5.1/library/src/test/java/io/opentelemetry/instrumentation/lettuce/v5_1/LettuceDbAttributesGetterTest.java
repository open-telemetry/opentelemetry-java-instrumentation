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
        new LettuceResponse(
            "WRONGTYPE Operation against a key holding the wrong kind of value",
            new IllegalStateException());

    assertThat(getter.getErrorType(null, response, response.getThrowable())).isEqualTo("WRONGTYPE");
  }

  @Test
  void ignoresClientErrorMessage() {
    LettuceResponse response = new LettuceResponse("Connection closed", null);

    assertThat(getter.getErrorType(null, response, null)).isNull();
  }
}
