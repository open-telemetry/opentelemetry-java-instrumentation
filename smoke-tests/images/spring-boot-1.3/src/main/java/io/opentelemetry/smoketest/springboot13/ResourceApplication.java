/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.springboot13;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public final class ResourceApplication {
  private static final String RESOURCE = "otel-smoke/injected-resource.txt";
  private static final String CONTENT = "injected by the smoke test extension\n";

  public static void main(String[] args) throws IOException {
    ClassLoader loader = ResourceApplication.class.getClassLoader();
    if (!loader
        .getClass()
        .getName()
        .equals("org.springframework.boot.loader.LaunchedURLClassLoader")) {
      throw new AssertionError("Unexpected application loader: " + loader);
    }
    if (!System.getProperty("java.specification.version").equals("1.8")) {
      throw new AssertionError("This regression must run on JDK 8");
    }
    System.out.println("LOADER " + loader.getClass().getName());

    List<String> failures = new ArrayList<>();
    URL resource = loader.getResource(RESOURCE);
    if (resource == null) {
      failures.add("getResource returned null");
    } else {
      verifyContent(resource.openStream(), "getResource", failures);
    }

    Enumeration<URL> resources = loader.getResources(RESOURCE);
    int count = 0;
    while (resources.hasMoreElements()) {
      verifyContent(resources.nextElement().openStream(), "getResources", failures);
      count++;
    }
    if (count != 1) {
      failures.add("getResources returned " + count + " resources instead of 1");
    }

    verifyContent(loader.getResourceAsStream(RESOURCE), "getResourceAsStream", failures);
    if (!failures.isEmpty()) {
      throw new AssertionError("RESOURCE assertions failed: " + failures);
    }
    System.out.println("RESOURCE getResource OK");
    System.out.println("RESOURCE getResources OK");
    System.out.println("RESOURCE getResourceAsStream OK");
  }

  private static void verifyContent(InputStream stream, String method, List<String> failures)
      throws IOException {
    if (stream == null) {
      failures.add(method + " returned null");
      return;
    }
    try (InputStreamReader reader = new InputStreamReader(stream, UTF_8)) {
      StringBuilder output = new StringBuilder();
      char[] buffer = new char[256];
      int length;
      while ((length = reader.read(buffer)) != -1) {
        output.append(buffer, 0, length);
      }
      String actual = output.toString();
      if (!CONTENT.equals(actual)) {
        failures.add(method + " returned unexpected content: " + actual);
      }
    }
  }

  private ResourceApplication() {}
}
