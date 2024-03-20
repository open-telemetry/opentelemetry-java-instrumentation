/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0;

import static com.xxl.job.core.context.XxlJobContext.HANDLE_COCE_SUCCESS;

import com.xxl.job.core.context.XxlJobContext;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobHelper;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;

public final class XxlJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.xxl-job-2.3.0";
  private static final Instrumenter<XxlJobProcessRequest, Void> INSTRUMENTER =
      XxlJobInstrumenterFactory.create(INSTRUMENTATION_NAME);
  private static final XxlJobHelper HELPER =
      XxlJobHelper.create(
          INSTRUMENTER,
          unused -> {
            // From 2.3.0, XxlJobContext is used to store the result of the job execution.
            XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
            if (xxlJobContext != null) {
              int handleCode = xxlJobContext.getHandleCode();
              return handleCode != HANDLE_COCE_SUCCESS;
            }
            return false;
          });

  public static XxlJobHelper helper() {
    return HELPER;
  }

  private XxlJobSingletons() {}
}
