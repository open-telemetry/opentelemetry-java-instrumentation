/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.HelperResources;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpringBootClassloadingTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void testResourceInjection() throws Exception {
    // Defer loading until after the test listener resets so its global-ignore guard sees the class.
    URLClassLoader classloader =
        Class.forName("org.springframework.boot.loader.LaunchedURLClassLoader")
            .asSubclass(URLClassLoader.class)
            .getConstructor(URL[].class, ClassLoader.class)
            .newInstance(new URL[0], null);
    cleanup.deferCleanup(classloader);

    Path tmpFile = Files.createTempFile("hello", "tmp");
    cleanup.deferCleanup(() -> Files.deleteIfExists(tmpFile));
    Files.write(tmpFile, "hello".getBytes(UTF_8));
    URL url = tmpFile.toUri().toURL();
    HelperResources.register(classloader, "hello.txt", singletonList(url));

    assertThat(classloader.getResource("hello.txt")).isEqualTo(url);
    assertThat(Collections.list(classloader.getResources("hello.txt"))).containsExactly(url);

    try (InputStream inputStream = classloader.getResourceAsStream("hello.txt")) {
      assertThat(inputStream).hasContent("hello");
    }
  }
}
