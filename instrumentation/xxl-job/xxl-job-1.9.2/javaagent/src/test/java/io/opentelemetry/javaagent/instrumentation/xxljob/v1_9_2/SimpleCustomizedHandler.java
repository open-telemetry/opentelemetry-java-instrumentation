/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v1_9_2;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;

class SimpleCustomizedHandler extends IJobHandler {

  @Override
  public ReturnT<String> execute(String s) throws Exception {
    return new ReturnT<>("Hello World");
  }
}
