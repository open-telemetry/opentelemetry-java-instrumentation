/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

public enum ElasticJobType {
  SIMPLE,
  DATAFLOW,
  HTTP,
  SCRIPT,
  UNKNOWN;

  public static ElasticJobType fromExecutor(Object jobItemExecutor) {
    if (jobItemExecutor == null) {
      return UNKNOWN;
    }

    switch (jobItemExecutor.getClass().getSimpleName()) {
      case "HttpJobExecutor":
        return HTTP;
      case "ScriptJobExecutor":
        return SCRIPT;
      case "SimpleJobExecutor":
        return SIMPLE;
      case "DataflowJobExecutor":
        return DATAFLOW;
      default:
        return UNKNOWN;
    }
  }
}
