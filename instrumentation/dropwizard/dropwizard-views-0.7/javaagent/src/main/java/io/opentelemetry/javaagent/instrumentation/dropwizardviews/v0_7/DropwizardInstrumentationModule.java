/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardviews.v0_7;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class DropwizardInstrumentationModule extends InstrumentationModule {
  public DropwizardInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "dropwizard-views-0.7" : "dropwizard-views",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"dropwizard-views"}
            : new String[] {"dropwizard-views-0.7"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DropwizardRendererInstrumentation());
  }
}
