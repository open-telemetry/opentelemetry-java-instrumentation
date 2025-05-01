/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_21;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class NatsInstrumentationModule extends InstrumentationModule {

  public NatsInstrumentationModule() {
    super("nats", "nats-2.21");
  }

  // TODO classLoaderMatcher

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new ConnectionInstrumentation());
  }
}
