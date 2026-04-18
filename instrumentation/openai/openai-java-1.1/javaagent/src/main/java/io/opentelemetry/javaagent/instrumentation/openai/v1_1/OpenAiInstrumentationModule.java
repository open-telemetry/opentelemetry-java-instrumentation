/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openai.v1_1;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OpenAiInstrumentationModule extends InstrumentationModule {
  public OpenAiInstrumentationModule() {
    super("openai-java", "openai-java-1.1", "openai");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new OpenAiClientInstrumentation(), new OpenAiClientAsyncInstrumentation());
  }
}
