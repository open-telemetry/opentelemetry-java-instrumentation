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
import java.util.Objects;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.shardingsphere.elasticjob.infra.listener.ShardingContexts;

public class ElasticJobExecutorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.shardingsphere.elasticjob.executor.ElasticJobExecutor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("process")
            .and(
                takesArgument(
                    0, named("org.apache.shardingsphere.elasticjob.api.JobConfiguration")))
            .and(
                takesArgument(
                    1,
                    named("org.apache.shardingsphere.elasticjob.infra.listener.ShardingContexts")))
            .and(takesArgument(2, int.class))
            .and(
                takesArgument(
                    3,
                    named("org.apache.shardingsphere.elasticjob.tracing.event.JobExecutionEvent"))),
        ElasticJobExecutorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ElasticJobHelper.ElasticJobScope onEnter(
        @Advice.FieldValue("jobItemExecutor") Object jobItemExecutor,
        @Advice.Argument(1) ShardingContexts shardingContexts,
        @Advice.Argument(2) int item) {

      ElasticJobType jobType = ElasticJobType.fromExecutor(jobItemExecutor);
      if (jobType != ElasticJobType.SCRIPT && jobType != ElasticJobType.HTTP) {
        return null;
      }
      ElasticJobProcessRequest request =
          ElasticJobProcessRequest.create(
              shardingContexts.getJobName(),
              shardingContexts.getTaskId(),
              item,
              shardingContexts.getShardingTotalCount(),
              Objects.toString(shardingContexts.getShardingItemParameters(), null),
              jobType);
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
