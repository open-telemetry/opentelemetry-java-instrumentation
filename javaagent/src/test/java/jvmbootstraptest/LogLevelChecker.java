/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

public class LogLevelChecker {
  // returns an exception if logs are not in DEBUG
  public static void main(String[] args) {

    String str =
        System.getProperty("io.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel");

    if ((str == null) || (str != null && !str.equalsIgnoreCase("debug"))) {
      throw new RuntimeException("debug mode not set");
    }
  }
}
