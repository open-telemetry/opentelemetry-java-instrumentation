/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v3_3_0;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.IJobHandler;

class SimpleCustomizedHandler extends IJobHandler {

  @Override
  public void execute() {
    XxlJobHelper.handleSuccess();
  }
}
