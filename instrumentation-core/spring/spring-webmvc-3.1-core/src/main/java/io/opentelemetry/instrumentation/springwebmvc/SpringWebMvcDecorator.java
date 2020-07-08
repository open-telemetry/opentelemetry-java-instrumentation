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

package io.opentelemetry.instrumentation.springwebmvc;

import io.grpc.Context;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracerBase;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.instrumentation.servlet.HttpServletRequestGetter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;

public class SpringWebMvcDecorator
    extends HttpServerTracerBase<HttpServletRequest, HttpServletRequest, HttpServletRequest> {

  public static final SpringWebMvcDecorator DECORATE = new SpringWebMvcDecorator();

  public Span afterStart(final Span span) {
    assert span != null;
    return span;
  }

  public Span beforeFinish(final Span span) {
    assert span != null;
    return span;
  }

  @Override
  public void onError(final Span span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      span.setStatus(Status.UNKNOWN);
      addThrowable(span, throwable);
    }
  }

  @Override
  protected Integer peerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  protected String peerHostIP(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  @Override
  protected Getter<HttpServletRequest> getGetter() {
    return HttpServletRequestGetter.GETTER;
  }

  @Override
  protected URI url(HttpServletRequest request) throws URISyntaxException {
    return new URI(request.getRequestURI());
  }

  @Override
  protected String method(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  protected void attachServerContext(Context context, HttpServletRequest request) {
    request.setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  @Override
  public Context getServerContext(HttpServletRequest request) {
    Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
    return context instanceof Context ? (Context) context : null;
  }
}
