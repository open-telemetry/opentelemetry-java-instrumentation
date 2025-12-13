/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws.v2_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
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
  public boolean defaultEnabled() {
    // this instrumentation only produces controller telemetry
    return superDefaultEnabled()
        && DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "common",
                "controller_telemetry/development",
                "enabled")
            .orElse(false);
  }

  // This method can be removed and super.defaultEnabled() can be used instead once the deprecated
  // InstrumentationModule.defaultEnabled(ConfigProperties) is removed, at which point
  // InstrumentationModule.defaultEnabled() will no longer need to throw an exception.
  private static boolean superDefaultEnabled() {
    return DeclarativeConfigUtil.getBoolean(
            GlobalOpenTelemetry.get(), "java", "common", "default_enabled")
        .orElse(true);
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
