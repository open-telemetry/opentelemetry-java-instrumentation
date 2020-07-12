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

package io.opentelemetry.auto.instrumentation.ratpack;

import com.google.common.net.HostAndPort;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import ratpack.handling.Context;
import ratpack.http.HttpUrlBuilder;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.http.Status;
import ratpack.server.PublicAddress;

// TODO Ratpack does not create server spans, should not use HttpServerDecorator
public class RatpackServerDecorator extends HttpServerDecorator<Request, Request, Response> {
  public static final RatpackServerDecorator DECORATE = new RatpackServerDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.ratpack-1.4");

  @Override
  protected String method(final Request request) {
    return request.getMethod().getName();
  }

  @Override
  protected URI url(final Request request) {
    final HostAndPort address = request.getLocalAddress();
    // This call implicitly uses request via a threadlocal provided by ratpack.
    final PublicAddress publicAddress =
        PublicAddress.inferred(address.getPort() == 443 ? "https" : "http");
    final HttpUrlBuilder url =
        publicAddress.builder().path(request.getPath()).params(request.getQueryParams());
    return url.build();
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.getRemoteAddress().getHost();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return request.getRemoteAddress().getPort();
  }

  @Override
  protected Integer status(final Response response) {
    final Status status = response.getStatus();
    if (status != null) {
      return status.getCode();
    } else {
      return null;
    }
  }

  public Span onContext(final Span span, final Context ctx) {

    String description = ctx.getPathBinding().getDescription();
    if (description == null || description.isEmpty()) {
      description = "/";
    } else if (!description.startsWith("/")) {
      description = "/" + description;
    }

    span.updateName(description);

    return span;
  }

  @Override
  public Span onError(final Span span, final Throwable throwable) {
    // Attempt to unwrap ratpack.handling.internal.HandlerException without direct reference.
    if (throwable instanceof Error && throwable.getCause() != null) {
      return super.onError(span, throwable.getCause());
    }
    return super.onError(span, throwable);
  }
}
