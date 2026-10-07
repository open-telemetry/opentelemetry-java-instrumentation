/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi.v5_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OshiInstrumentationModule extends InstrumentationModule {

  static final String OSHI_5_0_INSTRUMENTATION_NAME = "oshi-5.0";
  static final String OSHI_INSTRUMENTATION_NAME = "oshi";

  public OshiInstrumentationModule() {
    super(OSHI_5_0_INSTRUMENTATION_NAME, OSHI_INSTRUMENTATION_NAME);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new SystemInfoInstrumentation());
  }
}
