/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.NettyFutureInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class NettyInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public NettyInstrumentationModule() {
    super("netty", "netty-4.1");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Class added in 4.1.0 and not in 4.0.56 to avoid resolving this instrumentation completely
    // when using 4.0.
    return hasClassesNamed("io.netty.handler.codec.http.CombinedHttpHeaders");
  }

  @Override
  public String getModuleGroup() {
    return "netty";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new BootstrapInstrumentation(),
        new NettyFutureInstrumentation(),
        new NettyChannelPipelineInstrumentation(),
        new AbstractChannelHandlerContextInstrumentation(),
        new SingleThreadEventExecutorInstrumentation());
  }
}
