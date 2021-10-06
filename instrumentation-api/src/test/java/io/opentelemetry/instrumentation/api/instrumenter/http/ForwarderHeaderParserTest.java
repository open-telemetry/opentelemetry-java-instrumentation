/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ForwarderHeaderParserTest {

  @Test
  void extractForwarded() {
    assertThat(ForwarderHeaderParser.extractForwarded("for=1.1.1.1")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedIpv6() {
    assertThat(
            ForwarderHeaderParser.extractForwarded(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedWithPort() {
    assertThat(ForwarderHeaderParser.extractForwarded("for=\"1.1.1.1:2222\"")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedIpv6WithPort() {
    assertThat(
            ForwarderHeaderParser.extractForwarded(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedCaps() {
    assertThat(ForwarderHeaderParser.extractForwarded("For=1.1.1.1")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMalformed() {
    assertThat(ForwarderHeaderParser.extractForwarded("for=;for=1.1.1.1")).isNull();
  }

  @Test
  void extractForwardedEmpty() {
    assertThat(ForwarderHeaderParser.extractForwarded("")).isNull();
  }

  @Test
  void extractForwardedEmptyValue() {
    assertThat(ForwarderHeaderParser.extractForwarded("for=")).isNull();
  }

  @Test
  void extractForwardedEmptyValueWithSemicolon() {
    assertThat(ForwarderHeaderParser.extractForwarded("for=;")).isNull();
  }

  @Test
  void extractForwardedNoFor() {
    assertThat(ForwarderHeaderParser.extractForwarded("by=1.1.1.1;test=1.1.1.1")).isNull();
  }

  @Test
  void extractForwardedMultiple() {
    assertThat(ForwarderHeaderParser.extractForwarded("for=1.1.1.1;for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMultipleIpV6() {
    assertThat(
            ForwarderHeaderParser.extractForwarded(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedMultipleWithPort() {
    assertThat(ForwarderHeaderParser.extractForwarded("for=\"1.1.1.1:2222\";for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMultipleIpV6WithPort() {
    assertThat(
            ForwarderHeaderParser.extractForwarded(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedMixedSplitter() {
    assertThat(
            ForwarderHeaderParser.extractForwarded(
                "test=abcd; by=1.2.3.4, for=1.1.1.1;for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMixedSplitterIpv6() {
    assertThat(
            ForwarderHeaderParser.extractForwarded(
                "test=abcd; by=1.2.3.4, for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedMixedSplitterWithPort() {
    assertThat(
            ForwarderHeaderParser.extractForwarded(
                "test=abcd; by=1.2.3.4, for=\"1.1.1.1:2222\";for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMixedSplitterIpv6WithPort() {
    assertThat(
            ForwarderHeaderParser.extractForwarded(
                "test=abcd; by=1.2.3.4, for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedFor() {
    assertThat(ForwarderHeaderParser.extractForwardedFor("1.1.1.1")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedForIpv6() {
    assertThat(
            ForwarderHeaderParser.extractForwardedFor(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForIpv6Unquoted() {
    assertThat(
            ForwarderHeaderParser.extractForwardedFor("[1111:1111:1111:1111:1111:1111:1111:1111]"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForIpv6Unbracketed() {
    assertThat(ForwarderHeaderParser.extractForwardedFor("1111:1111:1111:1111:1111:1111:1111:1111"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForWithPort() {
    assertThat(ForwarderHeaderParser.extractForwardedFor("1.1.1.1:2222")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedForIpv6WithPort() {
    assertThat(
            ForwarderHeaderParser.extractForwardedFor(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForIpv6UnquotedWithPort() {
    assertThat(
            ForwarderHeaderParser.extractForwardedFor(
                "[1111:1111:1111:1111:1111:1111:1111:1111]:2222"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForEmpty() {
    assertThat(ForwarderHeaderParser.extractForwardedFor("")).isNull();
  }

  @Test
  void extractForwardedForMultiple() {
    assertThat(ForwarderHeaderParser.extractForwardedFor("1.1.1.1,1.2.3.4")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedForMultipleIpv6() {
    assertThat(
            ForwarderHeaderParser.extractForwardedFor(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]\",1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForMultipleIpv6Unquoted() {
    assertThat(
            ForwarderHeaderParser.extractForwardedFor(
                "[1111:1111:1111:1111:1111:1111:1111:1111],1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForMultipleIpv6Unbracketed() {
    assertThat(
            ForwarderHeaderParser.extractForwardedFor(
                "1111:1111:1111:1111:1111:1111:1111:1111,1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForMultipleWithPort() {
    assertThat(ForwarderHeaderParser.extractForwardedFor("1.1.1.1:2222,1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedForMultipleIpv6WithPort() {
    assertThat(
            ForwarderHeaderParser.extractForwardedFor(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\",1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForMultipleIpv6UnquotedWithPort() {
    assertThat(
            ForwarderHeaderParser.extractForwardedFor(
                "[1111:1111:1111:1111:1111:1111:1111:1111]:2222,1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }
}
