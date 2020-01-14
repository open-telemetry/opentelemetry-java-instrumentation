package io.opentelemetry.auto.tooling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VersionLogger {

  /** Log version strings for trace-api, and java-agent */
  public static void logAllVersions() {
    log.info(
        "trace-api - version: {}",
        getVersionString(Utils.getAgentClassLoader().getResourceAsStream("trace-api.version")));
    log.info(
        "java-agent - version: {}",
        getVersionString(
            ClassLoader.getSystemClassLoader().getResourceAsStream("java-agent.version")));
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
