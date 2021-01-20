/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemStream
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.NonTransientResourceException
import org.springframework.batch.item.ParseException
import org.springframework.batch.item.UnexpectedInputException

class TestPartitionedItemReader implements ItemReader<String>, ItemStream {
  ThreadLocal<Integer> start = new ThreadLocal<>()
  ThreadLocal<Integer> end = new ThreadLocal<>()

  @Override
  String read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
    if (start.get() >= end.get()) {
      return null
    }
    def value = start.get()
    start.set(value + 1)
    return String.valueOf(value)
  }

  @Override
  void open(ExecutionContext executionContext) throws ItemStreamException {
    start.set(executionContext.getInt("start"))
    end.set(executionContext.getInt("end"))
  }

  @Override
  void update(ExecutionContext executionContext) throws ItemStreamException {
  }

  @Override
  void close() throws ItemStreamException {
  }
}
