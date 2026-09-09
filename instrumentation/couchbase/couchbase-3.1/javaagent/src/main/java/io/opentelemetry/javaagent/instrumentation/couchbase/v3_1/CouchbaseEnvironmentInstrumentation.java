/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.couchbase.client.core.env.CoreEnvironment;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CouchbaseEnvironmentInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.env.CoreEnvironment$Builder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This CoreEnvironment.Builder<?> builder) {
      OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
      boolean legacyBridge = isLegacyBridge(builder.getClass().getClassLoader());
      String instrumentationName;
      if (legacyBridge) {
        instrumentationName =
            SemconvStability.v3Preview()
                ? "io.opentelemetry.couchbase-3.1"
                : "io.opentelemetry.javaagent.couchbase-3.1";
      } else {
        instrumentationName = "com.couchbase.client.jvm";
      }
      builder.requestTracer(
          CouchbaseRequestTracer.create(
              openTelemetry.getTracer(instrumentationName), legacyBridge));
    }

    @SuppressWarnings("EffectivelyPrivate")
    public static boolean isLegacyBridge(ClassLoader classLoader) {
      try {
        Class.forName("com.couchbase.client.core.endpoint.EventingEndpoint", false, classLoader);
        return false;
      } catch (ClassNotFoundException ignored) {
        return true;
      }
    }
  }
}
