/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ResponseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.asynchttpclient.AsyncCompletionHandler");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return hasSuperClass(named("org.asynchttpclient.AsyncCompletionHandler"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("onCompleted")
            .and(takesArgument(0, named("org.asynchttpclient.Response")))
            .and(isPublic()),
        ResponseAdvice.class.getName());
  }
}
