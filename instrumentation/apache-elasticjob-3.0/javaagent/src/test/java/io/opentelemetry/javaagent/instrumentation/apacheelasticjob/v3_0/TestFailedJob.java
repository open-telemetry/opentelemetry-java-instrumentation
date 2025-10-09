/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import org.apache.shardingsphere.elasticjob.api.ShardingContext;
import org.apache.shardingsphere.elasticjob.simple.job.SimpleJob;

public class TestFailedJob implements SimpleJob {

  @Override
  public void execute(ShardingContext context) {
    // Simulate a failed job by throwing an exception
    throw new RuntimeException("Simulated job failure for testing");
  }
}
