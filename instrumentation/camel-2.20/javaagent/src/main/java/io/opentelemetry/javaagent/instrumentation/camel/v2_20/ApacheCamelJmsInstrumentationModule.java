/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * Muzzle evaluates all library references in an {@link InstrumentationModule} together. This module
 * isolates the optional JMS references so the core Camel instrumentation can load without the JMS
 * API.
 */
@AutoService(InstrumentationModule.class)
public class ApacheCamelJmsInstrumentationModule extends InstrumentationModule {

  public ApacheCamelJmsInstrumentationModule() {
    super("camel", "camel-2.20", "camel-jms");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CamelMuzzleInstrumentation(), new JmsMessageInstrumentation());
  }
}
