/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import org.springframework.batch.item.ItemProcessor

class TestItemProcessor implements ItemProcessor<String, Integer> {
  @Override
  Integer process(String item) throws Exception {
    Integer.parseInt(item)
  }
}