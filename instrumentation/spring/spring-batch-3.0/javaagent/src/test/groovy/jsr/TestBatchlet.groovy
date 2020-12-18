/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import javax.batch.api.BatchProperty
import javax.batch.api.Batchlet
import javax.inject.Inject

class TestBatchlet implements Batchlet {
  @Inject
  @BatchProperty(name = "fail")
  String fail

  @Override
  String process() throws Exception {
    if (fail != null && Integer.valueOf(fail) == 1) {
      throw new RuntimeException("fail")
    }
    return "FINISHED"
  }

  @Override
  void stop() throws Exception {
  }
}
