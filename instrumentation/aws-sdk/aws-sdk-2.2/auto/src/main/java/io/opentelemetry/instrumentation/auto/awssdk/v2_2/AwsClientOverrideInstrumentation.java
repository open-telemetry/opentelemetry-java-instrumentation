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

package io.opentelemetry.instrumentation.auto.awssdk.v2_2;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

/**
 * Separate instrumentation to inject into user configuration overrides. Overrides aren't merged so
 * we need to either inject into their override or create our own, but not both.
 */
@AutoService(Instrumenter.class)
public final class AwsClientOverrideInstrumentation extends AbstractAwsClientInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("software.amazon.awssdk.core.client.config.ClientOverrideConfiguration");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("software.amazon.awssdk.core.client.config.ClientOverrideConfiguration");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod().and(isPublic()).and(isStatic()).and(named("builder")),
        AwsClientOverrideInstrumentation.class.getName() + "$AwsSdkClientOverrideAdvice");
  }

  public static class AwsSdkClientOverrideAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Return final ClientOverrideConfiguration.Builder builder) {
      TracingExecutionInterceptor.overrideConfiguration(builder);
    }
  }
}
