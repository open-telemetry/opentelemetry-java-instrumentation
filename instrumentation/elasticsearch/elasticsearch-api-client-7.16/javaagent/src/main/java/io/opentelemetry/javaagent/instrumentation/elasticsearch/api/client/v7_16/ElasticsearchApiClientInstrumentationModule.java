/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.api.client.v7_16;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ElasticsearchApiClientInstrumentationModule extends InstrumentationModule {
  public ElasticsearchApiClientInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview()
            ? "elasticsearch-api-client-7.16"
            : "elasticsearch-api-client",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"elasticsearch-api-client", "elasticsearch"}
            : new String[] {"elasticsearch-api-client-7.16", "elasticsearch"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 7.16
    return hasClassesNamed("co.elastic.clients.elasticsearch.ElasticsearchClient")
        // artifact presence gate (provides native OTel support)
        // added in co.elastic.clients:elasticsearch-java 8.10
        .and(not(hasClassesNamed("co.elastic.clients.transport.instrumentation.Instrumentation")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RestClientTransportInstrumentation(), new RestClientHttpClientInstrumentation());
  }
}
