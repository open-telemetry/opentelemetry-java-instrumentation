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

package io.opentelemetry.auto.instrumentation.grpc.client;

import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

@AutoService(Instrumenter.class)
public class GrpcClientBuilderInstrumentation extends Instrumenter.Default {

  public GrpcClientBuilderInstrumentation() {
    super("grpc", "grpc-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.grpc.ManagedChannelBuilder"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GrpcClientDecorator",
      packageName + ".GrpcInjectAdapter",
      packageName + ".TracingClientInterceptor",
      packageName + ".TracingClientInterceptor$TracingClientCall",
      packageName + ".TracingClientInterceptor$TracingClientCallListener",
      "io.opentelemetry.auto.instrumentation.grpc.common.GrpcHelper",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        "io.grpc.ManagedChannelBuilder", InetSocketAddress.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> map = new HashMap<>(2);
    map.put(
        isMethod().and(named("build")),
        GrpcClientBuilderInstrumentation.class.getName() + "$AddInterceptorAdvice");
    map.put(
        isMethod().and(named("forAddress").and(ElementMatchers.takesArguments(2))),
        GrpcClientBuilderInstrumentation.class.getName() + "$ForAddressAdvice");
    return map;
  }

  public static class AddInterceptorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInterceptor(
        @Advice.This final ManagedChannelBuilder thiz,
        @Advice.FieldValue("interceptors") final List<ClientInterceptor> interceptors) {
      boolean shouldRegister = true;
      for (final ClientInterceptor interceptor : interceptors) {
        if (interceptor instanceof TracingClientInterceptor) {
          shouldRegister = false;
          break;
        }
      }
      if (shouldRegister) {
        final ContextStore<ManagedChannelBuilder, InetSocketAddress> contextStore =
            InstrumentationContext.get(ManagedChannelBuilder.class, InetSocketAddress.class);
        final InetSocketAddress sockAddr = contextStore.get(thiz);
        interceptors.add(0, new TracingClientInterceptor(sockAddr));
      }
    }
  }

  public static class ForAddressAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static final void forAddress(
        @Advice.Argument(0) final String address,
        @Advice.Argument(1) final int port,
        @Advice.Return final ManagedChannelBuilder builder) {
      final ContextStore<ManagedChannelBuilder, InetSocketAddress> contextStore =
          InstrumentationContext.get(ManagedChannelBuilder.class, InetSocketAddress.class);
      contextStore.put(builder, InetSocketAddress.createUnresolved(address, port));
    }
  }
}
