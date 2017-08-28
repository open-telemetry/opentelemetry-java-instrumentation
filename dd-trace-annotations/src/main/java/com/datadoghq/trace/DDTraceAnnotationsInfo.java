package com.datadoghq.trace;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DDTraceAnnotationsInfo {
  public static final String VERSION;

  static {
    String v;
    try {
      final StringBuffer sb = new StringBuffer();

      final BufferedReader br =
          new BufferedReader(
              new InputStreamReader(
                  DDTraceAnnotationsInfo.class.getResourceAsStream("/dd-trace-annotations.version"),
                  "UTF-8"));
      for (int c = br.read(); c != -1; c = br.read()) sb.append((char) c);

      v = sb.toString().trim();
    } catch (final Exception e) {
      v = "unknown";
    }
    VERSION = v;
    log.info("dd-trace-annotations - version: {}", v);
  }

  public static void main(String... args) {
    System.out.println(VERSION);
  }
}
