/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

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

  @Test
  void testGetPath() {
    assertThat(UrlParser.getPath("https://localhost")).isNull();
    assertThat(UrlParser.getPath("https://localhost/")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost/api/v1")).isEqualTo("/api/v1");

    assertThat(UrlParser.getPath("https://localhost?")).isNull();
    assertThat(UrlParser.getPath("https://localhost/?")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost/api/v1?")).isEqualTo("/api/v1");

    assertThat(UrlParser.getPath("https://localhost?query")).isNull();
    assertThat(UrlParser.getPath("https://localhost/?query")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost/api/v1?query")).isEqualTo("/api/v1");

    assertThat(UrlParser.getPath("https://localhost#")).isNull();
    assertThat(UrlParser.getPath("https://localhost/#")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost/api/v1#")).isEqualTo("/api/v1");

    assertThat(UrlParser.getPath("https://localhost#fragment")).isNull();
    assertThat(UrlParser.getPath("https://localhost/#fragment")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost/api/v1#fragment")).isEqualTo("/api/v1");
  }

  @Test
  void testGetPathWithPort() {
    assertThat(UrlParser.getPath("https://localhost:8080")).isNull();
    assertThat(UrlParser.getPath("https://localhost:8080/")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost:8080/api/v1")).isEqualTo("/api/v1");

    assertThat(UrlParser.getPath("https://localhost:8080?")).isNull();
    assertThat(UrlParser.getPath("https://localhost:8080/?")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost:8080/api/v1?")).isEqualTo("/api/v1");

    assertThat(UrlParser.getPath("https://localhost:8080?query")).isNull();
    assertThat(UrlParser.getPath("https://localhost:8080/?query")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost:8080/api/v1?query")).isEqualTo("/api/v1");

    assertThat(UrlParser.getPath("https://localhost:8080#")).isNull();
    assertThat(UrlParser.getPath("https://localhost:8080/#")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost:8080/api/v1#")).isEqualTo("/api/v1");

    assertThat(UrlParser.getPath("https://localhost:8080#fragment")).isNull();
    assertThat(UrlParser.getPath("https://localhost:8080/#fragment")).isEqualTo("/");
    assertThat(UrlParser.getPath("https://localhost:8080/api/v1#fragment")).isEqualTo("/api/v1");
  }

  @Test
  void testGetPathWithNoAuthority() {
    assertThat(UrlParser.getPath("https:")).isNull();
    assertThat(UrlParser.getPath("https:/")).isNull();
    assertThat(UrlParser.getPath("https:/api/v1")).isNull();

    assertThat(UrlParser.getPath("https:?")).isNull();
    assertThat(UrlParser.getPath("https:/?")).isNull();
    assertThat(UrlParser.getPath("https:/api/v1?")).isNull();

    assertThat(UrlParser.getPath("https:?query")).isNull();
    assertThat(UrlParser.getPath("https:/?query")).isNull();
    assertThat(UrlParser.getPath("https:/api/v1?query")).isNull();

    assertThat(UrlParser.getPath("https:#")).isNull();
    assertThat(UrlParser.getPath("https:/#")).isNull();
    assertThat(UrlParser.getPath("https:/api/v1#")).isNull();

    assertThat(UrlParser.getPath("https:#fragment")).isNull();
    assertThat(UrlParser.getPath("https:/#fragment")).isNull();
    assertThat(UrlParser.getPath("https:/api/v1#fragment")).isNull();
  }

  @Test
  void testGetPathtWithNoScheme() {
    assertThat(UrlParser.getPath("")).isNull();
    assertThat(UrlParser.getPath("/")).isNull();
    assertThat(UrlParser.getPath("/api/v1")).isNull();

    assertThat(UrlParser.getPath("?")).isNull();
    assertThat(UrlParser.getPath("/?")).isNull();
    assertThat(UrlParser.getPath("/api/v1?")).isNull();

    assertThat(UrlParser.getPath("?query")).isNull();
    assertThat(UrlParser.getPath("/?query")).isNull();
    assertThat(UrlParser.getPath("/api/v1?query")).isNull();

    assertThat(UrlParser.getPath("#")).isNull();
    assertThat(UrlParser.getPath("/#")).isNull();
    assertThat(UrlParser.getPath("/api/v1#")).isNull();

    assertThat(UrlParser.getPath("#fragment")).isNull();
    assertThat(UrlParser.getPath("/#fragment")).isNull();
    assertThat(UrlParser.getPath("/api/v1#fragment")).isNull();
  }
}
