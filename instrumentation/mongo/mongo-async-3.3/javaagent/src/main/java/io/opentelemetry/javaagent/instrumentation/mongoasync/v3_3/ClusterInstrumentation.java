/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongoasync.v3_3;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ClusterSettings;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoClusterSettings;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoClusterTargets;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Records the target a cluster was configured with while the cluster is being constructed, which is
 * the earliest point at which the driver has settled the configuration and the last point at which
 * it is still the one the client was built with.
 *
 * <p>{@code BaseCluster} moved into an internal package in 3.8, so both of its locations are
 * matched.
 */
final class ClusterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.mongodb.connection.BaseCluster", "com.mongodb.internal.connection.BaseCluster");
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
      // a client that resolves an SRV host is given a placeholder seed list naming a host it
      // never talks to, so the srv host is read first
      MongoServerTarget target = MongoServerTarget.srvHost(MongoClusterSettings.srvHost(settings));
      if (target == null) {
        target = MongoServerTarget.seeds(settings.getHosts());
      }
      MongoClusterTargets.register(clusterId, target);
    }
  }
}
