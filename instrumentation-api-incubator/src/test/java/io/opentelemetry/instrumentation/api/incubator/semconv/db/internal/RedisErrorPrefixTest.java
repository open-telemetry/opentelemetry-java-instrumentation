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
import org.junit.jupiter.api.Test;
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
  @MethodSource("asciiServerErrorLines")
  void extractsAsciiPrefix(String serverErrorLine, String expected) {
    assertThat(RedisErrorPrefix.extractAscii(serverErrorLine)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("serverErrorLines")
  void extractsFirstTokenFromByteBuffer(String serverErrorLine, String expected) {
    ByteBuffer errorLine =
        serverErrorLine == null ? null : ByteBuffer.wrap(serverErrorLine.getBytes(UTF_8));
    assertThat(RedisErrorPrefix.extract(errorLine)).isEqualTo(expected);
  }

  @Test
  void extractsFromByteBufferViewsWithoutChangingState() {
    String errorLine = "excluded-before  ERRé中! detail excluded-after";
    byte[] contents = errorLine.getBytes(UTF_8);
    int position = "excluded-before".length();
    int limit = position + "  ERRé中!".getBytes(UTF_8).length;

    ByteBuffer heap = ByteBuffer.wrap(contents);
    assertExtractsWithoutChangingState(heap, position, limit);

    ByteBuffer direct = ByteBuffer.allocateDirect(contents.length);
    direct.put(contents).flip();
    assertExtractsWithoutChangingState(direct, position, limit);

    ByteBuffer readOnly = ByteBuffer.wrap(contents).asReadOnlyBuffer();
    assertExtractsWithoutChangingState(readOnly, position, limit);

    byte[] slicedContents = ("outside" + errorLine + "outside").getBytes(UTF_8);
    ByteBuffer parent = ByteBuffer.wrap(slicedContents);
    parent.position("outside".length());
    parent.limit("outside".length() + contents.length);
    ByteBuffer slice = parent.slice();
    assertExtractsWithoutChangingState(slice, position, limit);
  }

  @Test
  void handlesNullByteBuffer() {
    assertThat(RedisErrorPrefix.extract((ByteBuffer) null)).isNull();
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
        argumentSet("embedded carriage return", "Code\rsuffix detail", "Code\rsuffix"),
        argumentSet("embedded line feed", "Code\nsuffix detail", "Code\nsuffix"),
        argumentSet(
            "embedded CRLF after leading spaces",
            "  Custom.Code!\r\nsuffix details",
            "Custom.Code!\r\nsuffix"),
        argumentSet("CRLF-only prefix", "\r\n", "\r\n"),
        argumentSet("Unicode space", "ERR custom detail", "ERR custom"),
        argumentSet("leading non-breaking space", "\u00a0CODE detail", "\u00a0CODE"),
        argumentSet("Unicode text", "ERRé中 detail", "ERRé中"));
  }

  private static Stream<Arguments> asciiServerErrorLines() {
    return Stream.of(
        argumentSet("ordinary ASCII prefix", "  WRONGTYPE Operation against a key", "WRONGTYPE"),
        argumentSet(
            "ASCII punctuation and case", "err.custom-code:42! detail", "err.custom-code:42!"),
        argumentSet(
            "ASCII tabs and line breaks", "Code\tpart\r\nsuffix detail", "Code\tpart\r\nsuffix"),
        argumentSet(
            "long ASCII prefix",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ detail",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"),
        argumentSet("non-ASCII prefix", "éCODE detail", null),
        argumentSet("non-ASCII prefix after spaces", "   \u00a0CODE detail", null),
        argumentSet("non-ASCII details", "WRONGTYPE é中 details", "WRONGTYPE"),
        argumentSet("U+007F", "\u007fCODE details", "\u007fCODE"),
        argumentSet("U+0080", "\u0080CODE details", null),
        argumentSet("null", null, null),
        argumentSet("empty", "", null),
        argumentSet("spaces only", "   ", null));
  }

  private static void assertExtractsWithoutChangingState(
      ByteBuffer input, int position, int limit) {
    byte[] originalContents = contents(input);
    input.position(position);
    input.limit(limit);
    input.position(position);
    input.mark();

    assertThat(RedisErrorPrefix.extract(input)).isEqualTo("ERRé中!");
    assertThat(input.position()).isEqualTo(position);
    assertThat(input.limit()).isEqualTo(limit);
    input.reset();
    assertThat(input.position()).isEqualTo(position);
    assertThat(contents(input)).containsExactly(originalContents);
  }

  private static byte[] contents(ByteBuffer input) {
    ByteBuffer duplicate = input.duplicate();
    duplicate.clear();
    byte[] contents = new byte[duplicate.remaining()];
    duplicate.get(contents);
    return contents;
  }
}
