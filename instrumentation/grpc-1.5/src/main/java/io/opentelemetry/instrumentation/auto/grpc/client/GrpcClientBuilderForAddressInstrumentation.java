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
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.net.InetSocketAddress;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

@AutoService(Instrumenter.class)
public class GrpcClientBuilderForAddressInstrumentation extends AbstractGrpcClientInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.grpc.ManagedChannelBuilder"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("forAddress").and(ElementMatchers.takesArguments(2))),
        GrpcClientBuilderForAddressInstrumentation.class.getName() + "$ForAddressAdvice");
  }

  public static class ForAddressAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static final void forAddress(
        @Advice.Argument(0) String address,
        @Advice.Argument(1) int port,
        @Advice.Return ManagedChannelBuilder builder) {
      ContextStore<ManagedChannelBuilder, InetSocketAddress> contextStore =
          InstrumentationContext.get(ManagedChannelBuilder.class, InetSocketAddress.class);
      contextStore.put(builder, InetSocketAddress.createUnresolved(address, port));
    }
  }
}
