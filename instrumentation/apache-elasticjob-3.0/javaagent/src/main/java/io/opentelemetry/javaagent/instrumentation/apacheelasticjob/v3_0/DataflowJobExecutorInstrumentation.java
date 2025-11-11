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
import org.apache.shardingsphere.elasticjob.dataflow.job.DataflowJob;

public class DataflowJobExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.shardingsphere.elasticjob.dataflow.executor.DataflowJobExecutor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("process"))
            .and(
                takesArgument(
                    0, named("org.apache.shardingsphere.elasticjob.dataflow.job.DataflowJob")))
            .and(
                takesArgument(
                    3, named("org.apache.shardingsphere.elasticjob.api.ShardingContext"))),
        DataflowJobExecutorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ElasticJobHelper.ElasticJobScope onEnter(
        @Advice.Argument(0) DataflowJob<?> elasticJob,
        @Advice.Argument(3) ShardingContext shardingContext) {

      ElasticJobProcessRequest request =
          ElasticJobProcessRequest.createFromShardingContext(
              shardingContext,
              ElasticJobType.DATAFLOW.getValue(),
              elasticJob.getClass(),
              "processData");

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
