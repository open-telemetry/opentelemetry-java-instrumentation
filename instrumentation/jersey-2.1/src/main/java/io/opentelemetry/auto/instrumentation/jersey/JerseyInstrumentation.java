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
package io.opentelemetry.auto.instrumentation.jersey;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * This is instrumentation for Jersey REST framework. It differs from other JAX-RS specific
 * instrumentations in that this one produces SERVER spans. This is useful in rare case when one's
 * application runs in runtime which is not already instrumented. Or if server instrumentation is
 * undesired for some reason and is disabled.
 *
 * @see <a href="https://eclipse-ee4j.github.io/jersey/">Eclipse Jersey</a>
 */
@AutoService(Instrumenter.class)
public final class JerseyInstrumentation extends Instrumenter.Default {

  public JerseyInstrumentation() {
    super("jersey");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.jersey.server.ResourceConfig");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JerseyDecorator",
      packageName + ".JerseyRequestExtractAdapter",
      packageName + ".TracingRequestEventListener",
      packageName + ".TracingRequestEventListener$1",
      packageName + ".TracingApplicationEventListener"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor().and(isPublic()).and(takesArguments(0)),
        JerseyResourceConfigAdvice.class.getName());
  }

  public static class JerseyResourceConfigAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void ingressProcessEnter(@Advice.This final ResourceConfig resourceConfig) {
      resourceConfig.register(TracingApplicationEventListener.class);
    }
  }
}
