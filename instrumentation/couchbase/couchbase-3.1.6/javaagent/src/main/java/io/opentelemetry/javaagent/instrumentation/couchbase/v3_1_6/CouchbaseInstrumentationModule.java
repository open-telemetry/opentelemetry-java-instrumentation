/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.Collections;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public CouchbaseInstrumentationModule() {
    super("couchbase", "couchbase-3.1.6");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // introduced in java-client 3.1.6 (core-io 2.1.6)
    return hasClassesNamed("com.couchbase.client.core.endpoint.EventingEndpoint")
        // introduced in java-client 3.2.0 (core-io 2.2.0)
        .and(not(hasClassesNamed("com.couchbase.client.core.cnc.RequestSpan$StatusCode")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new CouchbaseEnvironmentInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
