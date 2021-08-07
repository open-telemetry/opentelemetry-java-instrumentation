/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import java.util.concurrent.atomic.AtomicReference
import org.springframework.batch.item.ItemReader

class SingleItemReader implements ItemReader<String> {
  final AtomicReference<String> item = new AtomicReference<>("42")

  @Override
  String read() {
    return item.getAndSet(null)
  }
}
