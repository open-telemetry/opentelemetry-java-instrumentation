/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import javax.batch.api.BatchProperty
import javax.batch.api.chunk.ItemReader
import javax.inject.Inject

class TestPartitionedItemReader implements ItemReader {
  @Inject
  @BatchProperty(name = "start")
  String startStr
  @Inject
  @BatchProperty(name = "end")
  String endStr

  int start
  int end

  @Override
  void open(Serializable checkpoint) throws Exception {
    start = Integer.parseInt(startStr)
    end = Integer.parseInt(endStr)
  }

  @Override
  void close() throws Exception {
  }

  @Override
  Object readItem() throws Exception {
    if (start >= end) {
      return null
    }
    return String.valueOf(start++)
  }

  @Override
  Serializable checkpointInfo() throws Exception {
    return null
  }
}
