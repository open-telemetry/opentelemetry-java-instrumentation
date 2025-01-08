/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_6.JsonRpcSingletons.SERVER_INVOCATION_LISTENER;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.googlecode.jsonrpc4j.InvocationListener;
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;
import com.googlecode.jsonrpc4j.MultipleInvocationListener;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class JsonRpcServerInstrumentation implements TypeInstrumentation {

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

    transformer.applyAdviceToMethod(
        isMethod().and(named("setInvocationListener")),
        this.getClass().getName() + "$SetInvocationListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void setInvocationListener(
        @Advice.This JsonRpcBasicServer jsonRpcServer,
        @Advice.FieldValue(value = "invocationListener", readOnly = false)
            InvocationListener invocationListener) {
      invocationListener = SERVER_INVOCATION_LISTENER;
    }
  }

  @SuppressWarnings("unused")
  public static class SetInvocationListenerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void setInvocationListener(
        @Advice.This JsonRpcBasicServer jsonRpcServer,
        @Advice.Argument(value = 0, readOnly = false, typing = Assigner.Typing.DYNAMIC)
            InvocationListener invocationListener) {
      VirtualField<JsonRpcBasicServer, Boolean> instrumented =
          VirtualField.find(JsonRpcBasicServer.class, Boolean.class);
      if (!Boolean.TRUE.equals(instrumented.get(jsonRpcServer))) {
        if (invocationListener == null) {
          invocationListener = SERVER_INVOCATION_LISTENER;
        } else if (invocationListener instanceof MultipleInvocationListener) {
          ((MultipleInvocationListener) invocationListener)
              .addInvocationListener(SERVER_INVOCATION_LISTENER);
        } else {
          invocationListener =
              new MultipleInvocationListener(invocationListener, SERVER_INVOCATION_LISTENER);
        }

        instrumented.set(jsonRpcServer, true);
      }
    }
  }
}
