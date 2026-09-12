/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import java.util.List;
import java.util.function.BiConsumer;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class HbaseInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public HbaseInstrumentationModule() {
    super("hbase-client", "hbase-client-2.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
            // added in 2.0.0
            "org.apache.hadoop.hbase.ipc.RpcConnection",
            // added in 2.0.0
            "org.apache.hadoop.hbase.client.AsyncAdmin")
        // added in 2.5.0 (native OTel support)
        .and(not(hasClassesNamed("org.apache.hadoop.hbase.client.trace.IpcClientSpanBuilder")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RegionServerCallableInstrumentation(),
        new AbstractRpcClientInstrumentation(),
        new RpcConnectionInstrumentation(),
        new NettyRpcDuplexHandlerInstrumentation(),
        new IpcCallInstrumentation());
  }

  @Override
  public void registerVirtualFields(BiConsumer<String, String> virtualFieldRegistrar) {
    virtualFieldRegistrar.accept(
        "org.apache.hadoop.hbase.ipc.Call", RequestAndContext.class.getName());
  }
}
