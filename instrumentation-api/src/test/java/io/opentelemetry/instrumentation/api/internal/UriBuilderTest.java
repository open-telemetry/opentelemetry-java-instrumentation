/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class UriBuilderTest {

  @ParameterizedTest
  @ArgumentsSource(Parameters.class)
  public void test(String scheme, String host, int port, String path, String query)
      throws URISyntaxException {

    assertThat(UriBuilder.uri(scheme, host, port, path, query))
        .isEqualTo(new URI(scheme, null, host, port, path, query, null).toString());
  }

  // can't use parameterized test above because URI.toString() encodes the port when it is supplied,
  // even it's the default port
  @Test
  public void testHttpDefaultPort() {
    assertThat(UriBuilder.uri("http", "myhost", 80, "/mypath", "myquery"))
        .isEqualTo("http://myhost/mypath?myquery");
  }

  // can't use parameterized test above because URI.toString() encodes the port when it is supplied,
  // even it's the default port
  @Test
  public void testHttpsDefaultPort() {
    assertThat(UriBuilder.uri("https", "myhost", 443, "/mypath", "myquery"))
        .isEqualTo("https://myhost/mypath?myquery");
  }

  private static class Parameters implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of("http", "myhost", -1, "/mypath", "myquery"), // test default http port
          Arguments.of("http", "myhost", 8080, "/mypath", "myquery"), // test non-default http port
          Arguments.of("https", "myhost", -1, "/mypath", "myquery"), // test default https port
          Arguments.of(
              "https", "myhost", 8443, "/mypath", "myquery"), // test non-default https port
          Arguments.of("http", "myhost", -1, "/", "myquery"), // test root path
          Arguments.of("http", "myhost", -1, "", "myquery"), // test empty path
          Arguments.of("http", "myhost", -1, "/mypath", ""), // test empty query string
          Arguments.of("http", "myhost", -1, "/mypath", null) // test null query string
          );
    }
  }
}
