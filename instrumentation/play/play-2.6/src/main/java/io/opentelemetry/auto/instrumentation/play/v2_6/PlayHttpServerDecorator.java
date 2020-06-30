/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.play.v2_6;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import play.api.mvc.Request;
import play.api.mvc.Result;
import play.api.routing.HandlerDef;
import play.libs.typedmap.TypedKey;
import play.routing.Router;
import scala.Option;

// TODO Play does not creste server spans, it should not use HttpServerDecorator
@Slf4j
public class PlayHttpServerDecorator extends HttpServerDecorator<Request, Request, Result> {
  public static final PlayHttpServerDecorator DECORATE = new PlayHttpServerDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.play-2.6");

  private static final Method typedKeyGetUnderlying;

  static {
    Method typedKeyGetUnderlyingCheck = null;
    try {
      // This method was added in Play 2.6.8
      typedKeyGetUnderlyingCheck = TypedKey.class.getMethod("asScala");
    } catch (final NoSuchMethodException ignored) {
    }
    // Fallback
    if (typedKeyGetUnderlyingCheck == null) {
      try {
        typedKeyGetUnderlyingCheck = TypedKey.class.getMethod("underlying");
      } catch (final NoSuchMethodException ignored) {
      }
    }
    typedKeyGetUnderlying = typedKeyGetUnderlyingCheck;
  }

  @Override
  protected String method(final Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return new URI((request.secure() ? "https://" : "http://") + request.host() + request.uri());
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.remoteAddress();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return null;
  }

  @Override
  protected Integer status(final Result httpResponse) {
    return httpResponse.header().status();
  }

  @Override
  public Span onRequest(final Span span, final Request request) {
    super.onRequest(span, request);
    if (request != null) {
      // more about routes here:
      // https://github.com/playframework/playframework/blob/master/documentation/manual/releases/release26/migration26/Migration26.md
      Option<HandlerDef> defOption = null;
      if (typedKeyGetUnderlying != null) { // Should always be non-null but just to make sure
        try {
          defOption =
              request
                  .attrs()
                  .get(
                      (play.api.libs.typedmap.TypedKey<HandlerDef>)
                          typedKeyGetUnderlying.invoke(Router.Attrs.HANDLER_DEF));
        } catch (final IllegalAccessException | InvocationTargetException ignored) {
        }
      }
      if (defOption != null && !defOption.isEmpty()) {
        final String path = defOption.get().path();
        span.updateName(request.method() + " " + path);
      }
    }
    return span;
  }

  @Override
  public Span onError(final Span span, Throwable throwable) {
    span.setAttribute(Tags.HTTP_STATUS, 500);
    span.setStatus(Status.UNKNOWN);
    if (throwable != null
        // This can be moved to instanceof check when using Java 8.
        && throwable.getClass().getName().equals("java.util.concurrent.CompletionException")
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    while ((throwable instanceof InvocationTargetException
            || throwable instanceof UndeclaredThrowableException)
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    return super.onError(span, throwable);
  }
}
