/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RedisErrorPrefixTest {

  @ParameterizedTest
  @MethodSource("serverErrorLines")
  void extractsFirstToken(String serverErrorLine, String expected) {
    assertThat(RedisErrorPrefix.extract(serverErrorLine)).isEqualTo(expected);
  }

  private static Stream<Arguments> serverErrorLines() {
    return Stream.of(
        argumentSet("wrong type", "WRONGTYPE Operation against a key", "WRONGTYPE"),
        argumentSet("dedicated server code", "NOSCRIPT No matching script", "NOSCRIPT"),
        argumentSet("punctuation", "ERR.custom-code:42! detail", "ERR.custom-code:42!"),
        argumentSet("lowercase", "customError detail", "customError"),
        argumentSet(
            "long prefix",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ detail",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"),
        argumentSet("prefix only", "WRONGTYPE", "WRONGTYPE"),
        argumentSet("leading spaces", "   WRONGTYPE detail", "WRONGTYPE"),
        argumentSet("repeated separator spaces", "WRONGTYPE   detail", "WRONGTYPE"),
        argumentSet("trailing spaces", "WRONGTYPE   ", "WRONGTYPE"),
        argumentSet("null", null, null),
        argumentSet("empty", "", null),
        argumentSet("spaces only", "   ", null),
        argumentSet("embedded tab", "ERR\tcustom detail", "ERR\tcustom"),
        argumentSet("leading tab", "\tERR detail", "\tERR"),
        argumentSet("tab only", "\t", "\t"),
        argumentSet("spaces before tab", "  \tERR detail", "\tERR"),
        argumentSet("Unicode space", "ERR\u2003custom detail", "ERR\u2003custom"),
        argumentSet("Unicode text", "ERR\u00e9\u4e2d detail", "ERR\u00e9\u4e2d"));
  }
}
