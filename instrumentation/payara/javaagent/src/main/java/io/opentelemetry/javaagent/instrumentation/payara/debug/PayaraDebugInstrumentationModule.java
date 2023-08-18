/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.payara.debug;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

/** This module is only intended for debugging flaky payara async request smoke test. */
@AutoService(InstrumentationModule.class)
public class PayaraDebugInstrumentationModule extends InstrumentationModule {

  public PayaraDebugInstrumentationModule() {
    super("payara-debug", "payara");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new AsyncContextImplInstrumentation(), new HandlerInstrumentation());
  }
}
