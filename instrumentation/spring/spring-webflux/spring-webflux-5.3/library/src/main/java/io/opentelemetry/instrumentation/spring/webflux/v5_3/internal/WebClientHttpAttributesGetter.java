/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalAttributesGetter;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.api.semconv.http.HttpErrorType;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class WebClientHttpAttributesGetter
    implements HttpClientExperimentalAttributesGetter<ClientRequest, ClientResponse> {
  private static final Logger logger =
      Logger.getLogger(WebClientHttpAttributesGetter.class.getName());

  @Nullable
  @Override
  public String getUrlTemplate(ClientRequest request) {
    // Populated by Spring's string-template URI overloads since 5.0.5.
    Object template =
        request
            .attributes()
            .get("org.springframework.web.reactive.function.client.WebClient.uriTemplate");
    if (!(template instanceof String)) {
      return null;
    }
    return extractPathTemplate((String) template);
  }

  // visible for testing
  @Nullable
  static String extractPathTemplate(String template) {
    try {
      String path = UriComponentsBuilder.fromUriString(template).build().getPath();
      return path == null || path.isEmpty() ? null : path;
    } catch (IllegalArgumentException e) {
      logger.log(FINE, "Unable to parse WebClient URI template", e);
      return null;
    }
  }

  @Override
  public String getUrlFull(ClientRequest request) {
    return request.url().toString();
  }

  @Override
  public String getHttpRequestMethod(ClientRequest request) {
    return request.method().name();
  }

  @Override
  public List<String> getHttpRequestHeader(ClientRequest request, String name) {
    return HeaderUtil.getHeader(request.headers(), name);
  }

  @Override
  public Collection<String> getHttpRequestHeaderNames(ClientRequest request) {
    return HeaderUtil.getKeys(request.headers());
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      ClientRequest request, ClientResponse response, @Nullable Throwable error) {
    return StatusCodes.get(response);
  }

  @Override
  public List<String> getHttpResponseHeader(
      ClientRequest request, ClientResponse response, String name) {
    return response.headers().header(name);
  }

  @Override
  public Collection<String> getHttpResponseHeaderNames(
      ClientRequest request, ClientResponse response) {
    return HeaderUtil.getKeys(response.headers().asHttpHeaders());
  }

  @Nullable
  @Override
  public String getServerAddress(ClientRequest request) {
    return request.url().getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(ClientRequest request) {
    URI url = request.url();
    return HttpConstants.portOrDefaultFromScheme(url.getPort(), url.getScheme());
  }

  @Nullable
  @Override
  public String getErrorType(
      ClientRequest request, @Nullable ClientResponse response, @Nullable Throwable error) {
    // if both response and error are null it means the request has been cancelled -- see the
    // WebClientTracingFilter class
    if (response == null && error == null) {
      return "cancelled";
    }
    return HttpErrorType.forClient(this, request, response, error);
  }
}
