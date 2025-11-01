/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class AsyncHttpClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public AsyncHttpClientInstrumentationModule() {
    super("async-http-client", "async-http-client-1.8");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // TimeoutsHolder class was added in 1.8.0, not present in 1.7.x
    return hasClassesNamed("com.ning.http.client.providers.netty.timeout.TimeoutsHolder");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new AsyncHttpProviderInstrumentation(), new ResponseInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
