/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.netty.http.client.HttpClient;

/**
 * This instrumentation solves the problem of the correct context propagation through the roller
 * coaster of Project Reactor and Netty thread hopping. It uses two public hooks of {@link
 * HttpClient}: {@link HttpClient#mapConnect(BiFunction)} and {@link
 * HttpClient#doOnRequest(BiConsumer)} to pass context from the caller to Reactor to Netty.
 */
@AutoService(InstrumentationModule.class)
public class ReactorNettyInstrumentationModule extends InstrumentationModule {

  public ReactorNettyInstrumentationModule() {
    super("reactor-netty", "reactor-netty-0.9");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Removed in 1.0.0
    return hasClassesNamed("reactor.netty.tcp.InetSocketAddressUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpClientInstrumentation());
  }
}
