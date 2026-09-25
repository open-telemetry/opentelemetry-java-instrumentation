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
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseProtostellarInstrumentationModule extends InstrumentationModule {

  public CouchbaseProtostellarInstrumentationModule() {
    super(
        "couchbase",
        expandDeprecatedNames(
            "couchbase-3.2|deprecated:couchbase-3.4", "couchbase", "couchbase-3.4.3-protostellar"));
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 3.4.3 (via com.couchbase.client:core-io 2.4.3)
    return hasClassesNamed("com.couchbase.client.core.CoreProtostellar");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CouchbaseProtostellarCoreInstrumentation(),
        new CouchbaseProtostellarRequestInstrumentation());
  }
}
