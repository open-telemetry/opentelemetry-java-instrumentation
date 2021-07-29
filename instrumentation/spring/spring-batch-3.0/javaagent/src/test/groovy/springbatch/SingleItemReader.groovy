/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import java.util.concurrent.atomic.AtomicReference
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.NonTransientResourceException
import org.springframework.batch.item.ParseException
import org.springframework.batch.item.UnexpectedInputException

class SingleItemReader implements ItemReader<String> {
  final AtomicReference<String> item = new AtomicReference<>("42")

  @Override
  String read() {
    return item.getAndSet(null)
  }
}
