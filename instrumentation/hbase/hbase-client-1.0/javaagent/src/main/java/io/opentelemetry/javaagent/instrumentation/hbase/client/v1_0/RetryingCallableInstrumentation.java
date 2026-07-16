/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.resetTableName;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseClientState.setTableName;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.hbase.TableName;

class RetryingCallableInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.hadoop.hbase.client.RegionServerCallable"))
        .or(extendsClass(named("org.apache.hadoop.hbase.client.RegionAdminServiceCallable")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("call"), getClass().getName() + "$RpcCallAdvice");
  }

  @SuppressWarnings("unused")
  public static class RpcCallAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.FieldValue(value = "tableName") TableName tableName) {
      if (tableName != null) {
        setTableName(tableName);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit() {
      resetTableName();
    }
  }
}
