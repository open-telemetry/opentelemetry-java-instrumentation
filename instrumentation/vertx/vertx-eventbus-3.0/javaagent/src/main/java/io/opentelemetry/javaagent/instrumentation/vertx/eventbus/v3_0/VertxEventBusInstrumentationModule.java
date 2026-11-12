/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VertxEventBusInstrumentationModule extends InstrumentationModule {

  public VertxEventBusInstrumentationModule() {
    super("vertx-eventbus-3.0", "vertx-eventbus", "vertx");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 3.0
    return hasClassesNamed("io.vertx.core.eventbus.impl.EventBusImpl");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new EventBusImplInstrumentation(),
        new EventBusInstrumentation(),
        new CreateMessageInstrumentation(),
        new MessageProducerInstrumentation(),
        new MessageConsumerInstrumentation(),
        new ReplyHandlerInstrumentation());
  }
}
