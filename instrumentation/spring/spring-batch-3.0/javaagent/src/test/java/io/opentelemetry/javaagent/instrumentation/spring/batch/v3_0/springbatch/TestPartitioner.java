/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

public class TestPartitioner implements Partitioner {
  @Override
  public Map<String, ExecutionContext> partition(int gridSize) {
    Map<String, ExecutionContext> map = new HashMap<>();
    map.put("partition0", new ExecutionContext(ImmutableMap.of("start", 0, "end", 8)));
    map.put("partition1", new ExecutionContext(ImmutableMap.of("start", 8, "end", 13)));
    return map;
  }
}
