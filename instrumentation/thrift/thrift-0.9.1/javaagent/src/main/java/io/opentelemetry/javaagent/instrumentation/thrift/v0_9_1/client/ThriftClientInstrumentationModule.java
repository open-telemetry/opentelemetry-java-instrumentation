/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class ThriftClientInstrumentationModule extends InstrumentationModule {

  public ThriftClientInstrumentationModule() {
    super("thrift", "thrift-0.9.1", "thrift-0.9.1-client");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.thrift.protocol.TProtocolDecorator")
        .and(not(hasClassesNamed("org.apache.thrift.TAsyncProcessor")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ThriftClientInstrumentation());
  }
}
