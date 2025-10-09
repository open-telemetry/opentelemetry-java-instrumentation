/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticjob.v3_0;

import java.util.Arrays;
import java.util.List;
import org.apache.shardingsphere.elasticjob.api.ShardingContext;
import org.apache.shardingsphere.elasticjob.dataflow.job.DataflowJob;

public class TestDataflowJob implements DataflowJob<String> {

  @Override
  public List<String> fetchData(ShardingContext context) {
    // Simulate fetching data based on sharding item
    switch (context.getShardingItem()) {
      case 0:
        return Arrays.asList("data-0-1", "data-0-2");
      case 1:
        return Arrays.asList("data-1-1", "data-1-2");
      default:
        return Arrays.asList("data-default");
    }
  }

  @Override
  public void processData(ShardingContext shardingContext, List<String> data) {
    // Simulate processing
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
