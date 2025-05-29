/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_1_2;

import com.xxl.job.core.biz.model.ReturnT;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobHelper;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;

public final class XxlJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.xxl-job-2.1.2";
  private static final Instrumenter<XxlJobProcessRequest, Void> INSTRUMENTER =
      XxlJobInstrumenterFactory.create(INSTRUMENTATION_NAME);
  private static final XxlJobHelper HELPER =
      XxlJobHelper.create(
          INSTRUMENTER,
          object -> {
            if (object != null && (object instanceof ReturnT)) {
              ReturnT<?> result = (ReturnT<?>) object;
              return result.getCode() == ReturnT.FAIL_CODE;
            }
            return false;
          });

  public static XxlJobHelper helper() {
    return HELPER;
  }

  private XxlJobSingletons() {}
}
