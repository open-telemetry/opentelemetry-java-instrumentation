/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

/** Beginning in version 5.3.0, DocumentRequest was renamed to DocWriteRequest. */
@AutoService(InstrumentationModule.class)
public class Elasticsearch53TransportClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public Elasticsearch53TransportClientInstrumentationModule() {
    super("elasticsearch-transport", "elasticsearch-transport-5.3", "elasticsearch");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AbstractClientInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
