/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
@AutoService(InstrumentationModule.class)
public class GraphqlInstrumentationModule extends InstrumentationModule {

  public GraphqlInstrumentationModule() {
    super("graphql-java", "graphql-java-12.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new GraphqlInstrumentation());
  }
}
