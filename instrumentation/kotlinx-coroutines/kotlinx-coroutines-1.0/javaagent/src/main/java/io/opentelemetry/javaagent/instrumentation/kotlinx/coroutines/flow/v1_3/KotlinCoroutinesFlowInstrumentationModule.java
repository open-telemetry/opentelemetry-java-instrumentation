/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinx.coroutines.flow.v1_3;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KotlinCoroutinesFlowInstrumentationModule extends InstrumentationModule {

  public KotlinCoroutinesFlowInstrumentationModule() {
    super(
        "kotlinx-coroutines",
        expandDeprecatedNames(
            "kotlinx-coroutines-1.0",
            "kotlinx-coroutines-1.0-flow",
            // Resolve every current name before checking either deprecated Flow alias.
            "kotlinx-coroutines|deprecated:kotlinx-coroutines-flow",
            "kotlinx-coroutines-1.0|deprecated:kotlinx-coroutines-flow-1.3"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AbstractFlowInstrumentation());
  }
}
