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
package io.opentelemetry.auto.instrumentation.jersey;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;

public class JerseyDecorator
    extends HttpServerDecorator<ContainerRequest, ContainerRequest, ContainerResponse> {
  public static final JerseyDecorator DECORATE = new JerseyDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.jersey");

  @Override
  protected String method(final ContainerRequest httpServletRequest) {
    return httpServletRequest.getMethod();
  }

  @Override
  protected URI url(final ContainerRequest httpServletRequest) {
    return httpServletRequest.getRequestUri();
  }

  // https://github.com/eclipse-ee4j/jersey/issues/745
  @Override
  protected String peerHostIP(final ContainerRequest httpServletRequest) {
    return null;
  }

  // https://github.com/eclipse-ee4j/jersey/issues/745
  @Override
  protected Integer peerPort(final ContainerRequest httpServletRequest) {
    return null;
  }

  @Override
  protected Integer status(final ContainerResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }
}
