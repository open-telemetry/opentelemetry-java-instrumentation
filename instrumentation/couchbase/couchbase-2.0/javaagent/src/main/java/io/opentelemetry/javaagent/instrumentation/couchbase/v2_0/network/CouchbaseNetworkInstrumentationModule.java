/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Network capture is isolated from the high-level Couchbase instrumentation so Muzzle can disable
 * it independently when the pre-2.6 core networking classes are not present.
 */
@AutoService(InstrumentationModule.class)
public class CouchbaseNetworkInstrumentationModule extends InstrumentationModule {

  public CouchbaseNetworkInstrumentationModule() {
    super("couchbase", "couchbase-2.0", "couchbase-network-2.0", "couchbase-2.0-network");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 2.6.0 (via com.couchbase.client:core-io 1.6.0)
    return not(hasClassesNamed("com.couchbase.client.core.env.NetworkResolution"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CouchbaseCoreNetworkInstrumentation(), new CouchbaseNetworkInstrumentation());
  }
}
