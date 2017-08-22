package com.datadoghq.trace.agent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DDJavaAgentInfo {
  public static final String VERSION;

  static {
    String v;
    try {
      final StringBuffer sb = new StringBuffer();

      final BufferedReader br =
          new BufferedReader(
              new InputStreamReader(
                  DDJavaAgentInfo.class.getResourceAsStream("dd-java-agent.version"), "UTF-8"));
      for (int c = br.read(); c != -1; c = br.read()) sb.append((char) c);

      v = sb.toString().trim();
    } catch (final Exception e) {
      v = "unknown";
    }
    VERSION = v;
    log.info("dd-java-agent - version: {}", v);
  }
}
