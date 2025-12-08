/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.slf4jbridge;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Slf4jBridgeInstrumentationModule extends InstrumentationModule {

  public Slf4jBridgeInstrumentationModule() {
    super("internal-slf4j-bridge");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new LoggerFactoryInstrumentation());
  }
}
