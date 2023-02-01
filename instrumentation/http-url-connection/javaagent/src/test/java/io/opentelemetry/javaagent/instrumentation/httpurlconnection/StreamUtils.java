/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

final class StreamUtils {
  static List<String> readLines(InputStream stream) throws IOException {
    List<String> lines = new ArrayList<>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
    while (reader.ready()) {
      String line = reader.readLine();
      if (!Strings.isNullOrEmpty(line)) {
        lines.add(line);
      }
    }

    return lines;
  }

  private StreamUtils() {}
}
