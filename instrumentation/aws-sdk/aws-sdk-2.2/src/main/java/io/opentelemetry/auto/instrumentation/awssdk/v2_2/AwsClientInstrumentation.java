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

package io.opentelemetry.auto.instrumentation.awssdk.v2_2;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;

/** AWS SDK v2 instrumentation */
@AutoService(Instrumenter.class)
public final class AwsClientInstrumentation extends AbstractAwsClientInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("software.amazon.awssdk.core.client.builder.SdkClientBuilder");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("software.amazon.awssdk.")
        .and(
            implementsInterface(
                named("software.amazon.awssdk.core.client.builder.SdkClientBuilder")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        isMethod().and(isPublic()).and(named("build")),
        AwsClientInstrumentation.class.getName() + "$AwsSdkClientBuilderBuildAdvice");

    transformers.put(
        isMethod().and(isPublic()).and(named("overrideConfiguration")),
        AwsClientInstrumentation.class.getName()
            + "$AwsSdkClientBuilderOverrideConfigurationAdvice");

    return Collections.unmodifiableMap(transformers);
  }

  public static class AwsSdkClientBuilderOverrideConfigurationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This final SdkClientBuilder thiz) {
      TracingExecutionInterceptor.OVERRIDDEN.put(thiz, true);
    }
  }

  public static class AwsSdkClientBuilderBuildAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This final SdkClientBuilder thiz) {
      if (!Boolean.TRUE.equals(TracingExecutionInterceptor.OVERRIDDEN.get(thiz))) {
        TracingExecutionInterceptor.overrideConfiguration(thiz);
      }
    }
  }
}
