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

package io.opentelemetry.instrumentation.auto.grpc.client;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class GrpcClientBuilderBuildInstrumentation extends AbstractGrpcClientInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.grpc.ManagedChannelBuilder"))
        .and(declaresField(named("interceptors")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("build")),
        GrpcClientBuilderBuildInstrumentation.class.getName() + "$AddInterceptorAdvice");
  }

  public static class AddInterceptorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInterceptor(
        @Advice.This final ManagedChannelBuilder thiz,
        @Advice.FieldValue("interceptors") final List<ClientInterceptor> interceptors) {
      boolean shouldRegister = true;
      for (ClientInterceptor interceptor : interceptors) {
        if (interceptor instanceof TracingClientInterceptor) {
          shouldRegister = false;
          break;
        }
      }
      if (shouldRegister) {
        ContextStore<ManagedChannelBuilder, InetSocketAddress> contextStore =
            InstrumentationContext.get(ManagedChannelBuilder.class, InetSocketAddress.class);
        InetSocketAddress sockAddr = contextStore.get(thiz);
        interceptors.add(0, new TracingClientInterceptor(sockAddr));
      }
    }
  }
}
