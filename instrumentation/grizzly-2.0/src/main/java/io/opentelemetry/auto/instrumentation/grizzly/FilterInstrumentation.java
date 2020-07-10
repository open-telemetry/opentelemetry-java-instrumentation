/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.grizzly;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

@AutoService(Instrumenter.class)
public final class FilterInstrumentation extends Instrumenter.Default {

  public FilterInstrumentation() {
    super("grizzly");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.glassfish.grizzly.filterchain.BaseFilter");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return hasSuperClass(named("org.glassfish.grizzly.filterchain.BaseFilter"))
        // HttpCodecFilter is instrumented in the server instrumentation
        .and(
            not(
                ElementMatchers.<TypeDescription>named(
                    "org.glassfish.grizzly.http.HttpCodecFilter")))
        .and(
            not(
                ElementMatchers.<TypeDescription>named(
                    "org.glassfish.grizzly.http.HttpServerFilter")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".GrizzlyHttpServerTracer", packageName + ".ExtractAdapter"};
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("handleRead")
            .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
            .and(isPublic()),
        packageName + ".FilterAdvice");
  }
}
