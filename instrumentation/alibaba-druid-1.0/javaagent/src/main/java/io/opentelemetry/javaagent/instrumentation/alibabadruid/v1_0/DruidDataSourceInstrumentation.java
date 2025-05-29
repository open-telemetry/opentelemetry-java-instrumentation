/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0;

import static io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0.DruidSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.alibaba.druid.pool.DruidDataSourceMBean;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.management.ObjectName;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DruidDataSourceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.alibaba.druid.stat.DruidDataSourceStatManager");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(isStatic()).and(named("addDataSource")),
        this.getClass().getName() + "$AddDataSourceAdvice");

    typeTransformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(isStatic()).and(named("removeDataSource")),
        this.getClass().getName() + "$RemoveDataSourceAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddDataSourceAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Object dataSource, @Advice.Return ObjectName objectName) {
      DruidDataSourceMBean druidDataSource = (DruidDataSourceMBean) dataSource;
      String dataSourceName =
          objectName.getKeyProperty("type") + "-" + objectName.getKeyProperty("id");
      telemetry().registerMetrics(druidDataSource, dataSourceName);
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveDataSourceAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Argument(0) Object dataSource) {
      DruidDataSourceMBean druidDataSource = (DruidDataSourceMBean) dataSource;
      telemetry().unregisterMetrics(druidDataSource);
    }
  }
}
