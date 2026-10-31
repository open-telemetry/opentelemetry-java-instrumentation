/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class LettuceResponseTest {

  @Test
  void extractsServerErrorPrefixWithoutChangingApplicationMessage() {
    StatusOutput<String, String> output = new StatusOutput<>(StringCodec.UTF8);
    output.setError(ByteBuffer.wrap("WRONGTYPE details".getBytes(UTF_8)));
    String applicationMessage = output.getError();

    assertThat(LettuceResponse.fromCommandOutput(output).getRedisErrorCode())
        .isEqualTo("WRONGTYPE");
    assertThat(output.getError()).isEqualTo(applicationMessage);
  }

  @Test
  void preservesLiteralStringVerbatim() {
    assertThat(LettuceResponse.fromErrorMessage("ￃﾩ details").getRedisErrorCode()).isEqualTo("ￃﾩ");
  }
}
