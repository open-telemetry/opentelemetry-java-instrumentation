/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.grpc.v1_14;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import com.linecorp.armeria.server.ServiceRequestContext;
import io.grpc.ServerCall;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcAuthorityStorage;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ArmeriaServerCallInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.linecorp.armeria.server.grpc.ArmeriaServerCall",
        "com.linecorp.armeria.internal.server.grpc.AbstractServerCall");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ServerCall<?, ?> serverCall,
        @Advice.FieldValue("ctx") ServiceRequestContext ctx) {
      String authority = ctx.request().headers().get(":authority");
      if (authority != null) {
        // ArmeriaServerCall does not implement getAuthority. We will store the value for authority
        // header as virtual field, this field is read in grpc instrumentation in
        // TracingServerInterceptor
        GrpcAuthorityStorage.setAuthority(serverCall, authority);
      }
    }
  }
}
