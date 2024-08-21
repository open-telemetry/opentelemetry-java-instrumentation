/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import org.springframework.batch.item.ItemReader

import java.util.concurrent.atomic.AtomicReference

class SingleItemReader implements ItemReader<String> {
  final AtomicReference<String> item = new AtomicReference<>("42")

  @Override
  String read() {
    return item.getAndSet(null)
  }
}
