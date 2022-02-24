/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ForwardedHeaderParserTest {

  @Test
  void extractProtoFromForwardedHeader() {
    assertThat(ForwardedHeaderParser.extractProtoFromForwardedHeader("for=1.1.1.1;proto=xyz"))
        .isEqualTo("xyz");
  }

  @Test
  void extractProtoFromForwardedHeaderWithTrailingSemicolon() {
    assertThat(ForwardedHeaderParser.extractProtoFromForwardedHeader("for=1.1.1.1;proto=xyz;"))
        .isEqualTo("xyz");
  }

  @Test
  void extractProtoFromForwardedHeaderWithTrailingComma() {
    assertThat(ForwardedHeaderParser.extractProtoFromForwardedHeader("for=1.1.1.1;proto=xyz,"))
        .isEqualTo("xyz");
  }

  @Test
  void extractProtoFromForwardedHeaderWithQuotes() {
    assertThat(ForwardedHeaderParser.extractProtoFromForwardedHeader("for=1.1.1.1;proto=\"xyz\""))
        .isEqualTo("xyz");
  }

  @Test
  void extractProtoFromForwardedHeaderWithQuotesAndTrailingSemicolon() {
    assertThat(ForwardedHeaderParser.extractProtoFromForwardedHeader("for=1.1.1.1;proto=\"xyz\";"))
        .isEqualTo("xyz");
  }

  @Test
  void extractProtoFromForwardedHeaderWithQuotesAndTrailingComma() {
    assertThat(ForwardedHeaderParser.extractProtoFromForwardedHeader("for=1.1.1.1;proto=\"xyz\","))
        .isEqualTo("xyz");
  }

  @Test
  void extractProtoFromForwardedProtoHeader() {
    assertThat(ForwardedHeaderParser.extractProtoFromForwardedProtoHeader("xyz")).isEqualTo("xyz");
  }

  @Test
  void extractProtoFromForwardedProtoHeaderWithQuotes() {
    assertThat(ForwardedHeaderParser.extractProtoFromForwardedProtoHeader("\"xyz\""))
        .isEqualTo("xyz");
  }

  @Test
  void extractClientIpFromForwardedHeader() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedHeader("for=1.1.1.1"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithIpv6() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedHeader(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithPort() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedHeader("for=\"1.1.1.1:2222\""))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithIpv6AndPort() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedHeader(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithCaps() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedHeader("For=1.1.1.1"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromMalformedForwardedHeader() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedHeader("for=;for=1.1.1.1"))
        .isNull();
  }

  @Test
  void extractClientIpFromEmptyForwardedHeader() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedHeader("")).isNull();
  }

  @Test
  void extractClientIpFromForwardedHeaderWithEmptyValue() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedHeader("for=")).isNull();
  }

  @Test
  void extractClientIpFromForwardedHeaderWithValueAndSemicolon() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedHeader("for=;")).isNull();
  }

  @Test
  void extractClientIpFromForwardedHeaderWithNoFor() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedHeader("by=1.1.1.1;test=1.1.1.1"))
        .isNull();
  }

  @Test
  void extractClientIpFromForwardedHeaderWithMultiple() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedHeader("for=1.1.1.1;for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithMultipleIpV6() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedHeader(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithMultipleAndPort() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedHeader(
                "for=\"1.1.1.1:2222\";for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithMultipleIpV6AndPort() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedHeader(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithMixedSplitter() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedHeader(
                "test=abcd; by=1.2.3.4, for=1.1.1.1;for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithMixedSplitterIpv6() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedHeader(
                "test=abcd; by=1.2.3.4, for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithMixedSplitterAndPort() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedHeader(
                "test=abcd; by=1.2.3.4, for=\"1.1.1.1:2222\";for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedHeaderWithMixedSplitterIpv6AndPort() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedHeader(
                "test=abcd; by=1.2.3.4, for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedForHeader() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedForHeader("1.1.1.1"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithIpv6() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithIpv6Unquoted() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "[1111:1111:1111:1111:1111:1111:1111:1111]"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithIpv6Unbracketed() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "1111:1111:1111:1111:1111:1111:1111:1111"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithPort() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedForHeader("1.1.1.1:2222"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithIpv6AndPort() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithIpv6UnquotedAndPort() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "[1111:1111:1111:1111:1111:1111:1111:1111]:2222"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromEmptyForwardedForHeader() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedForHeader("")).isNull();
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithMultiple() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedForHeader("1.1.1.1,1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithMultipleIpv6() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]\",1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithMultipleIpv6Unquoted() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "[1111:1111:1111:1111:1111:1111:1111:1111],1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithMultipleIpv6Unbracketed() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "1111:1111:1111:1111:1111:1111:1111:1111,1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithMultipleAndPort() {
    assertThat(ForwardedHeaderParser.extractClientIpFromForwardedForHeader("1.1.1.1:2222,1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithMultipleIpv6AndPort() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\",1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractClientIpFromForwardedForHeaderWithMultipleIpv6UnquotedAndPort() {
    assertThat(
            ForwardedHeaderParser.extractClientIpFromForwardedForHeader(
                "[1111:1111:1111:1111:1111:1111:1111:1111]:2222,1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }
}
