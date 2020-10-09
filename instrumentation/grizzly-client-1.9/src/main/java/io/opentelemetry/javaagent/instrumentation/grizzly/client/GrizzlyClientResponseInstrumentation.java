/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly.client;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
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
public final class GrizzlyClientResponseInstrumentation extends Instrumenter.Default {

  public GrizzlyClientResponseInstrumentation() {
    super("grizzly-client", "ning");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.ning.http.client.AsyncHandler", Pair.class.getName());
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("com.ning.http.client.AsyncCompletionHandler");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return hasSuperClass(named("com.ning.http.client.AsyncCompletionHandler"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GrizzlyClientTracer", packageName + ".GrizzlyInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("onCompleted")
            .and(takesArgument(0, named("com.ning.http.client.Response")))
            .and(isPublic()),
        packageName + ".GrizzlyClientResponseAdvice");
  }
}
