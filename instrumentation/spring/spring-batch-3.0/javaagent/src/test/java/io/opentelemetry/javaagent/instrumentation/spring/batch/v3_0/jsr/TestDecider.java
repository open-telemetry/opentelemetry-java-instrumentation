/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import javax.batch.api.Decider;
import javax.batch.runtime.StepExecution;

class TestDecider implements Decider {
  @Override
  public String decide(StepExecution[] stepExecutions) {
    return "LEFT";
  }
}
