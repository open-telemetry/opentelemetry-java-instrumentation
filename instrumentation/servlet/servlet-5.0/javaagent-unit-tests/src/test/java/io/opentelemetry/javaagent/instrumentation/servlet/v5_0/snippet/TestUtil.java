/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class TestUtil {

  protected static byte[] readFileAsBytes(String resourceName) throws IOException {
    InputStream in =
        SnippetPrintWriterTest.class.getClassLoader().getResourceAsStream(resourceName);
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = in.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return result.toByteArray();
  }

  protected static String readFileAsString(String resourceName) throws IOException {
    return new String(readFileAsBytes(resourceName), UTF_8);
  }

  private TestUtil() {}
}
