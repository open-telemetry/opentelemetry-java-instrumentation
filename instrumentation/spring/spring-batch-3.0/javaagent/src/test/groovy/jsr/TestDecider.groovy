/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import javax.batch.api.Decider
import javax.batch.runtime.StepExecution

class TestDecider implements Decider {
  @Override
  String decide(StepExecution[] stepExecutions) throws Exception {
    "LEFT"
  }
}
