/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class OpenSearchRestClientTransportInstrumentationModule extends InstrumentationModule {
  public OpenSearchRestClientTransportInstrumentationModule() {
    super("opensearch-java", "opensearch-java-3.0", "opensearch");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.opensearch.client.transport.rest_client.RestClientTransport");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RestClientTransportInstrumentation());
  }
}
