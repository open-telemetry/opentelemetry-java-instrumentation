/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.clickhouse.client.ClickHouseRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class ClickHouseRequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.clickhouse.client.ClickHouseRequest",
        "com.clickhouse.client.ClickHouseRequest$Mutation");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("copy", "seal", "write").and(takesNoArguments()),
        getClass().getName() + "$CopyTargetAdvice");
  }

  @SuppressWarnings("unused")
  public static class CopyTargetAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This ClickHouseRequest<?> request,
        @Advice.Return ClickHouseRequest<?> copiedRequest) {
      ClickHouseClientV1Singletons.copyServerTarget(request, copiedRequest);
    }
  }
}
