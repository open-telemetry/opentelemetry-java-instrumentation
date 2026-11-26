/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Traces the rejected requests that Pekko HTTP 2.0 answers through the parsing error handler
 * overload that carries the request. The rejections themselves are still marked by {@link
 * PekkoHttpServerInstrumentationModule}, which is selected by the same names.
 */
@AutoService(InstrumentationModule.class)
public class PekkoHttp2ParsingErrorInstrumentationModule extends InstrumentationModule {
  public PekkoHttp2ParsingErrorInstrumentationModule() {
    super("pekko-http-1.0", "pekko-http-server", "pekko-http", "pekko");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 2.0.0-M2. Muzzle would also skip this module on pekko-http 1.x, but only after
    // matching every ParsingErrorHandler there, which logs a mismatch for each 1.x application
    // and fails the 1.x tests, which treat any muzzle mismatch as an error
    return hasClassesNamed("org.apache.pekko.http.IllegalRequestContext");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ParsingErrorHandlerRequestContextInstrumentation());
  }
}
