/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

public class TestPartitioner implements Partitioner {
  @Override
  public Map<String, ExecutionContext> partition(int gridSize) {
    Map<String, ExecutionContext> map = new HashMap<>();
    Map<String, Object> partition0 = new HashMap<>();
    partition0.put("start", 0);
    partition0.put("end", 8);
    map.put("partition0", new ExecutionContext(partition0));
    Map<String, Object> partition1 = new HashMap<>();
    partition1.put("start", 8);
    partition1.put("end", 13);
    map.put("partition1", new ExecutionContext(partition1));
    return map;
  }
}
