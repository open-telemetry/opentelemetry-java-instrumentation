/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class IpcCallInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.hadoop.hbase.ipc.Call");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("callComplete"), "org.apache.hadoop.hbase.ipc.HbaseCallAdvice$CallCompleteAdvice");
    transformer.applyAdviceToMethod(
        named("setTimeout"), "org.apache.hadoop.hbase.ipc.HbaseCallAdvice$SetTimeoutAdvice");
  }
}
