/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws.v2_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringWsInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public SpringWsInstrumentationModule() {
    super("spring-ws", "spring-ws-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new AnnotatedMethodInstrumentation());
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // this instrumentation only produces controller telemetry
    return super.defaultEnabled(config) && ExperimentalConfig.get().controllerTelemetryEnabled();
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
