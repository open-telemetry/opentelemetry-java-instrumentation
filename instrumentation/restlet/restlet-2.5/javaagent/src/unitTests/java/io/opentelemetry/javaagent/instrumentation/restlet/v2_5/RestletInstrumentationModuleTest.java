/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_5;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.javaagent.bootstrap.internal.ClassLoaderMatcherCacheHolder;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RestletInstrumentationModuleTest {

  @ParameterizedTest
  @MethodSource("manifests")
  void matchesManifestFallback(String manifest, boolean expected, @TempDir Path tempDir)
      throws IOException {
    Path jar = tempDir.resolve("restlet.jar");
    createJar(jar, manifest);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
      assertThat(loader.getResource("META-INF/maven/org.restlet/org.restlet/pom.properties"))
          .isNull();
      assertThat(new RestletInstrumentationModule().classLoaderMatcher().matches(loader))
          .isEqualTo(expected);
    }
  }

  @Test
  void invalidationDuringLookupDoesNotRepublishCachedResult(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    Path supportedJar = tempDir.resolve("supported.jar");
    Path unsupportedJar = tempDir.resolve("unsupported.jar");
    createJar(supportedJar, manifest("org.restlet", "2.5.0"));
    createJar(unsupportedJar, manifest("org.restlet", "2.4.4"));

    URL supportedRequest = requestClassUrl(supportedJar);
    URL unsupportedRequest = requestClassUrl(unsupportedJar);
    AtomicReference<URL> currentRequest = new AtomicReference<>(supportedRequest);
    AtomicInteger requestLookups = new AtomicInteger();
    CountDownLatch lookupStarted = new CountDownLatch(1);
    CountDownLatch continueLookup = new CountDownLatch(1);
    CountDownLatch invalidationComplete = new CountDownLatch(1);
    ClassLoader loader =
        new ClassLoader(null) {
          @Override
          public URL getResource(String name) {
            if (!"org/restlet/Request.class".equals(name)) {
              return null;
            }
            if (requestLookups.incrementAndGet() == 2) {
              lookupStarted.countDown();
              try {
                continueLookup.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
              }
            }
            return currentRequest.get();
          }
        };
    RestletInstrumentationModule module = new RestletInstrumentationModule();
    AtomicReference<Boolean> firstResult = new AtomicReference<>();
    Thread matcherThread =
        new Thread(() -> firstResult.set(module.classLoaderMatcher().matches(loader)));
    matcherThread.start();
    Thread invalidationThread =
        new Thread(() -> {
          ClassLoaderMatcherCacheHolder.invalidateAllCachesForClassLoader(loader);
          invalidationComplete.countDown();
        });
    try {
      assertThat(lookupStarted.await(10, SECONDS)).isTrue();
      invalidationThread.start();
      assertThat(invalidationComplete.await(10, SECONDS)).isTrue();
      currentRequest.set(supportedRequest);
    } finally {
      continueLookup.countDown();
    }
    matcherThread.join(SECONDS.toMillis(10));
    if (invalidationThread.isAlive()) {
      invalidationThread.join(SECONDS.toMillis(10));
    }

    assertThat(matcherThread.isAlive()).isFalse();
    assertThat(firstResult.get()).isTrue();
    currentRequest.set(unsupportedRequest);
    assertThat(module.classLoaderMatcher().matches(loader)).isFalse();
    assertThat(requestLookups.get()).isEqualTo(4);
  }

  private static void createJar(Path jar, String manifest) throws IOException {
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("org/restlet/Request.class"));
      output.closeEntry();
      if (manifest != null) {
        output.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
        output.write(manifest.getBytes(UTF_8));
        output.closeEntry();
      }
    }
  }

  private static URL requestClassUrl(Path jar) throws IOException {
    return new URL("jar:" + jar.toUri().toURL() + "!/org/restlet/Request.class");
  }

  private static Stream<Arguments> manifests() {
    return Stream.of(
        argumentSet("minimum supported", manifest("org.restlet", "2.5.0"), true),
        argumentSet("later supported", manifest("org.restlet", "2.7.0"), true),
        argumentSet("older version", manifest("org.restlet", "2.4.4"), false),
        argumentSet("release candidate", manifest("org.restlet", "2.5.0-RC1"), false),
        argumentSet("next major", manifest("org.restlet", "3.0.0-m1"), false),
        argumentSet("missing manifest", null, false),
        argumentSet(
            "missing version",
            "Manifest-Version: 1.0\nBundle-SymbolicName: org.restlet\n\n",
            false),
        argumentSet(
            "missing symbolic name", "Manifest-Version: 1.0\nBundle-Version: 2.5.0\n\n", false),
        argumentSet("malformed version", manifest("org.restlet", "not-a-version"), false),
        argumentSet("malformed manifest", "not a manifest\n\n", false),
        argumentSet("other bundle", manifest("other.bundle", "2.5.0"), false));
  }

  private static String manifest(String symbolicName, String version) {
    return "Manifest-Version: 1.0\nBundle-SymbolicName: "
        + symbolicName
        + "\nBundle-Version: "
        + version
        + "\n\n";
  }
}
