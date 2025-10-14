/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import static io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0.ElasticJobSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.shardingsphere.elasticjob.api.ShardingContext;
import org.apache.shardingsphere.elasticjob.simple.job.SimpleJob;

public class SimpleJobExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.shardingsphere.elasticjob.simple.executor.SimpleJobExecutor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("process"))
            .and(
                takesArgument(
                    0, named("org.apache.shardingsphere.elasticjob.simple.job.SimpleJob")))
            .and(
                takesArgument(
                    3, named("org.apache.shardingsphere.elasticjob.api.ShardingContext"))),
        SimpleJobExecutorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ElasticJobHelper.ElasticJobScope onEnter(
        @Advice.Argument(0) SimpleJob elasticJob,
        @Advice.Argument(3) ShardingContext shardingContext) {

      ElasticJobProcessRequest request =
          ElasticJobProcessRequest.createWithUserJobInfo(
              shardingContext.getJobName(),
              shardingContext.getTaskId(),
              shardingContext.getShardingItem(),
              shardingContext.getShardingTotalCount(),
              shardingContext.getShardingParameter() != null
                  ? shardingContext.getShardingParameter()
                  : "",
              "SIMPLE",
              elasticJob.getClass(),
              "execute");

      return helper().startSpan(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter ElasticJobHelper.ElasticJobScope scope, @Advice.Thrown Throwable throwable) {
      if (scope != null) {
        helper().endSpan(scope, throwable);
      }
    }
  }
}
