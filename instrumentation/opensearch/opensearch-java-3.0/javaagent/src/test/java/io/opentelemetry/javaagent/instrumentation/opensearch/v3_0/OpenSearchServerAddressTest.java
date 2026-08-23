/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenSearchServerAddressTest {

  @Test
  void shouldRemoveIpv6Brackets() throws Exception {
    URL instrumentationJar =
        Path.of(System.getProperty("otel.javaagent.experimental.initializer.jar")).toUri().toURL();
    try (URLClassLoader classLoader =
        new URLClassLoader(new URL[] {instrumentationJar}, getClass().getClassLoader())) {
      Class<?> serverAddressClass =
          classLoader.loadClass(
              "io.opentelemetry.javaagent.instrumentation.opensearch.v3_0.OpenSearchServerAddress");

      assertThat(
              serverAddressClass
                  .getMethod("fromHost", String.class)
                  .invoke(null, "https://[2001:db8::1]:9200"))
          .isNotNull()
          .hasFieldOrPropertyWithValue("address", "2001:db8::1")
          .hasFieldOrPropertyWithValue("port", 9200);
    }
  }
}
