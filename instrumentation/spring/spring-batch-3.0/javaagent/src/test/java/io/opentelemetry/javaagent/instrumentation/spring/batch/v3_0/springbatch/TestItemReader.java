/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.batch.item.support.ListItemReader;

public class TestItemReader extends ListItemReader<String> {
  public TestItemReader() {
    super(IntStream.range(0, 13).mapToObj(String::valueOf).collect(Collectors.toList()));
  }
}
