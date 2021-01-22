/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import org.springframework.batch.core.partition.support.Partitioner
import org.springframework.batch.item.ExecutionContext

class TestPartitioner implements Partitioner {
  @Override
  Map<String, ExecutionContext> partition(int gridSize) {
    return [
      "partition0": new ExecutionContext([
        "start": 0, "end": 8
      ]),
      "partition1": new ExecutionContext([
        "start": 8, "end": 13
      ])
    ]
  }
}
