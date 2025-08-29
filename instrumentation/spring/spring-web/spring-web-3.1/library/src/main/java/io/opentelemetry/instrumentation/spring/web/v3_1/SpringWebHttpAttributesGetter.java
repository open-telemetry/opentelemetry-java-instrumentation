/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalAttributesGetter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

enum SpringWebHttpAttributesGetter
    implements HttpClientExperimentalAttributesGetter<HttpRequest, ClientHttpResponse> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  @Nullable
  public String getUrlFull(HttpRequest httpRequest) {
    return httpRequest.getURI().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeaders().getOrDefault(name, emptyList());
  }

  @Nullable private static final MethodHandle GET_STATUS_CODE;

  @Nullable private static final MethodHandle STATUS_CODE_VALUE;

  static {
    MethodHandle getStatusCode = null;
    MethodHandle statusCodeValue = null;
    Class<?> httpStatusCodeClass = null;

    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    try {
      httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatusCode");
    } catch (ClassNotFoundException e) {
      try {
        httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatus");
      } catch (ClassNotFoundException ignored) {
        // ignored
      }
    }

    if (httpStatusCodeClass != null) {
      try {
        getStatusCode =
            lookup.findVirtual(
                ClientHttpResponse.class,
                "getStatusCode",
                MethodType.methodType(httpStatusCodeClass));
        statusCodeValue =
            lookup.findVirtual(httpStatusCodeClass, "value", MethodType.methodType(int.class));
      } catch (NoSuchMethodException | IllegalAccessException ignored) {
        // ignored
      }
    }

    GET_STATUS_CODE = getStatusCode;
    STATUS_CODE_VALUE = statusCodeValue;
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, @Nullable Throwable error) {

    if (GET_STATUS_CODE == null || STATUS_CODE_VALUE == null) {
      return null;
    }

    try {
      Object statusCode = GET_STATUS_CODE.invoke(clientHttpResponse);
      return (int) STATUS_CODE_VALUE.invoke(statusCode);
    } catch (Throwable e) {
      return null;
    }
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, String name) {
    return clientHttpResponse.getHeaders().getOrDefault(name, emptyList());
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequest httpRequest) {
    return httpRequest.getURI().getHost();
  }

  @Override
  public Integer getServerPort(HttpRequest httpRequest) {
    return httpRequest.getURI().getPort();
  }

  private static final String URI_TEMPLATE_ATTRIBUTE =
      "org.springframework.web.client.RestClient.uriTemplate";
  @Nullable private static final MethodHandle GET_ATTRIBUTES;

  static {
    MethodHandle getAttributes = null;
    Class<?> httpRequestClass = null;

    try {
      httpRequestClass = Class.forName("org.springframework.http.HttpRequest");
    } catch (ClassNotFoundException e) {
      // ignored
    }

    if (httpRequestClass != null) {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      try {
        getAttributes =
            lookup.findVirtual(
                httpRequestClass, "getAttributes", MethodType.methodType(java.util.Map.class));
      } catch (NoSuchMethodException | IllegalAccessException ignored) {
        // ignored
      }
    }

    GET_ATTRIBUTES = getAttributes;
  }

  @Nullable
  @Override
  public String getUrlTemplate(HttpRequest httpRequest) {
    if (GET_ATTRIBUTES == null) {
      return null;
    }

    try {
      Object attributes = GET_ATTRIBUTES.invoke(httpRequest);
      if (attributes instanceof java.util.Map) {
        Map<?, ?> map = (Map<?, ?>) attributes;
        Object value = map.get(URI_TEMPLATE_ATTRIBUTE);
        if (value instanceof String) {
          return (String) value;
        }
      }
    } catch (Throwable e) {
      // ignore
    }

    return null;
  }
}
