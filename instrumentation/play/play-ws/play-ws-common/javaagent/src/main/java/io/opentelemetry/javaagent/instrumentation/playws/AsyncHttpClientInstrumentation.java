/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
  public ElementMatcher<TypeDescription> typeMatcher() {
    // CachingAsyncHttpClient rejects overrides to AsyncHandler
    // It also delegates to another AsyncHttpClient
    return nameStartsWith("play.")
        .and(
            implementsInterface(named("play.shaded.ahc.org.asynchttpclient.AsyncHttpClient"))
                .and(not(named("play.api.libs.ws.ahc.cache.CachingAsyncHttpClient"))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("play.shaded.ahc.org.asynchttpclient.Request")))
            .and(takesArgument(1, named("play.shaded.ahc.org.asynchttpclient.AsyncHandler"))),
        adviceName);
  }
}
