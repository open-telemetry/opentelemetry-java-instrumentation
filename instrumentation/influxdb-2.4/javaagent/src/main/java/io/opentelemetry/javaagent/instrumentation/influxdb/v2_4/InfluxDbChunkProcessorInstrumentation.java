/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.EOFException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class InfluxDbChunkProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.influxdb.impl.InfluxDBImpl$JSONChunkProccesor",
        "org.influxdb.impl.InfluxDBImpl$MessagePackChunkProccesor",
        "com.squareup.moshi.JsonAdapter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("process"), getClass().getName() + "$ProcessAdvice");
    transformer.applyAdviceToMethod(
        named("fromJson").and(takesArgument(0, named("okio.BufferedSource"))),
        getClass().getName() + "$ReadAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Thrown @Nullable Throwable error) {
      InfluxDbQueryState state = Context.current().get(InfluxDbQueryState.KEY);
      if (state != null && error != null) {
        state.onFailure(error);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ReadAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Thrown @Nullable Throwable error) {
      InfluxDbQueryState state = Context.current().get(InfluxDbQueryState.KEY);
      if (state != null && error != null && !(error instanceof EOFException)) {
        state.onFailure(error);
      }
    }
  }
}
