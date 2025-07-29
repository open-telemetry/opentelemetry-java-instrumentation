/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static io.opentelemetry.instrumentation.test.utils.GcUtils.awaitGc;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

class ResourceInjectionTest {

  private static String readLine(URL url) throws Exception {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
      return reader.readLine().trim();
    }
  }

  @Test
  @SuppressWarnings("UnnecessaryAsync")
  void resourcesInjectedToNonDelegatingClassLoader() throws Exception {
    String resourceName = "test-resources/test-resource.txt";
    URL[] urls = {SystemUtils.class.getProtectionDomain().getCodeSource().getLocation()};
    AtomicReference<URLClassLoader> emptyLoader =
        new AtomicReference<>(new URLClassLoader(urls, null));

    Enumeration<URL> resourceUrls = emptyLoader.get().getResources(resourceName);
    assertThat(resourceUrls.hasMoreElements()).isFalse();
    resourceUrls = null;

    URLClassLoader notInjectedLoader = new URLClassLoader(urls, null);

    // this triggers resource injection
    emptyLoader.get().loadClass(SystemUtils.class.getName());

    List<URL> resourceList = Collections.list(emptyLoader.get().getResources(resourceName));

    assertThat(resourceList.size()).isEqualTo(2);
    assertThat(readLine(resourceList.get(0))).isEqualTo("Hello world!");
    assertThat(readLine(resourceList.get(1))).isEqualTo("Hello there");

    assertThat(notInjectedLoader.getResources(resourceName).hasMoreElements()).isFalse();

    // references to emptyloader are gone
    emptyLoader.get().close(); // cleanup
    WeakReference<URLClassLoader> ref = new WeakReference<>(emptyLoader.get());
    emptyLoader.set(null);

    awaitGc(ref, Duration.ofSeconds(10));

    assertThat(ref.get()).isNull();
  }
}
