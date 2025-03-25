/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.NocodeInstrumentationRules;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.ArrayList;
import java.util.List;

@AutoService(InstrumentationModule.class)
public final class NocodeInstrumentationModule extends InstrumentationModule {

  public NocodeInstrumentationModule() {
    super("nocode");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    List<TypeInstrumentation> answer = new ArrayList<>();
    for (NocodeInstrumentationRules.Rule rule : NocodeInstrumentationRules.getGlobalRules()) {
      answer.add(new NocodeInstrumentation(rule));
    }
    // ensure that there is at least one instrumentation so that muzzle reference collection could
    // work
    if (answer.isEmpty()) {
      answer.add(new NocodeInstrumentation(null));
    }
    return answer;
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.javaagent") && className.contains("Nocode");
  }

  // If nocode instrumentation is added to something with existing auto-instrumentation,
  // it would generally be better to run the nocode bits after the "regular" bits.
  // E.g., if we want to add nocode to a servlet call, then we want to make sure that
  // the "standard" servlet instrumentation runs first to handle context propagation, etc.
  @Override
  public int order() {
    return Integer.MAX_VALUE;
  }
}
