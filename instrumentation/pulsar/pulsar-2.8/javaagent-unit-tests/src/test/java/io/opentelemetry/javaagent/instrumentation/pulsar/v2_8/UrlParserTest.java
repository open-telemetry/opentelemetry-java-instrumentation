/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import org.junit.jupiter.api.Test;

class UrlParserTest {

  @Test
  void parseUrl() {
    assertThat(UrlParser.parseUrl(null)).isNull();
    assertThat(UrlParser.parseUrl("localhost:1")).isNull();
    assertThat(UrlParser.parseUrl("localhost:1,localhost:2")).isNull();
    assertThat(UrlParser.parseUrl("localhost:1;localhost:2")).isNull();

    {
      UrlData url = UrlParser.parseUrl("pulsar://localhost:1");
      assertThat(url.getHost()).isEqualTo("localhost");
      assertThat(url.getPort()).isEqualTo(1);
    }
    {
      UrlData url = UrlParser.parseUrl("pulsar://localhost:1/foo");
      assertThat(url.getHost()).isEqualTo("localhost");
      assertThat(url.getPort()).isEqualTo(1);
    }
    {
      UrlData url = UrlParser.parseUrl("pulsar://localhost");
      assertThat(url.getHost()).isEqualTo("localhost");
      assertThat(url.getPort()).isNull();
    }
    {
      UrlData url = UrlParser.parseUrl("pulsar://localhost:xxx");
      assertThat(url.getHost()).isEqualTo("localhost");
      assertThat(url.getPort()).isNull();
    }
  }
}
