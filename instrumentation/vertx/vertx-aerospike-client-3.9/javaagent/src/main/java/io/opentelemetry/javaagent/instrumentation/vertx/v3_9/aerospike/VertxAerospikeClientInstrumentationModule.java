/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.aerospike;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

/**
 * Instrumentation for Aerospike client operations in Vert.x 3.9 applications.
 * 
 * Note: Muzzle warnings about missing framework classes can be ignored - they are expected
 * since these classes are in the javaagent classloader, not the application classloader.
 * The instrumentation will still work correctly at runtime.
 */
@AutoService(InstrumentationModule.class)
public class VertxAerospikeClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public VertxAerospikeClientInstrumentationModule() {
    super("vertx-aerospike-client", "vertx-aerospike-client-3.9", "vertx");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new NativeAerospikeClientInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}

