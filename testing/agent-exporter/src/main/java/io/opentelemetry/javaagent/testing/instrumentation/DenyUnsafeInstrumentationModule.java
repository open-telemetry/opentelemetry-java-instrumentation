/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.instrumentation;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

/**
 * Instrument libraries used for testing to remove usages of unsafe so we could test the agent with
 * --sun-misc-unsafe-memory-access=deny
 */
@AutoService(InstrumentationModule.class)
public class DenyUnsafeInstrumentationModule extends InstrumentationModule {

  public DenyUnsafeInstrumentationModule() {
    super("deny-unsafe");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ProtoUtf8UnsafeProcessorInstrumentation(),
        new ExceptionSamplerInstrumentation(),
        new ServerInstrumentation(),
        new DefaultStreamMessageInstrumentation());
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    return false;
  }
}
