/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlParserTest {

  @Test
  void testGetHost() {
    assertThat(UrlParser.getHost("https://localhost")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost/")).isEqualTo("localhost");

    assertThat(UrlParser.getHost("https://localhost?")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost/?")).isEqualTo("localhost");

    assertThat(UrlParser.getHost("https://localhost?query")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost/?query")).isEqualTo("localhost");

    assertThat(UrlParser.getHost("https://localhost#")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost/#")).isEqualTo("localhost");

    assertThat(UrlParser.getHost("https://localhost#fragment")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost/#fragment")).isEqualTo("localhost");
  }

  @Test
  void testGetHostWithPort() {
    assertThat(UrlParser.getHost("https://localhost:8080")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost:8080/")).isEqualTo("localhost");

    assertThat(UrlParser.getHost("https://localhost:8080?")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost:8080/?")).isEqualTo("localhost");

    assertThat(UrlParser.getHost("https://localhost:8080?query")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost:8080/?query")).isEqualTo("localhost");

    assertThat(UrlParser.getHost("https://localhost:8080#")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost:8080/#")).isEqualTo("localhost");

    assertThat(UrlParser.getHost("https://localhost:8080#fragment")).isEqualTo("localhost");
    assertThat(UrlParser.getHost("https://localhost:8080/#fragment")).isEqualTo("localhost");
  }

  @Test
  void testGetHostWithNoAuthority() {
    assertThat(UrlParser.getHost("https:")).isNull();
    assertThat(UrlParser.getHost("https:/")).isNull();

    assertThat(UrlParser.getHost("https:?")).isNull();
    assertThat(UrlParser.getHost("https:/?")).isNull();

    assertThat(UrlParser.getHost("https:?query")).isNull();
    assertThat(UrlParser.getHost("https:/?query")).isNull();

    assertThat(UrlParser.getHost("https:#")).isNull();
    assertThat(UrlParser.getHost("https:/#")).isNull();

    assertThat(UrlParser.getHost("https:#fragment")).isNull();
    assertThat(UrlParser.getHost("https:/#fragment")).isNull();
  }

  @Test
  void testGetHostWithNoScheme() {
    assertThat(UrlParser.getHost("")).isNull();
    assertThat(UrlParser.getHost("/")).isNull();

    assertThat(UrlParser.getHost("?")).isNull();
    assertThat(UrlParser.getHost("/?")).isNull();

    assertThat(UrlParser.getHost("?query")).isNull();
    assertThat(UrlParser.getHost("/?query")).isNull();

    assertThat(UrlParser.getHost("#")).isNull();
    assertThat(UrlParser.getHost("/#")).isNull();

    assertThat(UrlParser.getHost("#fragment")).isNull();
    assertThat(UrlParser.getHost("/#fragment")).isNull();
  }

  @Test
  void testGetPort() {
    assertThat(UrlParser.getPort("https://localhost")).isNull();
    assertThat(UrlParser.getPort("https://localhost/")).isNull();

    assertThat(UrlParser.getPort("https://localhost?")).isNull();
    assertThat(UrlParser.getPort("https://localhost/?")).isNull();

    assertThat(UrlParser.getPort("https://localhost?query")).isNull();
    assertThat(UrlParser.getPort("https://localhost/?query")).isNull();

    assertThat(UrlParser.getPort("https://localhost#")).isNull();
    assertThat(UrlParser.getPort("https://localhost/#")).isNull();

    assertThat(UrlParser.getPort("https://localhost#fragment")).isNull();
    assertThat(UrlParser.getPort("https://localhost/#fragment")).isNull();
  }

  @Test
  void testGetPortWithPort() {
    assertThat(UrlParser.getPort("https://localhost:8080")).isEqualTo(8080);
    assertThat(UrlParser.getPort("https://localhost:8080/")).isEqualTo(8080);

    assertThat(UrlParser.getPort("https://localhost:8080?")).isEqualTo(8080);
    assertThat(UrlParser.getPort("https://localhost:8080/?")).isEqualTo(8080);

    assertThat(UrlParser.getPort("https://localhost:8080?query")).isEqualTo(8080);
    assertThat(UrlParser.getPort("https://localhost:8080/?query")).isEqualTo(8080);

    assertThat(UrlParser.getPort("https://localhost:8080#")).isEqualTo(8080);
    assertThat(UrlParser.getPort("https://localhost:8080/#")).isEqualTo(8080);

    assertThat(UrlParser.getPort("https://localhost:8080#fragment")).isEqualTo(8080);
    assertThat(UrlParser.getPort("https://localhost:8080/#fragment")).isEqualTo(8080);
  }

  @Test
  void testGetPortWithNoAuthority() {
    assertThat(UrlParser.getPort("https:")).isNull();
    assertThat(UrlParser.getPort("https:/")).isNull();

    assertThat(UrlParser.getPort("https:?")).isNull();
    assertThat(UrlParser.getPort("https:/?")).isNull();

    assertThat(UrlParser.getPort("https:?query")).isNull();
    assertThat(UrlParser.getPort("https:/?query")).isNull();

    assertThat(UrlParser.getPort("https:#")).isNull();
    assertThat(UrlParser.getPort("https:/#")).isNull();

    assertThat(UrlParser.getPort("https:#fragment")).isNull();
    assertThat(UrlParser.getPort("https:/#fragment")).isNull();
  }

  @Test
  void testGetPortWithNoScheme() {
    assertThat(UrlParser.getPort("")).isNull();
    assertThat(UrlParser.getPort("/")).isNull();

    assertThat(UrlParser.getPort("?")).isNull();
    assertThat(UrlParser.getPort("/?")).isNull();

    assertThat(UrlParser.getPort("?query")).isNull();
    assertThat(UrlParser.getPort("/?query")).isNull();

    assertThat(UrlParser.getPort("#")).isNull();
    assertThat(UrlParser.getPort("/#")).isNull();

    assertThat(UrlParser.getPort("#fragment")).isNull();
    assertThat(UrlParser.getPort("/#fragment")).isNull();
  }
}
