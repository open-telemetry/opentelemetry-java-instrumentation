/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import org.apache.shardingsphere.elasticjob.api.ShardingContext;
import org.apache.shardingsphere.elasticjob.simple.job.SimpleJob;

public class TestSimpleJob implements SimpleJob {

  @Override
  public void execute(ShardingContext context) {
    // Simulate some work
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
