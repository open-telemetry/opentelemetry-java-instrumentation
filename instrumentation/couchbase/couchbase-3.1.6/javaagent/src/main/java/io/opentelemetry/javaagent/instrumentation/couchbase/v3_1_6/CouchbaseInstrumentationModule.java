/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;

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
    // New class introduced in 3.1, the minimum version we support.
    // NB: Couchbase does not provide any API guarantees on their core IO artifact so reconsider
    // instrumenting it instead of each individual JVM artifact if this becomes unmaintainable.
    return hasClassesNamed("com.couchbase.client.core.cnc.TracingIdentifiers");
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
