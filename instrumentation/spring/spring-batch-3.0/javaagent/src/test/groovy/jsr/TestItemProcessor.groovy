/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import javax.batch.api.chunk.ItemProcessor

class TestItemProcessor implements ItemProcessor {
  @Override
  Object processItem(Object item) throws Exception {
    Integer.parseInt(item as String)
  }
}