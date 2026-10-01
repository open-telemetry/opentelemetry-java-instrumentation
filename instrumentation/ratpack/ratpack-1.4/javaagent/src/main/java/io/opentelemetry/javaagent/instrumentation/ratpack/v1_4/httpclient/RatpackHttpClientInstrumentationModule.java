/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.httpclient;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/** HTTP client instrumentation for Ratpack 1.7 and later. */
@AutoService(InstrumentationModule.class)
public class RatpackHttpClientInstrumentationModule extends InstrumentationModule {
  public RatpackHttpClientInstrumentationModule() {
    super(
        "ratpack",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"ratpack-1.4", "ratpack-client"}
            : new String[] {"ratpack-1.7"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.7.0
    return hasClassesNamed("ratpack.exec.util.retry.Delay");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DefaultExecControllerInstrumentation(),
        new HttpClientInstrumentation(),
        new RequestActionSupportInstrumentation());
  }
}
