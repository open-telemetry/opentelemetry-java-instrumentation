package datadog.trace.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DDTraceApiInfo {
  public static final String VERSION;

  static {
    String v;
    try {
      final StringBuilder sb = new StringBuilder();

      final BufferedReader br =
          new BufferedReader(
              new InputStreamReader(
                  DDTraceApiInfo.class.getResourceAsStream("/dd-trace-api.version"), "UTF-8"));
      for (int c = br.read(); c != -1; c = br.read()) sb.append((char) c);

      v = sb.toString().trim();
    } catch (final Exception e) {
      v = "unknown";
    }
    VERSION = v;
    log.info("dd-trace-api - version: {}", v);
  }

  public static void main(final String... args) {
    System.out.println(VERSION);
  }
}
