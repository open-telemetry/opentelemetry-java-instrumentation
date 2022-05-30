/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OpenfeignInstrumentationModule extends InstrumentationModule {

  public OpenfeignInstrumentationModule() {
    super("openfeign", "openfeign-9.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new FeignSynchronousMethodHandlerInstrumentation(), new FeignClientInstrumentation());
  }
}
