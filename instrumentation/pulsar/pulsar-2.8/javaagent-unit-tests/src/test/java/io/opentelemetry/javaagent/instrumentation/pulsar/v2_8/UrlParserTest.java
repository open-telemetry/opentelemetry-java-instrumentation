/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlParserTest {

  @Test
  void parseUrl() {
    assertThat(UrlParser.parseUrl(null)).isNull();
    assertThat(UrlParser.parseUrl("localhost:1")).isNull();
    assertThat(UrlParser.parseUrl("localhost:1,localhost:2")).isNull();
    assertThat(UrlParser.parseUrl("localhost:1;localhost:2")).isNull();

    assertHostAndPort("pulsar://localhost:1", "localhost", 1);
    assertHostAndPort("pulsar://localhost:1/foo", "localhost", 1);
    assertHostAndPort("pulsar://localhost", "localhost", null);
    assertHostAndPort("pulsar://localhost:xxx", "localhost", null);
  }

  private static void assertHostAndPort(String input, String host, Integer port) {
    UrlParser.UrlData url = UrlParser.parseUrl(input);
    assertThat(url).isNotNull();
    assertThat(url.getHost()).isEqualTo(host);
    assertThat(url.getPort()).isEqualTo(port);
  }
}
