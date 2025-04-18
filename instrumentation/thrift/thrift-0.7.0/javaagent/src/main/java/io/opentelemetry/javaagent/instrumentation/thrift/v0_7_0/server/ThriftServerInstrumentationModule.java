/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class ThriftServerInstrumentationModule extends InstrumentationModule {

  public ThriftServerInstrumentationModule() {
    super("thrift", "thrift-0.7.0", "thrift-0.7.0-server");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.thrift.TServiceClient")
        .and(not(hasClassesNamed("org.apache.thrift.protocol.TProtocolDecorator")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ThriftServerInstrumentation(),
        new ThriftServletInstrumentation(),
        new ThriftFrameBufferInstrumentation(),
        new ThriftBaseProcessorInstrumentation());
  }
}
