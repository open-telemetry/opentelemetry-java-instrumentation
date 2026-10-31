/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.HostPort;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MysqlHostParserTest {

  @ParameterizedTest
  @MethodSource("singleHosts")
  void parsesHostAndOptionalPort(String authority, String host, Integer port) {
    assertThat(MysqlHostParser.parseHostList(authority, true))
        .extracting(HostPort::host, HostPort::port)
        .containsExactly(tuple(host, port));
  }

  private static Stream<Arguments> singleHosts() {
    return Stream.of(
        argumentSet("ordinary host", "h1", "h1", null),
        argumentSet("ordinary host and port", "h1:1111", "h1", 1111),
        argumentSet("explicit default port", "h1:3306", "h1", 3306),
        argumentSet("ordinary IPv6", "[::1]:1111", "::1", 1111),
        argumentSet("unbracketed IPv6", "::1", "::1", null),
        argumentSet("ordinary user info", "user:example@h1:1111", "h1", 1111),
        argumentSet("equals in ordinary user info", "address=example@h1:1111", "h1", 1111),
        argumentSet("parentheses in ordinary user info", "us(er):ex(ample)@h1:1111", "h1", 1111),
        argumentSet("leading username parentheses", "(user):example@h1:1111", "h1", 1111),
        argumentSet(
            "leading username and unbalanced password parentheses",
            "(user):ex(ample@h1:1111",
            "h1",
            1111),
        argumentSet("address-like user info", "address=(user):example@h1:1111", "h1", 1111),
        argumentSet("unbalanced address-like user info", "address=ex(ample@h1:1111", "h1", 1111),
        argumentSet("unbalanced credential parentheses", "user:ex(ample@h1:1111", "h1", 1111),
        argumentSet("address host", "address=(host=h1)", "h1", null),
        argumentSet("address host and port", "address=(host=h1)(port=1111)", "h1", 1111),
        argumentSet(
            "address properties and whitespace",
            "address=(HOST= h1 )(port= 1111 )(user=other)(password=example@value)",
            "h1",
            1111),
        argumentSet("address IPv6", "address=(host=::1)(port=1111)", "::1", 1111),
        argumentSet(
            "at sign in address property",
            "address=(host=h1)(port=1111)(user=other@example)",
            "h1",
            1111),
        argumentSet("key-value host", "(host=h1)", "h1", null),
        argumentSet("key-value host and port", "(host=h1,port=1111)", "h1", 1111),
        argumentSet("reordered properties", "(port=1111,host=h1)", "h1", 1111),
        argumentSet("trimmed properties", " ( host = h1 , port = 1111 ) ", "h1", 1111),
        argumentSet("ASCII key whitespace", "(\t host \r =h1,port=1111)", "h1", 1111),
        argumentSet("empty properties", "(,,host=h1,=,,)", "h1", null),
        argumentSet("empty port", "(host=h1,port=)", "h1", null),
        argumentSet("port without a value", "(host=h1,port)", "h1", null),
        argumentSet("encoded properties", "(%48ost=h%31,port=%31%31%31%31)", "h1", 1111),
        argumentSet("key-value IPv6", "(host=::1,port=1111)", "::1", 1111),
        argumentSet("encoded IPv6 brackets", "(host=%5B::1%5D,port=1111)", "::1", 1111),
        argumentSet(
            "ignored properties",
            "(host=h1,user=other,password=example%40value,other=a(b)c,flag)",
            "h1",
            null));
  }

  @Test
  void parsesMixedHostSyntaxesInConfiguredOrder() {
    assertThat(
            MysqlHostParser.parseHostList(
                "h1:1111, (host=h2,port=2222), address=(host=::1)(user=other@example),h1:1111",
                true))
        .extracting(HostPort::host, HostPort::port)
        .containsExactly(
            tuple("h1", 1111), tuple("h2", 2222), tuple("::1", null), tuple("h1", 1111));
  }

  @Test
  void doesNotTakeAnOmittedPortFromTheNextHost() {
    assertThat(MysqlHostParser.parseHostList("(host=h1),address=(host=h2)(port=2222)", true))
        .extracting(HostPort::host, HostPort::port)
        .containsExactly(tuple("h1", null), tuple("h2", 2222));
  }

  @Test
  void emptyAuthorityHasNoConfiguredHosts() {
    assertThat(MysqlHostParser.parseHostList("", true)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "h1:1111",
        "us(er):ex(ample)@h1:1111",
        "(user):example@h1:1111",
        "(user):ex(ample@h1:1111",
        "address=(user):example@h1:1111",
        "address=ex(ample@h1:1111",
        "user:ex(ample@h1:1111",
        "address=(host=h1)(port=1111)",
        "address=(host=h1)(port=1111)(user=other@example)"
      })
  void mariaDbUsesTheSameOrdinaryAndAddressHostParsing(String authority) {
    assertThat(MysqlHostParser.parseHostList(authority, false))
        .extracting(HostPort::host, HostPort::port)
        .containsExactly(tuple("h1", 1111));
  }

  @Test
  void mariaDbRejectsKeyValueHostsEvenInMixedLists() {
    assertThat(MysqlHostParser.parseHostList("h1:1111,(host=h2,port=2222)", false)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        " ",
        "h1,",
        "h1:invalid",
        "h1:",
        "h1:0",
        "h1:65536",
        "[::1]:invalid",
        "[::1]extra",
        "[h1]",
        "not a host",
        "unexpected=value",
        "address=(port=1111)",
        "address=(host=h1)(host=h2)",
        "address=(host=h1)(port=1111)(port=2222)",
        "address=(host=h1)(port=65536)",
        "address=(host=h1",
        "(host=h1",
        "(host=h1))",
        "(host=h1)(port=1111)",
        "(host=(h1),port=1111)",
        "(host=h1,(port)=1111)",
        "(port=1111)",
        "(host=)",
        "(host)",
        "(host=h1,host=h2)",
        "(host=h1,%68ost=h2)",
        "(Host=h1,host=h2)",
        "(host=h1,port=1111,port)",
        "(host=h1,port=65536)",
        "(host=h1,port=-1)",
        "(host=h1,port=+1111)",
        "(host=h1,port=not-a-port)",
        "(host=h1,=value)",
        "(host=h1,$key=value)",
        "(host=h1,\u2003key=value)",
        "(host=h1,other=bad%encoding)",
        "(%host=h1)",
        "(host=h1%20)",
        "(host=%09h1)",
        "(host=h1%29%28port%3d1111)",
        "(host=fe80::1%25eth0)",
        "(host=h1,other=value@h2)",
        "(host=h1,other=value@h2",
        "h1,address=(host=h2)(user=other@h3",
        "h1,(host=h2,other=value@h3",
        "(host=h1,other=value#h2)",
        "user:example@(host=h1)",
        "user:ex(ample)@(host=h1)",
        "[(host=h1),(host=h2)]",
        "h1:1111,(host=h2,port=65536)",
        "(host=h1),address=(port=1111)",
        "h1,h2,h3,h4,h5,(host=h6,port=65536)"
      })
  void rejectsTheEntireListWhenAnEntryIsUnsafe(String authority) {
    assertThat(MysqlHostParser.parseHostList(authority, true)).isNull();
  }
}
