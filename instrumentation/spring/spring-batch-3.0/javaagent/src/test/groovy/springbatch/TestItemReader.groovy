/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import java.util.stream.Collectors
import java.util.stream.IntStream
import org.springframework.batch.item.support.ListItemReader

class TestItemReader extends ListItemReader<String> {
  TestItemReader() {
    super(IntStream.range(0, 13).mapToObj(String.&valueOf).collect(Collectors.toList()) as List<String>)
  }
}