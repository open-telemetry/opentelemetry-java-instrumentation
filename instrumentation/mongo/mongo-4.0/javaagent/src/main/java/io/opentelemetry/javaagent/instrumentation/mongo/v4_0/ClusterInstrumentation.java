/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ClusterSettings;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoClusterSettings;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoClusterTargets;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class ClusterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.mongodb.internal.connection.BaseCluster",
        // since 4.3
        "com.mongodb.internal.connection.LoadBalancedCluster");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(0, named("com.mongodb.connection.ClusterId")))
            .and(takesArgument(1, named("com.mongodb.connection.ClusterSettings"))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.Argument(0) ClusterId clusterId, @Advice.Argument(1) ClusterSettings settings) {
      MongoClusterTargets.register(clusterId, MongoClusterSettings.configuredTarget(settings));
    }
  }
}
