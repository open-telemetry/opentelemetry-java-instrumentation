/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule {
  public CouchbaseInstrumentationModule() {
    super("couchbase", "couchbase-2.0");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "rx.__OpenTelemetryTracingUtil",
      "io.opentelemetry.instrumentation.rxjava.SpanFinishingSubscription",
      "io.opentelemetry.instrumentation.rxjava.TracedSubscriber",
      "io.opentelemetry.instrumentation.rxjava.TracedOnSubscribe",
      packageName + ".CouchbaseClientTracer",
      packageName + ".CouchbaseOnSubscribe",
      packageName + ".CouchbaseQueryNormalizer"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CouchbaseBucketInstrumentation(), new CouchbaseClusterInstrumentation());
  }
}
