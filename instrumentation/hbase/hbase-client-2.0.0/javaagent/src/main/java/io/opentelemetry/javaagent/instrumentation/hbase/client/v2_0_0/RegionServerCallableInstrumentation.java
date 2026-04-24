/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0_0.HbaseSingletons.TABLE_THREAD_LOCAL;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.hbase.TableName;

public final class RegionServerCallableInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.hadoop.hbase.client.RegionServerCallable")
        .or(named("org.apache.hadoop.hbase.client.RegionAdminServiceCallable"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("call")),
        RegionServerCallableInstrumentation.class.getName() + "$RpcCallAdvice");
  }

  public static class RpcCallAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.FieldValue(value = "tableName") TableName table) {
      if (table != null) {
        TABLE_THREAD_LOCAL.set(table.getNameAsString());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit() {
      TABLE_THREAD_LOCAL.remove();
    }
  }
}
