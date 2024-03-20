/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.xxljob;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;

public class CustomizedFailedHandler extends IJobHandler {

  @Override
  public ReturnT<String> execute(String s) throws Exception {
    return FAIL;
  }
}
