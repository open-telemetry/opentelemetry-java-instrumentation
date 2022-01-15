/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.netty.http.client.HttpClient;

/**
 * This instrumentation solves the problem of the correct context propagation through the roller
 * coaster of Project Reactor and Netty thread hopping. It uses two public hooks of {@link
 * HttpClient}: {@link HttpClient#mapConnect(Function)} and {@link
 * HttpClient#doOnRequest(BiConsumer)} to pass context from the caller to Reactor to Netty.
 */
@AutoService(InstrumentationModule.class)
public class ReactorNettyInstrumentationModule extends InstrumentationModule {

  public ReactorNettyInstrumentationModule() {
    super("reactor-netty", "reactor-netty-1.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Introduced in 1.0.0
    return hasClassesNamed("reactor.netty.transport.AddressUtils");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new HttpClientInstrumentation(),
        new ResponseReceiverInstrumentation(),
        new TransportConnectorInstrumentation());
  }
}
