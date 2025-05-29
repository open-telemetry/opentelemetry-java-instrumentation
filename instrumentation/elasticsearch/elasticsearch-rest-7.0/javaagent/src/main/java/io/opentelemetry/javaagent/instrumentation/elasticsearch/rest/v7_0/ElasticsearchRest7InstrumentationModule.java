/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v7_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ElasticsearchRest7InstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public ElasticsearchRest7InstrumentationModule() {
    super("elasticsearch-rest", "elasticsearch-rest-7.0", "elasticsearch");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Class `org.elasticsearch.client.RestClient$InternalRequest` introduced in 7.0.0.
    // Since Elasticsearch client version 8.10, the ES client comes with a native OTel
    // instrumentation that introduced the class
    // `co.elastic.clients.transport.instrumentation.Instrumentation`.
    // Disabling agent instrumentation for those cases.
    return hasClassesNamed("org.elasticsearch.client.RestClient$InternalRequest")
        .and(not(hasClassesNamed("co.elastic.clients.transport.instrumentation.Instrumentation")));
  }

  @Override
  public String getModuleGroup() {
    return "elasticsearch";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RestClientInstrumentation());
  }
}
