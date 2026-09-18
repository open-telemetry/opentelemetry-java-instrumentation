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
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseCoreInstrumentation;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseMessageHandlerInstrumentation;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseSeedNodesInstrumentation;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTarget;
import java.util.List;
import java.util.function.BiConsumer;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
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
        new CouchbaseSeedNodesInstrumentation(),
        new CouchbaseMessageHandlerInstrumentation(),
        new CouchbaseProtostellarCoreInstrumentation(),
        new CouchbaseProtostellarRequestInstrumentation());
  }

  @Override
  public void registerVirtualFields(BiConsumer<String, String> virtualFieldRegistrar) {
    virtualFieldRegistrar.accept(
        "com.couchbase.client.core.CoreProtostellar", CouchbaseServerTarget.class.getName());
    virtualFieldRegistrar.accept(
        "com.couchbase.client.core.protostellar.ProtostellarBaseRequest",
        CouchbaseServerTarget.class.getName());
  }
}
