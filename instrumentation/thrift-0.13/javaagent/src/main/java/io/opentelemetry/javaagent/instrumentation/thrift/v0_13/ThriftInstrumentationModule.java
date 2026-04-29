/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ThriftInstrumentationModule extends InstrumentationModule {

  public ThriftInstrumentationModule() {
    super("thrift", "thrift-0.13");
  }

  /*
  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // limit support to 0.13.0 since we don't test with earlier versions
    // added in 0.13.0
    return hasClassesNamed("org.apache.thrift.protocol.ShortStack");
  }

   */

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ThriftServiceClientInstrumentation(),
        new ThriftAsyncClientInstrumentation(),
        new ThriftTBaseProcessorInstrumentation());
  }
}
