/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client.DefaultCallResponseSpecInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client.DefaultStreamResponseSpecInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool.DefaultToolCallingManagerInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool.ToolCallbackInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringAiInstrumentationModule extends InstrumentationModule {

  public SpringAiInstrumentationModule() {
    super("spring-ai", "spring-ai-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DefaultCallResponseSpecInstrumentation(),
        new DefaultStreamResponseSpecInstrumentation(),
        new ToolCallbackInstrumentation(),
        new DefaultToolCallingManagerInstrumentation());
  }
}
