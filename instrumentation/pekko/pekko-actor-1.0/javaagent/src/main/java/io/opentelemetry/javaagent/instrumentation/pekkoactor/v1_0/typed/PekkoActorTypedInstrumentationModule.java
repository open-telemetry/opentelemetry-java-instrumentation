/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PekkoActorTypedInstrumentationModule extends InstrumentationModule {
  public PekkoActorTypedInstrumentationModule() {
    super("pekko-actor-1.0", "pekko-actor", "pekko");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new PekkoStashBufferNodeInstrumentation(), new PekkoStashBufferInstrumentation());
  }
}
