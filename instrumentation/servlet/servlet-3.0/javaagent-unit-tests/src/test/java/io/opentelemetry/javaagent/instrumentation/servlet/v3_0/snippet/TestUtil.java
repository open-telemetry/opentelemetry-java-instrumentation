/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class TestUtil {

  public static byte[] readFileBytes(String resourceName, Charset charsetName) throws IOException {
    return readFile(resourceName).getBytes(charsetName);
  }

  public static String readFile(String resourceName) throws IOException {
    InputStream in =
        SnippetPrintWriterTest.class.getClassLoader().getResourceAsStream(resourceName);
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = in.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return result.toString(StandardCharsets.UTF_8.name());
  }

  private TestUtil() {}
}
