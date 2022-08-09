/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GrpcClientBuilderBuildInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.grpc.ManagedChannelBuilder");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.grpc.ManagedChannelBuilder"))
        .and(declaresField(named("interceptors")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("build")),
        GrpcClientBuilderBuildInstrumentation.class.getName() + "$AddInterceptorAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddInterceptorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInterceptor(
        @Advice.This ManagedChannelBuilder<?> builder,
        @Advice.FieldValue("interceptors") List<ClientInterceptor> interceptors) {
      VirtualField<ManagedChannelBuilder<?>, Boolean> instrumented =
          VirtualField.find(ManagedChannelBuilder.class, Boolean.class);
      if (!Boolean.TRUE.equals(instrumented.get(builder))) {
        interceptors.add(0, GrpcSingletons.CLIENT_INTERCEPTOR);
        instrumented.set(builder, true);
      }
    }
  }
}
