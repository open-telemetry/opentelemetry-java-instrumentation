/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.asynchttpclient.common.v1_8.ResponseInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class AsyncHttpClientInstrumentationModule extends InstrumentationModule {
  public AsyncHttpClientInstrumentationModule() {
    super("async-http-client", "async-http-client-1.9");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.9.0
    return hasClassesNamed("com.ning.http.client.uri.Uri");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RequestInstrumentation(), new ResponseInstrumentation());
  }
}
