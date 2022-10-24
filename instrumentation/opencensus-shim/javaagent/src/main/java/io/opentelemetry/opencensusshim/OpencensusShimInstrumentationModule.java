/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opencensusshim;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OpencensusShimInstrumentationModule extends InstrumentationModule {

  public OpencensusShimInstrumentationModule() {
    super("opencensus-shim");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenTelemetryCtxInstrumentation());
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals("io.opentelemetry.opencensusshim.ContextExtractor");
  }
}
