/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LettuceResponseTest {

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
