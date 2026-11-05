/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;

/**
 * Marks the {@code ErrorInfo} of every request that was rejected before it was delivered.
 *
 * <p>The parsing error handler answers more than rejected requests: the controller stage routes a
 * request entity stream that failed and a response stream that failed to it as well, and those
 * happen after the request has been delivered and already has a server span of its own. The two
 * cannot be told apart at the handler, so a rejection is marked where it is made instead, and only
 * a marked one gets a span.
 *
 * <p>A {@code MessageStartError} is what the rejection of an undelivered request is carried in, and
 * the failures that follow a delivered request are not wrapped in one, which makes building one the
 * point where the two part company. It also covers the rejections that no parser produced, such as
 * a CONNECT request, which the stage that resolves the absolute uri turns into one of these itself.
 */
class MessageStartErrorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.http.impl.engine.parsing.ParserOutput$MessageStartError");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.FieldValue("info") ErrorInfo info) {
      PekkoHttpParsingErrorSingletons.markParsingError(info);
    }
  }
}
