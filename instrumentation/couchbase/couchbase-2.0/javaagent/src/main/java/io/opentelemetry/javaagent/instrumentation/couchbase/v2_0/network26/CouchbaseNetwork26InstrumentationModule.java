/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network26;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseNetwork26InstrumentationModule extends InstrumentationModule {

  public CouchbaseNetwork26InstrumentationModule() {
    super(
        "couchbase",
        expandDeprecatedNames(
            "couchbase-2.0|deprecated:couchbase-2.6", "couchbase-2.0-network-2.6"));
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 2.6.0, removed in 3.0.0
    return hasClassesNamed("com.couchbase.client.java.auth.CertAuthenticator");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CouchbaseCoreInstrumentation(), new CouchbaseNetworkInstrumentation());
  }
}
