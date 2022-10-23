/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import feign.Request;
import feign.Response;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

enum OpenFeignHttpClientAttributesGetter
    implements HttpClientAttributesGetter<ExecuteAndDecodeRequest, feign.Response> {
  INSTANCE;

  @Override
  public String method(ExecuteAndDecodeRequest request) {
    return request.getRequestTemplate().method();
  }

  @Override
  public List<String> requestHeader(ExecuteAndDecodeRequest request, String name) {
    Request req = request.getRequestTemplate().request();
    return new ArrayList<>(req.headers().getOrDefault(name, Collections.emptyList()));
  }

  @Nullable
  @Override
  public Integer statusCode(
      ExecuteAndDecodeRequest executeAndDecodeRequest,
      Response response,
      @Nullable Throwable error) {
    return response != null ? response.status() : null;
  }

  @Override
  public List<String> responseHeader(
      ExecuteAndDecodeRequest request, @Nullable Response response, String name) {
    return response != null
        ? new ArrayList<>(response.headers().getOrDefault(name, Collections.emptyList()))
        : Collections.emptyList();
  }

  @Override
  public String url(ExecuteAndDecodeRequest request) {
    return request.getTarget().url() + request.getTemplateUri().getPath();
  }

  @Nullable
  @Override
  public String flavor(ExecuteAndDecodeRequest request, @Nullable Response response) {
    return null;
  }
}
