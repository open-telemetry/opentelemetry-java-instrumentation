/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.reflection;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ReflectionInstrumentationModule extends InstrumentationModule {
  public ReflectionInstrumentationModule() {
    super("internal-reflection");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // internal instrumentations are always enabled by default
    return true;
  }

  @Override
  public boolean isIndyModule() {
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClassInstrumentation(), new ReflectionInstrumentation());
  }
}
