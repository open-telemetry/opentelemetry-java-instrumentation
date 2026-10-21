/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.nio.ByteBuffer;
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

  @ParameterizedTest
  @MethodSource("serverErrorLines")
  void extractsFirstTokenFromByteBuffer(String serverErrorLine, String expected) {
    ByteBuffer errorLine =
        serverErrorLine == null ? null : ByteBuffer.wrap(serverErrorLine.getBytes(UTF_8));
    assertThat(RedisErrorPrefix.extract(errorLine)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("byteBuffers")
  void extractsFromByteBufferRangeWithoutChangingItsState(ByteBuffer errorLine) {
    byte[] originalContents = contents(errorLine);
    errorLine.position(1);
    errorLine.mark();
    errorLine.limit(errorLine.capacity() - 1);

    assertThat(RedisErrorPrefix.extract(errorLine)).isEqualTo("échec中");
    assertThat(errorLine.position()).isEqualTo(1);
    assertThat(errorLine.limit()).isEqualTo(errorLine.capacity() - 1);
    assertThat(contents(errorLine)).containsExactly(originalContents);
    errorLine.reset();
    assertThat(errorLine.position()).isEqualTo(1);
  }

  private static Stream<Arguments> byteBuffers() {
    byte[] contents = "!  échec中 details?".getBytes(UTF_8);
    ByteBuffer heap = ByteBuffer.wrap(contents);
    ByteBuffer direct = ByteBuffer.allocateDirect(contents.length);
    direct.put(contents).flip();
    ByteBuffer readOnly = ByteBuffer.wrap(contents).asReadOnlyBuffer();
    ByteBuffer sliced = ByteBuffer.wrap(contents).slice();
    return Stream.of(
        argumentSet("heap", heap),
        argumentSet("direct", direct),
        argumentSet("read-only", readOnly),
        argumentSet("slice", sliced));
  }

  private static byte[] contents(ByteBuffer buffer) {
    ByteBuffer duplicate = buffer.duplicate();
    duplicate.clear();
    byte[] contents = new byte[duplicate.remaining()];
    duplicate.get(contents);
    return contents;
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
        argumentSet("Unicode text", "ERRé中 detail", "ERRé中"));
  }
}
