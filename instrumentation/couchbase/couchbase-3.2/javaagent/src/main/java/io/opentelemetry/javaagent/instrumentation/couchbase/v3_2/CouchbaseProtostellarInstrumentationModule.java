/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class CouchbaseProtostellarInstrumentationModule extends InstrumentationModule {

  public CouchbaseProtostellarInstrumentationModule() {
    super(
        "couchbase",
        expandDeprecatedNames(
            "couchbase-3.2|deprecated:couchbase-3.4", "couchbase", "couchbase-3.4.3-protostellar"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CouchbaseProtostellarCoreInstrumentation(),
        new CouchbaseProtostellarRequestInstrumentation());
  }
}
