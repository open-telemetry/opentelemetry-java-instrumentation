/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Mongo37InstrumentationModule extends InstrumentationModule {

  public Mongo37InstrumentationModule() {
    super(
        "mongo",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"mongo-3.1"}
            : new String[] {"mongo-3.7"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        // added in 3.7
        "com.mongodb.MongoClientSettings$Builder",
        // removed in 4.0 (replaced by com.mongodb.internal.async.SingleResultCallback)
        "com.mongodb.async.SingleResultCallback");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new MongoClientSettingsBuilderInstrumentation(),
        new Mongo37ClusterSettingsBuilderInstrumentation(),
        new SocketStreamInstrumentation(),
        new InternalStreamConnectionInstrumentation(),
        new BaseClusterInstrumentation(),
        new Mongo37ClusterInstrumentation());
  }
}
