/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

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
    super("couchbase", "couchbase-2.6");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // introduced in java-client 2.6, removed in 3.x
    return hasClassesNamed("com.couchbase.client.java.auth.CertAuthenticator");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CouchbaseCoreInstrumentation(), new CouchbaseNetworkInstrumentation());
  }

  @Override
  public String getModuleGroup() {
    return "couchbase";
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
