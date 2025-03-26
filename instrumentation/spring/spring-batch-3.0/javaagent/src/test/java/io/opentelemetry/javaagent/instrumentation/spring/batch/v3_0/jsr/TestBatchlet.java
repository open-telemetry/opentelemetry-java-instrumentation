/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.inject.Inject;

class TestBatchlet implements Batchlet {
  @Inject
  @BatchProperty(name = "fail")
  private String fail;

  @Override
  public String process() {
    if (fail != null && Integer.valueOf(fail) == 1) {
      throw new IllegalStateException("fail");
    }

    return "FINISHED";
  }

  @Override
  public void stop() {}

  public String getFail() {
    return fail;
  }

  public void setFail(String fail) {
    this.fail = fail;
  }
}
