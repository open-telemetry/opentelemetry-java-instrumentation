/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static io.opentelemetry.instrumentation.test.utils.GcUtils.awaitGc;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ResourceInjectionTest {

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void resourcesInjectedToNonDelegatingClassLoader() throws Exception {
    String resourceName = "test-resources/test-resource.txt";
    URL[] urls = {SystemUtils.class.getProtectionDomain().getCodeSource().getLocation()};
    URLClassLoader emptyLoader = new URLClassLoader(urls, null);

    Enumeration<URL> resourceUrls = emptyLoader.getResources(resourceName);
    assertThat(resourceUrls.hasMoreElements()).isFalse();
    resourceUrls = null;

    URLClassLoader notInjectedLoader = new URLClassLoader(urls, null);
    cleanup.deferCleanup(notInjectedLoader);
    // this triggers resource injection
    emptyLoader.loadClass(SystemUtils.class.getName());

    List<URL> resourceList = Collections.list(emptyLoader.getResources(resourceName));

    assertThat(resourceList).hasSize(2);
    assertThat(readLine(resourceList.get(0))).isEqualTo("Hello world!");
    assertThat(readLine(resourceList.get(1))).isEqualTo("Hello there");

    assertThat(notInjectedLoader.getResources(resourceName).hasMoreElements()).isFalse();

    // references to emptyloader are gone
    emptyLoader.close(); // cleanup
    WeakReference<URLClassLoader> ref = new WeakReference<>(emptyLoader);
    emptyLoader = null;

    awaitGc(ref, Duration.ofSeconds(10));

    assertThat(ref.get()).isNull();
  }

  private static String readLine(URL url) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(url.openStream(), UTF_8))) {
      return reader.readLine().trim();
    }
  }
}
