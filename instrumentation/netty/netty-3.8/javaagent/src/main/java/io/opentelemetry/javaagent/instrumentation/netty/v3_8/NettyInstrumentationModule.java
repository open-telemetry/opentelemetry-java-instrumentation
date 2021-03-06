/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class NettyInstrumentationModule extends InstrumentationModule {
  public NettyInstrumentationModule() {
    super("netty", "netty-3.8");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ChannelFutureListenerInstrumentation(),
        new NettyChannelInstrumentation(),
        new NettyChannelPipelineInstrumentation(),
        new DefaultChannelPipelineInstrumentation());
  }
}
