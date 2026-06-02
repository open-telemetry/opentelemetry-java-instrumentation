/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule {
  public CouchbaseInstrumentationModule() {
    super("couchbase", "couchbase-3.1");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 3.1.0 (via com.couchbase.client:core-io 2.1.0)
    return hasClassesNamed("com.couchbase.client.core.cnc.TracingIdentifiers")
        // added in 3.1.6 (via com.couchbase.client:core-io 2.1.6)
        .and(not(hasClassesNamed("com.couchbase.client.core.endpoint.EventingEndpoint")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new CouchbaseEnvironmentInstrumentation());
  }
}
