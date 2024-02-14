/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.pubsub.publisher.PubsubPublisherInstrumentation;
import io.opentelemetry.javaagent.instrumentation.pubsub.subscriber.PubsubSubscriberInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PubsubInstrumentationModule extends InstrumentationModule {
  public PubsubInstrumentationModule() {
    super("pubsub", "pubsub-1");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new PubsubPublisherInstrumentation(), new PubsubSubscriberInstrumentation());
  }
}
