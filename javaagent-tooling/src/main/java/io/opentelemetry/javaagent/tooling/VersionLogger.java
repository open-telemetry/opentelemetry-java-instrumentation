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

package io.opentelemetry.javaagent.tooling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionLogger {

  private static final Logger log = LoggerFactory.getLogger(VersionLogger.class);

  /** Log version string for java-agent */
  public static void logAllVersions() {
    log.info("opentelemetry-javaagent - version: {}", AgentVersion.VERSION);
    if (log.isDebugEnabled()) {
      log.debug(
          "Running on Java {}. JVM {} - {} - {}",
          System.getProperty("java.version"),
          System.getProperty("java.vm.name"),
          System.getProperty("java.vm.vendor"),
          System.getProperty("java.vm.version"));
    }
  }

  private static String getVersionString(InputStream stream) {
    String v;
    try {
      StringBuilder sb = new StringBuilder();
      BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
      for (int c = br.read(); c != -1; c = br.read()) {
        sb.append((char) c);
      }

      v = sb.toString().trim();
    } catch (Exception e) {
      log.error("failed to read version stream", e);
      v = "unknown";
    } finally {
      try {
        if (null != stream) {
          stream.close();
        }
      } catch (IOException e) {
      }
    }
    return v;
  }
}
