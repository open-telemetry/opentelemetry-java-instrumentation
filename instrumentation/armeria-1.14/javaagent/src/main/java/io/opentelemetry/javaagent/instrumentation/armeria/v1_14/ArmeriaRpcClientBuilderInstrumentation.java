/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

 package io.opentelemetry.javaagent.instrumentation.armeria.v1_14;

 import static net.bytebuddy.matcher.ElementMatchers.isMethod;
 import static net.bytebuddy.matcher.ElementMatchers.isPublic;
 import static net.bytebuddy.matcher.ElementMatchers.named;
 
 import com.linecorp.armeria.client.grpc.GrpcClientBuilder;
 import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
 import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
 import io.opentelemetry.javaagent.instrumentation.grpc.v1_6.GrpcSingletons
 import net.bytebuddy.asm.Advice;
 import net.bytebuddy.description.type.TypeDescription;
 import net.bytebuddy.matcher.ElementMatcher;

public class ArmeriaRpcClientBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // we need to update class being targeted
    return named("com.linecorp.armeria.client.grpc.GrpcClientBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("build")),
        // reference this class' build advice
        ArmeriaRpcClientBuilderInstrumentation.class.getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodEnter
    // the the GrpcClientBuilder instead
    public static void build(@Advice.This GrpcClientBuilder builder) {
      builder.interceptor(GrpcSingletons.CLIENT_DECORATOR);
    }
  }
}
