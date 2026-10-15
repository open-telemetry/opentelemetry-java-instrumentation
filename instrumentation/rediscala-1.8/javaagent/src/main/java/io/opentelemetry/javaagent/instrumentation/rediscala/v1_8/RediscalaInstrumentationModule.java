/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RediscalaInstrumentationModule extends InstrumentationModule {

  public RediscalaInstrumentationModule() {
    super("rediscala-1.8", "rediscala");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RediscalaClientActorLikeInstrumentation(),
        new RediscalaMutablePoolInstrumentation(),
        new RequestInstrumentation(),
        new TransactionBuilderInstrumentation(),
        new TransactionInstrumentation());
  }
}
