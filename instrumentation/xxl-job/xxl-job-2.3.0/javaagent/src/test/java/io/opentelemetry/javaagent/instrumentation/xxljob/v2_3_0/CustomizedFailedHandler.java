/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.IJobHandler;

class CustomizedFailedHandler extends IJobHandler {

  @Override
  public void execute() throws Exception {
    XxlJobHelper.handleFail();
  }
}
