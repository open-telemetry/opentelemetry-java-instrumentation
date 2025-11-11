/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import static io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobType.DATAFLOW;
import static io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobType.HTTP;
import static io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobType.SCRIPT;
import static io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobType.SIMPLE;
import static io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobType.UNKNOWN;

public final class JobTypeHelper {

  public static ElasticJobType determineJobTypeFromExecutor(Object jobItemExecutor) {
    if (jobItemExecutor == null) {
      return UNKNOWN;
    } else {
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

  private JobTypeHelper() {}
}
