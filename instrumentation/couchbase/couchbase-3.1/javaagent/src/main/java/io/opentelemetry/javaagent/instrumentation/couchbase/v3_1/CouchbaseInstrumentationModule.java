/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule {
  public CouchbaseInstrumentationModule() {
    super("couchbase", "couchbase-3.1");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("com.couchbase.client.tracing.opentelemetry");
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
}
