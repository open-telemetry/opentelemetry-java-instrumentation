/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.resetTableName;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.setTableName;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.hbase.TableName;

class RegionServerCallableInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.apache.hadoop.hbase.client.RegionServerCallable",
        "org.apache.hadoop.hbase.client.RegionAdminServiceCallable");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("call"), getClass().getName() + "$RpcCallAdvice");
  }

  @SuppressWarnings("unused")
  public static class RpcCallAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.FieldValue(value = "tableName") TableName table) {
      if (table != null) {
        setTableName(table);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit() {
      resetTableName();
    }
  }
}
