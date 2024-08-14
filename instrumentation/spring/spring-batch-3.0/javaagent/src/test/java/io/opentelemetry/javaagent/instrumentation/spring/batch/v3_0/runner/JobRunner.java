/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner;

import static java.util.Collections.emptyMap;

import java.util.Map;
import org.springframework.batch.core.JobParameter;

public interface JobRunner {
  void runJob(String jobName, Map<String, JobParameter> params);

  default void runJob(String jobName) {
    runJob(jobName, emptyMap());
  }
}
