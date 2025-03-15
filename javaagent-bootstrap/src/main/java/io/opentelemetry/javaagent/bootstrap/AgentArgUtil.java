/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import javax.annotation.Nullable;

public class AgentArgUtil {

  private AgentArgUtil() {}

  @SuppressWarnings("SystemOut")
  public static void setSystemProperties(@Nullable String agentArgs) {
    boolean debug = false;
    if (agentArgs != null && !agentArgs.isEmpty()) {
      for (String option : agentArgs.split(";")) {
        String[] keyValue = option.split("=");
        if (keyValue.length == 2) {
          String key = keyValue[0];
          String value = keyValue[1];
          System.setProperty(key, value);
          if (key.equals("otel.javaagent.debug")) {
            debug = Boolean.parseBoolean(value);
          }
          if (debug) {
            System.out.println("Setting property [" + key + "] = " + value);
          }
        }
      }
    }
  }
}
