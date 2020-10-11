/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HttpCodecFilterInstrumentation extends Instrumenter.Default {

  public HttpCodecFilterInstrumentation() {
    super("grizzly");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.http.HttpCodecFilter");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".GrizzlyHttpServerTracer", packageName + ".ExtractAdapter"};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    // this is for 2.3 through 2.3.19
    transformers.put(
        named("handleRead")
            .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
            .and(takesArgument(1, named("org.glassfish.grizzly.http.HttpPacketParsing")))
            .and(isPublic()),
        packageName + ".HttpCodecFilterOldAdvice");
    // this is for 2.3.20+
    transformers.put(
        named("handleRead")
            .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
            .and(takesArgument(1, named("org.glassfish.grizzly.http.HttpHeader")))
            .and(isPublic()),
        packageName + ".HttpCodecFilterAdvice");
    return transformers;
  }
}
