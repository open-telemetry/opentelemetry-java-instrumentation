/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseCoreInstrumentation;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseSeedNodesInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule {
  public CouchbaseInstrumentationModule() {
    super(
        "couchbase", expandDeprecatedNames("couchbase-3.2|deprecated:couchbase-3.4", "couchbase"));
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 3.2.0 (via com.couchbase.client:core-io 2.2.0)
    return hasClassesNamed("com.couchbase.client.core.cnc.RequestSpan$StatusCode");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CouchbaseEnvironmentInstrumentation(),
        new CouchbaseCoreInstrumentation(),
        new CouchbaseSeedNodesInstrumentation());
  }
}
