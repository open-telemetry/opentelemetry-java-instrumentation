/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_1;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JwsInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public JwsInstrumentationModule() {
    super("jaxws-jws-api", "jaxws-jws-api-1.1", "jaxws");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new JwsAnnotationsInstrumentation());
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // this instrumentation only produces controller telemetry
    return super.defaultEnabled(config)
        && config.getBoolean(
            "otel.instrumentation.common.experimental.controller-telemetry.enabled", false);
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
