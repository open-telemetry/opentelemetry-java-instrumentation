/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

record StartupTimings(double springSeconds, double jvmSeconds) {
  private static final Pattern STARTUP_LINE =
      Pattern.compile(
          ".*Started\\s+SpringbootApplication\\s+in\\s+"
              + "([0-9]+(?:\\.[0-9]+)?)\\s+seconds\\s+"
              + "\\(JVM running for\\s+([0-9]+(?:\\.[0-9]+)?)(?:\\s+seconds)?\\).*");

  static StartupTimings parse(String logs) {
    StartupTimings result = null;
    for (String line : logs.lines().toList()) {
      if (!line.contains("Started SpringbootApplication")) {
        continue;
      }
      Matcher matcher = STARTUP_LINE.matcher(line);
      if (!matcher.matches()) {
        throw new IllegalArgumentException("Malformed Spring startup timing line: " + line);
      }
      if (result != null) {
        throw new IllegalArgumentException("Multiple Spring startup timing lines");
      }
      double springSeconds = Double.parseDouble(matcher.group(1));
      double jvmSeconds = Double.parseDouble(matcher.group(2));
      if (!Double.isFinite(springSeconds)
          || !Double.isFinite(jvmSeconds)
          || springSeconds <= 0
          || jvmSeconds <= 0
          || jvmSeconds < springSeconds) {
        throw new IllegalArgumentException("Invalid Spring startup timings: " + line);
      }
      result = new StartupTimings(springSeconds, jvmSeconds);
    }
    if (result == null) {
      throw new IllegalArgumentException("Missing Spring startup timing line");
    }
    return result;
  }
}
