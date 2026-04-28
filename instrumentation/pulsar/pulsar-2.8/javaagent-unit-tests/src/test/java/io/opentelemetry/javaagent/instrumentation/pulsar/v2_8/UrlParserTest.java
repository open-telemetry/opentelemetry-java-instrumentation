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

    assertThat(UrlParser.parseUrl("pulsar://localhost:1"))
        .isNotNull()
        .satisfies(
            url -> {
              assertThat(url.getHost()).isEqualTo("localhost");
              assertThat(url.getPort()).isEqualTo(1);
            });
    assertThat(UrlParser.parseUrl("pulsar://localhost:1/foo"))
        .isNotNull()
        .satisfies(
            url -> {
              assertThat(url.getHost()).isEqualTo("localhost");
              assertThat(url.getPort()).isEqualTo(1);
            });
    assertThat(UrlParser.parseUrl("pulsar://localhost"))
        .isNotNull()
        .satisfies(
            url -> {
              assertThat(url.getHost()).isEqualTo("localhost");
              assertThat(url.getPort()).isNull();
            });
    assertThat(UrlParser.parseUrl("pulsar://localhost:xxx"))
        .isNotNull()
        .satisfies(
            url -> {
              assertThat(url.getHost()).isEqualTo("localhost");
              assertThat(url.getPort()).isNull();
            });
  }
}
