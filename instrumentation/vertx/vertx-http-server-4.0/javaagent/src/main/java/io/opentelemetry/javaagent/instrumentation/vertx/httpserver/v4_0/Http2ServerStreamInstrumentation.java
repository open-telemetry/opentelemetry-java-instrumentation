/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class Http2ServerStreamInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.vertx.core.http.impl.Http2ServerStream", "io.vertx.core.http.impl.VertxHttp2Stream");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("doWriteHeaders")
            .and(takesArgument(0, named("io.netty.handler.codec.http2.Http2Headers")))
            .and(takesArgument(1, boolean.class))
            .and(takesArgument(2, named("io.vertx.core.Handler")))
            .and(isDeclaredBy(named("io.vertx.core.http.impl.Http2ServerStream")))
            .and(isDeclaredBy(not(declaresField(named("request"))))),
        getClass().getName() + "$HeadersAdvice");
    transformer.applyAdviceToMethod(
        named("doWriteHeaders")
            .and(takesArgument(0, named("io.netty.handler.codec.http2.Http2Headers")))
            .and(takesArgument(1, boolean.class))
            .and(takesArgument(2, named("io.vertx.core.Handler")))
            .and(isDeclaredBy(named("io.vertx.core.http.impl.Http2ServerStream")))
            .and(isDeclaredBy(declaresField(named("request")))),
        getClass().getName() + "$StreamRequestHeadersAdvice");
    transformer.applyAdviceToMethod(
        named("doWriteHeaders")
            .and(takesArgument(0, named("io.netty.handler.codec.http2.Http2Headers")))
            .and(takesArgument(1, boolean.class))
            .and(takesArgument(2, boolean.class))
            .and(takesArgument(3, named("io.vertx.core.Handler")))
            .and(isDeclaredBy(named("io.vertx.core.http.impl.Http2ServerStream"))),
        getClass().getName() + "$StreamRequestHeadersWithFlushAdvice");
    transformer.applyAdviceToMethod(
        named("writeData")
            .and(takesArguments(3))
            .and(takesArgument(0, named("io.netty.buffer.ByteBuf")))
            .and(takesArgument(1, boolean.class))
            .and(takesArgument(2, named("io.vertx.core.Handler")))
            .and(isDeclaredBy(named("io.vertx.core.http.impl.VertxHttp2Stream"))),
        getClass().getName() + "$DataAdvice");
  }

  @SuppressWarnings("unused")
  public static class StreamRequestHeadersAdvice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Handler<AsyncResult<Void>> onEnter(
        @Advice.FieldValue("request") Object streamHandler,
        @Advice.Argument(1) boolean end,
        @Advice.Argument(2) @Nullable Handler<AsyncResult<Void>> handler) {
      return ResponseCompletionHandler.wrapHeaders(streamHandler, end, handler);
    }
  }

  @SuppressWarnings("unused")
  public static class HeadersAdvice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Handler<AsyncResult<Void>> onEnter(
        @Advice.FieldValue("response") HttpServerResponse response,
        @Advice.Argument(1) boolean end,
        @Advice.Argument(2) @Nullable Handler<AsyncResult<Void>> handler) {
      return ResponseCompletionHandler.wrapHeaders(response, end, handler);
    }
  }

  @SuppressWarnings("unused")
  public static class StreamRequestHeadersWithFlushAdvice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(3))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Handler<AsyncResult<Void>> onEnter(
        @Advice.FieldValue("request") Object streamHandler,
        @Advice.Argument(1) boolean end,
        @Advice.Argument(3) @Nullable Handler<AsyncResult<Void>> handler) {
      return ResponseCompletionHandler.wrapHeaders(streamHandler, end, handler);
    }
  }

  @SuppressWarnings("unused")
  public static class DataAdvice {
    @Nullable
    @AssignReturned.ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Handler<AsyncResult<Void>> onEnter(
        @Advice.FieldValue("conn") Object connection,
        @Advice.Argument(1) boolean end,
        @Advice.Argument(2) @Nullable Handler<AsyncResult<Void>> handler) {
      return ResponseCompletionHandler.wrapData(connection, end, handler);
    }
  }
}
