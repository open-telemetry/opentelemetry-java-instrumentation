/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

public final class JobTypeHelper {

  public static String determineJobTypeFromExecutor(Object jobItemExecutor) {
    if (jobItemExecutor == null) {
      return "UNKNOWN";
    } else {
      switch (jobItemExecutor.getClass().getSimpleName()) {
        case "HttpJobExecutor":
          return "HTTP";
        case "ScriptJobExecutor":
          return "SCRIPT";
        case "SimpleJobExecutor":
          return "SIMPLE";
        case "DataflowJobExecutor":
          return "DATAFLOW";
        default:
          return "UNKNOWN";
      }
    }
  }

  private JobTypeHelper() {}
}

