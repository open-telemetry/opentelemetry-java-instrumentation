/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v1_9_2;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.glue.GlueTypeEnum;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobHelper;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;

public final class XxlJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.xxl-job-1.9.2";
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

  @SuppressWarnings({"Unused", "ReturnValueIgnored"})
  private static void limitSupportedVersions() {
    // GLUE_POWERSHELL was added in 1.9.2. Using this constant here ensures that muzzle will disable
    // this instrumentation on earlier versions where this constant does not exist.
    GlueTypeEnum.GLUE_POWERSHELL.name();
  }

  private XxlJobSingletons() {}
}
