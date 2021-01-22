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
  int start
  int end

  @Override
  String read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
    if (start >= end) {
      return null
    }
    return String.valueOf(start++)
  }

  @Override
  void open(ExecutionContext executionContext) throws ItemStreamException {
    start = executionContext.getInt("start")
    end = executionContext.getInt("end")
  }

  @Override
  void update(ExecutionContext executionContext) throws ItemStreamException {
  }

  @Override
  void close() throws ItemStreamException {
  }
}
