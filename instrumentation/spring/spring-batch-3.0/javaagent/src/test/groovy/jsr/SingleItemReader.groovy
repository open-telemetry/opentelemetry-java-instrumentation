/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import java.util.concurrent.atomic.AtomicReference
import javax.batch.api.chunk.ItemReader

class SingleItemReader implements ItemReader {
  final AtomicReference<String> item = new AtomicReference<>("42")

  @Override
  void open(Serializable serializable) throws Exception {
  }

  @Override
  void close() throws Exception {
  }

  @Override
  Object readItem() throws Exception {
    return item.getAndSet(null)
  }

  @Override
  Serializable checkpointInfo() throws Exception {
    return null
  }
}
