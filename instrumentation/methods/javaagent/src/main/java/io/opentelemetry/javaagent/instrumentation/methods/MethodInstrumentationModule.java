/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MethodInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  private final List<TypeInstrumentation> typeInstrumentations;

  public MethodInstrumentationModule() {
    super("methods");
    typeInstrumentations = createInstrumentations();
  }

  private static List<TypeInstrumentation> createInstrumentations() {
    List<TypeInstrumentation> list =
        MethodsConfig.parse(
            DeclarativeConfigUtil.getStructured(GlobalOpenTelemetry.get(), "java", empty())
                .getStructured("methods", empty()));
    // ensure that there is at least one instrumentation so that muzzle reference collection could
    // work
    if (list.isEmpty()) {
      return singletonList(
          new MethodInstrumentation(null, singletonMap(SpanKind.INTERNAL, emptyList())));
    }
    return list;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return typeInstrumentations;
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
