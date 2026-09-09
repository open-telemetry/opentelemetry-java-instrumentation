/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0;

import static io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0.DruidSingletons.getDataSourceName;
import static io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0.DruidSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.alibaba.druid.pool.DruidDataSourceMBean;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class DruidDataSourceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.alibaba.druid.stat.DruidDataSourceStatManager");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        isPublic()
            .and(isStatic())
            .and(named("addDataSource"))
            .and(takesArgument(0, Object.class))
            .and(takesArgument(1, String.class)),
        getClass().getName() + "$AddDataSourceAdvice");

    typeTransformer.applyAdviceToMethod(
        isPublic().and(isStatic()).and(named("removeDataSource")),
        getClass().getName() + "$RemoveDataSourceAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddDataSourceAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) Object dataSource, @Advice.Argument(1) @Nullable String name) {
      DruidDataSourceMBean druidDataSource = (DruidDataSourceMBean) dataSource;
      String poolName = name == null || name.isEmpty() ? getDataSourceName(druidDataSource) : name;
      telemetry().registerMetrics(druidDataSource, poolName);
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveDataSourceAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Argument(0) Object dataSource) {
      DruidDataSourceMBean druidDataSource = (DruidDataSourceMBean) dataSource;
      telemetry().unregisterMetrics(druidDataSource);
    }
  }
}
