/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class PowerJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.powerjob-4.0";
  private static final Instrumenter<PowerJobProcessRequest, Void> INSTRUMENTER =
      PowerJobInstrumenterFactory.create(INSTRUMENTATION_NAME);
  private static final PowerJobHelper HELPER =
      PowerJobHelper.create(
          INSTRUMENTER,
          processResult -> {
            if (processResult != null) {
              return !processResult.isSuccess();
            }
            return false;
          });

  public static PowerJobHelper helper() {
    return HELPER;
  }

  private PowerJobSingletons() {}
}
