/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisNoScriptException;
import org.junit.jupiter.api.Test;

class LettuceDbAttributesGetterTest {

  private final LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

  @Test
  void ignoresClientErrorWithoutResponse() {
    assertThat(getter.getErrorType(null, null, new RedisCommandTimeoutException("timed out")))
        .isNull();
    assertThat(
            getter.getDbResponseStatusCode(
                null, null, new RedisCommandTimeoutException("timed out")))
        .isNull();
  }

  @Test
  void delegatesExceptionFallbackWhenReplyHasNoPrefix() {
    LettuceResponse response = LettuceResponse.fromErrorMessage("   ");

    assertThat(getter.getErrorType(null, response, null)).isEqualTo("_OTHER");
    assertThat(getter.getDbResponseStatusCode(null, response, null)).isNull();
    assertThat(getter.getErrorType(null, response, new RedisNoScriptException("missing script")))
        .isNull();
  }

  @Test
  void doesNotInventExceptionClassForUnknownFailure() {
    assertThat(getter.getErrorType(null, LettuceResponse.unknownFailure(), null))
        .isEqualTo("_OTHER");
    assertThat(getter.getDbResponseStatusCode(null, LettuceResponse.unknownFailure(), null))
        .isNull();
  }
}
