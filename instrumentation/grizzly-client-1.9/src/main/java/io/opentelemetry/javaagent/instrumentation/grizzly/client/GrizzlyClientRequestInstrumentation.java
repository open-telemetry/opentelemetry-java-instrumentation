/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly.client;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.Pair;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class GrizzlyClientRequestInstrumentation extends Instrumenter.Default {

  public GrizzlyClientRequestInstrumentation() {
    super("grizzly-client", "ning");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.ning.http.client.AsyncHandler", Pair.class.getName());
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("com.ning.http.client.AsyncHttpClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GrizzlyClientTracer", packageName + ".GrizzlyInjectAdapter"
    };
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("executeRequest")
            .and(takesArgument(0, named("com.ning.http.client.Request")))
            .and(takesArgument(1, named("com.ning.http.client.AsyncHandler")))
            .and(isPublic()),
        packageName + ".GrizzlyClientRequestAdvice");
  }
}
