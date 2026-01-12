/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public CouchbaseInstrumentationModule() {
    super("couchbase", "couchbase-2.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals("rx.OpenTelemetryTracingUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CouchbaseBucketInstrumentation(), new CouchbaseClusterInstrumentation());
  }

  @Override
  public String getModuleGroup() {
    return "couchbase";
  }

  @Override
  public List<String> injectedClassNames() {
    return singletonList("rx.OpenTelemetryTracingUtil");
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // removed in 3.x
    return hasClassesNamed("com.couchbase.client.java.CouchbaseAsyncBucket");
  }
}
