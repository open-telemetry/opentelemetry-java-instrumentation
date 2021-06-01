/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v7_0;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Elasticsearch7RestClientInstrumentationModule extends InstrumentationModule {
  public Elasticsearch7RestClientInstrumentationModule() {
    super("elasticsearch-rest", "elasticsearch-rest-7.0", "elasticsearch");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class introduced in 7.0.0
    return hasClassesNamed("org.elasticsearch.client.RestClient$InternalRequest");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RestClientInstrumentation());
  }
}
