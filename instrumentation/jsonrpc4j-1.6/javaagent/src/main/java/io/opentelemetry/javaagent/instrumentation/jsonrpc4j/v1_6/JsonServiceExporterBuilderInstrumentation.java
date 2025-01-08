/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.googlecode.jsonrpc4j.JsonRpcServer;
import com.googlecode.jsonrpc4j.spring.JsonServiceExporter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JsonServiceExporterBuilderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.googlecode.jsonrpc4j.spring.JsonServiceExporter");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.googlecode.jsonrpc4j.spring.JsonServiceExporter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("exportService")), this.getClass().getName() + "$ExportAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExportAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void setInvocationListener(
        @Advice.This JsonServiceExporter exporter,
        @Advice.FieldValue("jsonRpcServer") JsonRpcServer jsonRpcServer) {
      VirtualField<JsonRpcServer, Boolean> instrumented =
          VirtualField.find(JsonRpcServer.class, Boolean.class);
      if (!Boolean.TRUE.equals(instrumented.get(jsonRpcServer))) {
        jsonRpcServer.setInvocationListener(JsonRpcSingletons.SERVER_INVOCATION_LISTENER);
        instrumented.set(jsonRpcServer, true);
      }
    }
  }
}
