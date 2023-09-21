/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * Most of this class is identical to version 5's instrumentation, but they changed an interface to
 * an abstract class, so the bytecode isn't directly compatible.
 */
@AutoService(InstrumentationModule.class)
public class Elasticsearch6TransportClientInstrumentationModule extends InstrumentationModule {
  public Elasticsearch6TransportClientInstrumentationModule() {
    super("elasticsearch-transport", "elasticsearch-transport-6.0", "elasticsearch");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AbstractClientInstrumentation());
  }
}
