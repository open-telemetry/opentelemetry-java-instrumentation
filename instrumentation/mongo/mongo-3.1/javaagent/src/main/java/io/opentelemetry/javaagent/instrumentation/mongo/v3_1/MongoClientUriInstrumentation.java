/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.mongodb.MongoClientURI;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoClusterSettings;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoClusterSettings.LegacySrvTargetScope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class MongoClientUriInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.Mongo");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isStatic()
            .and(named("createCluster"))
            .and(takesArgument(0, named("com.mongodb.MongoClientURI"))),
        getClass().getName() + "$CreateClusterAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateClusterAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static LegacySrvTargetScope onEnter(@Advice.Argument(0) MongoClientURI clientUri) {
      return MongoClusterSettings.openLegacySrvTargetScope(clientUri.getURI());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable LegacySrvTargetScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
