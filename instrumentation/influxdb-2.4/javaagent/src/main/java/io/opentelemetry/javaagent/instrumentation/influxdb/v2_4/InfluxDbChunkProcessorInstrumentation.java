/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class InfluxDbChunkProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.influxdb.impl.InfluxDBImpl$JSONChunkProccesor",
        "org.influxdb.impl.InfluxDBImpl$MessagePackChunkProccesor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("process"), getClass().getName() + "$ProcessAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Thrown @Nullable Throwable error) {
      InfluxDbQueryState state = currentContext().get(InfluxDbQueryState.KEY);
      if (state != null && error != null) {
        // Newer clients may swallow decoding failures after canceling, without a failure consumer.
        state.onFailure(error);
      }
    }
  }
}
