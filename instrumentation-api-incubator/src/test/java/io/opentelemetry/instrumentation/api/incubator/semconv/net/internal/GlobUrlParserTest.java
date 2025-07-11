/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobUrlParserTest {

  @Test
  void testGetHost() {
    assertThat(GlobUrlParser.getHost("https://*.example.???")).isEqualTo("*.example.???");
    assertThat(GlobUrlParser.getHost("https://*.example.???/")).isEqualTo("*.example.???");

    assertThat(GlobUrlParser.getHost("https://localhost?")).isEqualTo("localhost?");
    assertThat(GlobUrlParser.getHost("https://*.example.???/?")).isEqualTo("*.example.???");

    assertThat(GlobUrlParser.getHost("https://localhost?query")).isEqualTo("localhost?query");
    assertThat(GlobUrlParser.getHost("https://*.example.???/?query")).isEqualTo("*.example.???");

    assertThat(GlobUrlParser.getHost("https://*.example.???#")).isEqualTo("*.example.???");
    assertThat(GlobUrlParser.getHost("https://*.example.???/#")).isEqualTo("*.example.???");

    assertThat(GlobUrlParser.getHost("https://*.example.???#fragment")).isEqualTo("*.example.???");
    assertThat(GlobUrlParser.getHost("https://*.example.???/#fragment")).isEqualTo("*.example.???");
  }

  @Test
  void testGetHostWithPort() {
    assertThat(GlobUrlParser.getHost("https://*.example.???:8080")).isEqualTo("*.example.???");
    assertThat(GlobUrlParser.getHost("https://*.example.???:8080")).isEqualTo("*.example.???");
    assertThat(GlobUrlParser.getHost("https://*.example.???:8080/")).isEqualTo("*.example.???");

    assertThat(GlobUrlParser.getHost("https://*.example.???:8080?")).isEqualTo("*.example.???");
    assertThat(GlobUrlParser.getHost("https://*.example.???:8080/?")).isEqualTo("*.example.???");

    assertThat(GlobUrlParser.getHost("https://*.example.???:8080?query"))
        .isEqualTo("*.example.???");
    assertThat(GlobUrlParser.getHost("https://*.example.???:8080/?query"))
        .isEqualTo("*.example.???");

    assertThat(GlobUrlParser.getHost("https://*.example.???:8080#")).isEqualTo("*.example.???");
    assertThat(GlobUrlParser.getHost("https://*.example.???:8080/#")).isEqualTo("*.example.???");

    assertThat(GlobUrlParser.getHost("https://*.example.???:8080#fragment"))
        .isEqualTo("*.example.???");
    assertThat(GlobUrlParser.getHost("https://*.example.???:8080/#fragment"))
        .isEqualTo("*.example.???");
  }

  // Copied from UrlParserTest
  @Test
  void testGetHostWithNoAuthority() {
    assertThat(GlobUrlParser.getHost("https:")).isNull();
    assertThat(GlobUrlParser.getHost("https:/")).isNull();

    assertThat(GlobUrlParser.getHost("https:?")).isNull();
    assertThat(GlobUrlParser.getHost("https:/?")).isNull();

    assertThat(GlobUrlParser.getHost("https:?query")).isNull();
    assertThat(GlobUrlParser.getHost("https:/?query")).isNull();

    assertThat(GlobUrlParser.getHost("https:#")).isNull();
    assertThat(GlobUrlParser.getHost("https:/#")).isNull();

    assertThat(GlobUrlParser.getHost("https:#fragment")).isNull();
    assertThat(GlobUrlParser.getHost("https:/#fragment")).isNull();
  }

  // Copied from UrlParserTest
  @Test
  void testGetHostWithNoScheme() {
    assertThat(GlobUrlParser.getHost("")).isNull();
    assertThat(GlobUrlParser.getHost("/")).isNull();

    assertThat(GlobUrlParser.getHost("?")).isNull();
    assertThat(GlobUrlParser.getHost("/?")).isNull();

    assertThat(GlobUrlParser.getHost("?query")).isNull();
    assertThat(GlobUrlParser.getHost("/?query")).isNull();

    assertThat(GlobUrlParser.getHost("#")).isNull();
    assertThat(GlobUrlParser.getHost("/#")).isNull();

    assertThat(GlobUrlParser.getHost("#fragment")).isNull();
    assertThat(GlobUrlParser.getHost("/#fragment")).isNull();
  }

  @Test
  void testGetPort() {
    assertThat(GlobUrlParser.getPort("https://*.example.???")).isNull();
    assertThat(GlobUrlParser.getPort("https://*.example.???/")).isNull();

    assertThat(GlobUrlParser.getPort("https://*.example.???")).isNull();
    assertThat(GlobUrlParser.getPort("https://*.example.???/?")).isNull();

    assertThat(GlobUrlParser.getPort("https://localhost?query")).isNull();
    assertThat(GlobUrlParser.getPort("https://*.example.???/?query")).isNull();

    assertThat(GlobUrlParser.getPort("https://*.example.???#")).isNull();
    assertThat(GlobUrlParser.getPort("https://*.example.???/#")).isNull();

    assertThat(GlobUrlParser.getPort("https://*.example.???#fragment")).isNull();
    assertThat(GlobUrlParser.getPort("https://*.example.???/#fragment")).isNull();
  }

  @Test
  void testGetPortWithPort() {
    assertThat(GlobUrlParser.getPort("https://*.example.???:8080")).isEqualTo(8080);
    assertThat(GlobUrlParser.getPort("https://*.example.???:8080/")).isEqualTo(8080);

    assertThat(GlobUrlParser.getPort("https://*.example.???:8080?")).isEqualTo(8080);
    assertThat(GlobUrlParser.getPort("https://*.example.???:8080/?")).isEqualTo(8080);

    assertThat(GlobUrlParser.getPort("https://*.example.???:8080?query")).isEqualTo(8080);
    assertThat(GlobUrlParser.getPort("https://*.example.???:8080/?query")).isEqualTo(8080);

    assertThat(GlobUrlParser.getPort("https://*.example.???:8080#")).isEqualTo(8080);
    assertThat(GlobUrlParser.getPort("https://*.example.???:8080/#")).isEqualTo(8080);

    assertThat(GlobUrlParser.getPort("https://*.example.???:8080#fragment")).isEqualTo(8080);
    assertThat(GlobUrlParser.getPort("https://*.example.???:8080/#fragment")).isEqualTo(8080);
  }

  // Copied from UrlParserTest
  @Test
  void testGetPortWithNoAuthority() {
    assertThat(GlobUrlParser.getPort("https:")).isNull();
    assertThat(GlobUrlParser.getPort("https:/")).isNull();

    assertThat(GlobUrlParser.getPort("https:?")).isNull();
    assertThat(GlobUrlParser.getPort("https:/?")).isNull();

    assertThat(GlobUrlParser.getPort("https:?query")).isNull();
    assertThat(GlobUrlParser.getPort("https:/?query")).isNull();

    assertThat(GlobUrlParser.getPort("https:#")).isNull();
    assertThat(GlobUrlParser.getPort("https:/#")).isNull();

    assertThat(GlobUrlParser.getPort("https:#fragment")).isNull();
    assertThat(GlobUrlParser.getPort("https:/#fragment")).isNull();
  }

  // Copied from UrlParserTest
  @Test
  void testGetPortWithNoScheme() {
    assertThat(GlobUrlParser.getPort("")).isNull();
    assertThat(GlobUrlParser.getPort("/")).isNull();

    assertThat(GlobUrlParser.getPort("?")).isNull();
    assertThat(GlobUrlParser.getPort("/?")).isNull();

    assertThat(GlobUrlParser.getPort("?query")).isNull();
    assertThat(GlobUrlParser.getPort("/?query")).isNull();

    assertThat(GlobUrlParser.getPort("#")).isNull();
    assertThat(GlobUrlParser.getPort("/#")).isNull();

    assertThat(GlobUrlParser.getPort("#fragment")).isNull();
    assertThat(GlobUrlParser.getPort("/#fragment")).isNull();
  }

  @Test
  void testGetPath() {
    assertThat(GlobUrlParser.getPath("https://*.example.???")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???/")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???/api/v1")).isEqualTo("/api/v1");

    assertThat(GlobUrlParser.getPath("https://localhost?")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???/?")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???/api/v1?")).isEqualTo("/api/v1");

    assertThat(GlobUrlParser.getPath("https://localhost?query")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???/?query")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???/api/v1?query")).isEqualTo("/api/v1");

    assertThat(GlobUrlParser.getPath("https://*.example.???#")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???/#")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???/api/v1#")).isEqualTo("/api/v1");

    assertThat(GlobUrlParser.getPath("https://*.example.???#fragment")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???/#fragment")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???/api/v1#fragment")).isEqualTo("/api/v1");
  }

  @Test
  void testGetPathWithPort() {
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/api/v1")).isEqualTo("/api/v1");

    assertThat(GlobUrlParser.getPath("https://*.example.???:8080?")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/?")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/api/v1?")).isEqualTo("/api/v1");

    assertThat(GlobUrlParser.getPath("https://*.example.???:8080?query")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/?query")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/api/v1?query"))
        .isEqualTo("/api/v1");

    assertThat(GlobUrlParser.getPath("https://*.example.???:8080#")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/#")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/api/v1#")).isEqualTo("/api/v1");

    assertThat(GlobUrlParser.getPath("https://*.example.???:8080#fragment")).isNull();
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/#fragment")).isEqualTo("/");
    assertThat(GlobUrlParser.getPath("https://*.example.???:8080/api/v1#fragment"))
        .isEqualTo("/api/v1");
  }

  // Copied from UrlParserTest
  @Test
  void testGetPathWithNoAuthority() {
    assertThat(GlobUrlParser.getPath("https:")).isNull();
    assertThat(GlobUrlParser.getPath("https:/")).isNull();
    assertThat(GlobUrlParser.getPath("https:/api/v1")).isNull();

    assertThat(GlobUrlParser.getPath("https:?")).isNull();
    assertThat(GlobUrlParser.getPath("https:/?")).isNull();
    assertThat(GlobUrlParser.getPath("https:/api/v1?")).isNull();

    assertThat(GlobUrlParser.getPath("https:?query")).isNull();
    assertThat(GlobUrlParser.getPath("https:/?query")).isNull();
    assertThat(GlobUrlParser.getPath("https:/api/v1?query")).isNull();

    assertThat(GlobUrlParser.getPath("https:#")).isNull();
    assertThat(GlobUrlParser.getPath("https:/#")).isNull();
    assertThat(GlobUrlParser.getPath("https:/api/v1#")).isNull();

    assertThat(GlobUrlParser.getPath("https:#fragment")).isNull();
    assertThat(GlobUrlParser.getPath("https:/#fragment")).isNull();
    assertThat(GlobUrlParser.getPath("https:/api/v1#fragment")).isNull();
  }

  // Copied from UrlParserTest
  @Test
  void testGetPathWithNoScheme() {
    assertThat(GlobUrlParser.getPath("")).isNull();
    assertThat(GlobUrlParser.getPath("/")).isNull();
    assertThat(GlobUrlParser.getPath("/api/v1")).isNull();

    assertThat(GlobUrlParser.getPath("?")).isNull();
    assertThat(GlobUrlParser.getPath("/?")).isNull();
    assertThat(GlobUrlParser.getPath("/api/v1?")).isNull();

    assertThat(GlobUrlParser.getPath("?query")).isNull();
    assertThat(GlobUrlParser.getPath("/?query")).isNull();
    assertThat(GlobUrlParser.getPath("/api/v1?query")).isNull();

    assertThat(GlobUrlParser.getPath("#")).isNull();
    assertThat(GlobUrlParser.getPath("/#")).isNull();
    assertThat(GlobUrlParser.getPath("/api/v1#")).isNull();

    assertThat(GlobUrlParser.getPath("#fragment")).isNull();
    assertThat(GlobUrlParser.getPath("/#fragment")).isNull();
    assertThat(GlobUrlParser.getPath("/api/v1#fragment")).isNull();
  }
}
