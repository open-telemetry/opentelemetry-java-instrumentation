/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.googlecode.jsonrpc4j.InvocationListener;
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JsonRpcServerBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.googlecode.jsonrpc4j.JsonRpcBasicServer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.googlecode.jsonrpc4j.JsonRpcBasicServer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void setInvocationListener(
        @Advice.This JsonRpcBasicServer jsonRpcServer,
        @Advice.FieldValue("invocationListener") InvocationListener invocationListener) {
      VirtualField<JsonRpcBasicServer, Boolean> instrumented =
          VirtualField.find(JsonRpcBasicServer.class, Boolean.class);
      if (!Boolean.TRUE.equals(instrumented.get(jsonRpcServer))) {
        jsonRpcServer.setInvocationListener(JsonRpcSingletons.SERVER_INVOCATION_LISTENER);
        instrumented.set(jsonRpcServer, true);
      }
    }
  }
}
