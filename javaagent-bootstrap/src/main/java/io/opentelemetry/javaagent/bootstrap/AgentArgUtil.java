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
      String[] options = agentArgs.split(";");
      for (String option : options) {
        String[] keyValue = option.split("=");
        if (keyValue.length == 2) {
          if (keyValue[0].equals("otel.javaagent.debug")) {
            debug = Boolean.parseBoolean(keyValue[1]);
          System.setProperty(keyValue[0], keyValue[1]);
          if (debug) {
            System.out.println("Setting property [" + keyValue[0] + "] = " + keyValue[1]);
          }
        }
      }
    }
  }
}
