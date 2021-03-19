/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AsyncHttpClientInstrumentation implements TypeInstrumentation {
  private final String adviceName;

  public AsyncHttpClientInstrumentation(String adviceName) {
    this.adviceName = adviceName;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("play.shaded.ahc.org.asynchttpclient.AsyncHttpClient");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // CachingAsyncHttpClient rejects overrides to AsyncHandler
    // It also delegates to another AsyncHttpClient
    return nameStartsWith("play.")
        .<TypeDescription>and(
            implementsInterface(named("play.shaded.ahc.org.asynchttpclient.AsyncHttpClient"))
                .and(not(named("play.api.libs.ws.ahc.cache.CachingAsyncHttpClient"))));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("play.shaded.ahc.org.asynchttpclient.Request")))
            .and(takesArgument(1, named("play.shaded.ahc.org.asynchttpclient.AsyncHandler"))),
        adviceName);
  }
}
