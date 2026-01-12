/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardviews;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class DropwizardInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public DropwizardInstrumentationModule() {
    super("dropwizard-views", "dropwizard-views-0.7");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DropwizardRendererInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
