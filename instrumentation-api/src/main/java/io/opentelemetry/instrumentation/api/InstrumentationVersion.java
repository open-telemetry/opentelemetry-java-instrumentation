/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api;

import java.io.InputStream;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstrumentationVersion {
  private static final Logger log = LoggerFactory.getLogger(InstrumentationVersion.class);
  private static final String VERSION_FILE =
      "/io/opentelemetry/javaagent/shaded/instrumentation/api/instrumentation-version.txt";

  public static final String VERSION = findVersion();

  private static String findVersion() {
    try {
      InputStream in = InstrumentationVersion.class.getResourceAsStream(VERSION_FILE);
      char[] buff = new char[256];
      int ct = new InputStreamReader(in).read(buff);
      return new String(buff, 0, ct);
    } catch (Exception e) {
      log.warn("Error reading instrumentation version", e);
      return "UNKNOWN";
    }
  }
}
