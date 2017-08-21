package com.datadoghq.trace;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DDTraceInfo {

  public static final String JAVA_VERSION = System.getProperty("java.version", "unknown");
  public static final String JAVA_VM_NAME = System.getProperty("java.vm.name", "unknown");

  public static final String VERSION;

  static {
    String v;
    try {
      final StringBuffer sb = new StringBuffer();

      final BufferedReader br =
          new BufferedReader(
              new InputStreamReader(
                  DDTraceInfo.class.getResourceAsStream("dd-trace.version"), "UTF-8"));
      for (int c = br.read(); c != -1; c = br.read()) sb.append((char) c);

      v = sb.toString().trim();
    } catch (final Exception e) {
      v = "unknown";
    }
    VERSION = v;
    log.debug("dd-trace - version: {}", v);
  }
}
