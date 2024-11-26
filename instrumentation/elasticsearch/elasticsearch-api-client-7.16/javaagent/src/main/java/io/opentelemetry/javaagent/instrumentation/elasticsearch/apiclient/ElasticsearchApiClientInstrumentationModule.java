/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.apiclient;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ElasticsearchApiClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public ElasticsearchApiClientInstrumentationModule() {
    super("elasticsearch-api-client", "elasticsearch-api-client-7.16", "elasticsearch");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Since Elasticsearch client version 8.10, the ES client comes with a native OTel
    // instrumentation
    // that introduced the class `co.elastic.clients.transport.instrumentation.Instrumentation`.
    // Disabling agent instrumentation for those cases.
    return not(hasClassesNamed("co.elastic.clients.transport.instrumentation.Instrumentation"));
  }

  @Override
  public String getModuleGroup() {
    return "elasticsearch";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RestClientTransportInstrumentation(), new RestClientHttpClientInstrumentation());
  }
}
