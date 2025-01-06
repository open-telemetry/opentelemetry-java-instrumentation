/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class FinagleHttpInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public FinagleHttpInstrumentationModule() {
    super("finagle-http", "finagle-http-23.11");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new GenStreamingServerDispatcherInstrumentation(),
        new ChannelTransportInstrumentation(),
        new H2StreamChannelInitInstrumentation());
  }

  @Override
  public String getModuleGroup() {
    // relies on netty and needs access to common netty instrumentation classes
    return "netty";
  }

  @Override
  public List<String> injectedClassNames() {
    // these are injected so that they can access package-private members
    return Arrays.asList(
        "com.twitter.finagle.ChannelTransportHelpers",
        "io.netty.channel.OpenTelemetryChannelInitializerDelegate");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals("com.twitter.finagle.ChannelTransportHelpers")
        || className.equals("io.netty.channel.OpenTelemetryChannelInitializerDelegate");
  }
}
