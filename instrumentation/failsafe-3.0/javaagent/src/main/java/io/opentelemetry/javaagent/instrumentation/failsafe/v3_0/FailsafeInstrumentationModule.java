/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.failsafe.v3_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class FailsafeInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public FailsafeInstrumentationModule() {
    super("failsafe", "failsafe-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RetryPolicyInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
