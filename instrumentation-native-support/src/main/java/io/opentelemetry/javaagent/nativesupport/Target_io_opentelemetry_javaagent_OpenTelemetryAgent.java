/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.opentelemetry.javaagent.OpenTelemetryAgent;
import io.opentelemetry.javaagent.bootstrap.AgentInitializer;

import java.lang.instrument.Instrumentation;

@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
@TargetClass(OpenTelemetryAgent.class)
public final class Target_io_opentelemetry_javaagent_OpenTelemetryAgent {

  @SuppressWarnings("UnusedMethod")
  @Substitute
  private static void startAgent(Instrumentation inst, boolean fromPremain) {
    try {
      AgentInitializer.initialize(inst, null, fromPremain);
    } catch (Throwable ex) {
      // Don't rethrow.  We don't have a log manager here, so just print.
      System.err.println("ERROR " + OpenTelemetryAgent.class.getName());
      ex.printStackTrace();
    }
  }
}
