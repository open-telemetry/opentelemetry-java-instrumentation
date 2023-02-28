/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class TestUtil {

  public static byte[] readFileBytes(String resourceName) throws IOException {
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

  public static String readFile(String resourceName) throws IOException {
    return new String(readFileBytes(resourceName), UTF_8);
  }

  private TestUtil() {}
}
