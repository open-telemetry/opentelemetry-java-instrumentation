/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.tooling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VersionLogger {

  /** Log version string for java-agent */
  public static void logAllVersions() {
    log.info(
        "opentelemetry-auto - version: {}",
        getVersionString(
            ClassLoader.getSystemClassLoader().getResourceAsStream("opentelemetry-auto.version")));
    log.debug(
        "Running on Java {}. JVM {} - {} - {}",
        System.getProperty("java.version"),
        System.getProperty("java.vm.name"),
        System.getProperty("java.vm.vendor"),
        System.getProperty("java.vm.version"));
  }

  private static String getVersionString(final InputStream stream) {
    String v;
    try {
      final StringBuilder sb = new StringBuilder();
      final BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
      for (int c = br.read(); c != -1; c = br.read()) {
        sb.append((char) c);
      }

      v = sb.toString().trim();
    } catch (final Exception e) {
      log.error("failed to read version stream", e);
      v = "unknown";
    } finally {
      try {
        if (null != stream) {
          stream.close();
        }
      } catch (final IOException e) {
      }
    }
    return v;
  }
}
