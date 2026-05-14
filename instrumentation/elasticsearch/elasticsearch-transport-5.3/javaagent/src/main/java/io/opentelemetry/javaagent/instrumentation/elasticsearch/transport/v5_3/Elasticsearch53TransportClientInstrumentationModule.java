/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/** Beginning in version 5.3.0, DocumentRequest was renamed to DocWriteRequest. */
@AutoService(InstrumentationModule.class)
public class Elasticsearch53TransportClientInstrumentationModule extends InstrumentationModule {
  public Elasticsearch53TransportClientInstrumentationModule() {
    super("elasticsearch-transport", "elasticsearch-transport-5.3", "elasticsearch");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 5.3
    return hasClassesNamed("org.elasticsearch.action.DocWriteRequest");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AbstractClientInstrumentation());
  }
}
