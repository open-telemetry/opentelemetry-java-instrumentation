/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import org.springframework.batch.item.support.ListItemReader

import java.util.stream.Collectors
import java.util.stream.IntStream

class TestItemReader extends ListItemReader<String> {
  TestItemReader() {
    super(IntStream.range(0, 13).mapToObj(String.&valueOf).collect(Collectors.toList()) as List<String>)
  }
}