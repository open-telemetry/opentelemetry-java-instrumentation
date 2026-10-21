/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JedisServerErrorTest {

  @ParameterizedTest
  @MethodSource("errorReplies")
  void extractsErrorType(String reply, String expected) {
    assertThat(JedisServerError.errorType(reply)).isEqualTo(expected);
  }

  private static Stream<Arguments> errorReplies() {
    return Stream.of(
        argumentSet("standard", "WRONGTYPE Operation", "WRONGTYPE"),
        argumentSet("dedicated", "MOVED 42 127.0.0.1:6379", "MOVED"),
        argumentSet("punctuation", "Custom.Code! detail", "Custom.Code!"),
        argumentSet("lowercase", "lowercase message", "lowercase"),
        argumentSet(
            "long prefix",
            "VeryLongPrefix_0123456789_0123456789_0123456789_0123456789_0123456789 detail",
            "VeryLongPrefix_0123456789_0123456789_0123456789_0123456789_0123456789"),
        argumentSet("prefix only", "PrefixOnly", "PrefixOnly"),
        argumentSet("tabs are not separators", "Custom.Code!\tmessage", "Custom.Code!\tmessage"),
        argumentSet("leading spaces", "  Custom.Code! detail", "Custom.Code!"),
        argumentSet("empty", "", null),
        argumentSet("missing prefix", " ", null),
        argumentSet("empty RESP message", "\r\n", null),
        argumentSet("crlf", "Custom.Code!\r\nignored", "Custom.Code!"));
  }
}
