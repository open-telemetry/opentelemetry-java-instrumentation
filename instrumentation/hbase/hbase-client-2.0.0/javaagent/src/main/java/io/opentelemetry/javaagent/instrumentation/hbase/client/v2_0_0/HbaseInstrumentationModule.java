/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class HbaseInstrumentationModule extends InstrumentationModule {

  public HbaseInstrumentationModule() {
    super("hbase-client", "hbase-client-2.0.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.hadoop.hbase.client.AsyncAdmin")
        .and(not(hasClassesNamed("org.apache.hadoop.hbase.client.trace.IpcClientSpanBuilder")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RegionServerCallableInstrumentation(),
        new AbstractRpcClientInstrumentation(),
        new RpcConnectionInstrumentation(),
        new IpcCallInstrumentation());
  }
}
