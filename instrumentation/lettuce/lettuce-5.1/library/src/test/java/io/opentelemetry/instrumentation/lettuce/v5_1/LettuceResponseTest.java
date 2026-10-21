/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.MultiOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LettuceResponseTest {

  @ParameterizedTest
  @ValueSource(strings = {"échec", "错误.Code!", "ￃﾩ"})
  void decodesServerErrorOutputWithoutChangingApplicationMessage(String prefix) {
    StatusOutput<String, String> output = new StatusOutput<>(StringCodec.UTF8);
    output.setError(ByteBuffer.wrap((prefix + " details").getBytes(UTF_8)));
    String applicationMessage = output.getError();

    assertThat(LettuceResponse.fromCommandOutput(output, false).getRedisErrorType())
        .isEqualTo(prefix);
    assertThat(output.getError()).isEqualTo(applicationMessage);
  }

  @ParameterizedTest
  @ValueSource(strings = {"échec", "错误.Code!", "ￃﾩ"})
  void decodesTransactionErrorWithoutChangingApplicationMessage(String prefix) {
    StatusOutput<String, String> output = new StatusOutput<>(StringCodec.UTF8);
    MultiOutput<String, String> multi = new MultiOutput<>(StringCodec.UTF8);
    multi.add(new Command<>(CommandType.EVAL, output));
    multi.multi(1);
    multi.setError(ByteBuffer.wrap((prefix + " details").getBytes(UTF_8)));
    String applicationMessage = output.getError();
    multi.complete(1);
    Throwable failure = multi.get().get(0);

    assertThat(
            LettuceResponse.fromTransactionErrorMessage(failure.getMessage()).getRedisErrorType())
        .isEqualTo(prefix);
    assertThat(output.getError()).isEqualTo(applicationMessage);
    assertThat(failure).hasMessage(applicationMessage);
  }

  @Test
  void preservesUndecodableErrorOutput() {
    StatusOutput<String, String> output = new StatusOutput<>(StringCodec.UTF8);
    output.setError(ByteBuffer.wrap(new byte[] {(byte) 0xff}));

    assertThat(LettuceResponse.fromCommandOutput(output, false).getRedisErrorType())
        .isEqualTo(output.getError());
  }

  @Test
  void preservesDecodedStringVerbatim() {
    assertThat(LettuceResponse.fromErrorMessage("ￃﾩ details").getRedisErrorType()).isEqualTo("ￃﾩ");
  }

  @Test
  void extractsFirstTokenFromRedisErrorReply() {
    assertThat(LettuceResponse.fromErrorMessage("WRONGTYPE Operation failed").getRedisErrorType())
        .isEqualTo("WRONGTYPE");
    assertThat(LettuceResponse.fromErrorMessage("NOSCRIPT No matching script").getRedisErrorType())
        .isEqualTo("NOSCRIPT");
    assertThat(LettuceResponse.fromErrorMessage("Custom.Code! details").getRedisErrorType())
        .isEqualTo("Custom.Code!");
    assertThat(LettuceResponse.fromErrorMessage("wrongtype details").getRedisErrorType())
        .isEqualTo("wrongtype");
    assertThat(
            LettuceResponse.fromErrorMessage(
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMN details")
                .getRedisErrorType())
        .isEqualTo("ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMN");
  }

  @Test
  void extractsPrefixOnlyReply() {
    assertThat(LettuceResponse.fromErrorMessage("WRONGTYPE").getRedisErrorType())
        .isEqualTo("WRONGTYPE");
  }

  @Test
  void skipsLeadingAsciiSpaces() {
    assertThat(LettuceResponse.fromErrorMessage("   ERR details").getRedisErrorType())
        .isEqualTo("ERR");
    assertThat(LettuceResponse.fromErrorMessage("   Custom.Code!").getRedisErrorType())
        .isEqualTo("Custom.Code!");
  }

  @Test
  void onlyAsciiSpaceSeparatesTokens() {
    assertThat(LettuceResponse.fromErrorMessage("ERR\tDETAIL message").getRedisErrorType())
        .isEqualTo("ERR\tDETAIL");
    assertThat(LettuceResponse.fromErrorMessage(" \tERR details").getRedisErrorType())
        .isEqualTo("\tERR");
    assertThat(LettuceResponse.fromErrorMessage("échec details").getRedisErrorType())
        .isEqualTo("échec");
  }

  @Test
  void returnsNullWhenReplyHasNoUsablePrefix() {
    assertThat(LettuceResponse.fromErrorMessage(null).getRedisErrorType()).isNull();
    assertThat(LettuceResponse.fromErrorMessage("").getRedisErrorType()).isNull();
    assertThat(LettuceResponse.fromErrorMessage("   ").getRedisErrorType()).isNull();
  }
}
