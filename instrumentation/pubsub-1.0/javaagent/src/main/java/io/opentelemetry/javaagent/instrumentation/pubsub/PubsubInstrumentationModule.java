/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public final class PubsubInstrumentationModule extends InstrumentationModule {
  public PubsubInstrumentationModule() {
    super(PubsubSingletons.instrumentationName, "pubsub-1.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("com.opentelemetry.javaagent.instrumentation.pubsub");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new PubsubPublisherInstrumentation(), new PubsubSubscriberInstrumentation());
  }
}
