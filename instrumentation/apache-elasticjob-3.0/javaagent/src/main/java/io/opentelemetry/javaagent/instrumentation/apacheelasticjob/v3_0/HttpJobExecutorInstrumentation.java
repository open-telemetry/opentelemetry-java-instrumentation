/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import static io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.shardingsphere.elasticjob.api.ShardingContext;
import org.apache.shardingsphere.elasticjob.http.executor.HttpJobExecutor;

public class HttpJobExecutorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.shardingsphere.elasticjob.http.executor.HttpJobExecutor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("process")
            .and(
                takesArgument(
                    3, named("org.apache.shardingsphere.elasticjob.api.ShardingContext"))),
        HttpJobExecutorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ElasticJobHelper.ElasticJobScope onEnter(
        @Advice.Argument(3) ShardingContext shardingContext) {

      ElasticJobProcessRequest request =
          ElasticJobProcessRequest.create(
              shardingContext, ElasticJobType.HTTP, HttpJobExecutor.class, "process");

      return helper().startSpan(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter @Nullable ElasticJobHelper.ElasticJobScope scope,
        @Advice.Thrown @Nullable Throwable throwable) {
      helper().endSpan(scope, throwable);
    }
  }
}
