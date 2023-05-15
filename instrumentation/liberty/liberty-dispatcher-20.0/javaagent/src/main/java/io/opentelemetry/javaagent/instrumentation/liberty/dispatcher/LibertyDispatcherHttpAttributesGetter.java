/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

public class LibertyDispatcherHttpAttributesGetter
    implements HttpServerAttributesGetter<LibertyRequest, LibertyResponse> {

  @Override
  @Nullable
  public String getMethod(LibertyRequest libertyRequest) {
    return libertyRequest.getMethod();
  }

  @Override
  public List<String> getRequestHeader(LibertyRequest libertyRequest, String name) {
    return libertyRequest.getHeaderValues(name);
  }

  @Override
  @Nullable
  public Integer getStatusCode(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse, @Nullable Throwable error) {
    return libertyResponse.getStatus();
  }

  @Override
  public List<String> getResponseHeader(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse, String name) {
    return libertyResponse.getHeaderValues(name);
  }

  @Override
  @Nullable
  public String getScheme(LibertyRequest libertyRequest) {
    return libertyRequest.getScheme();
  }

  @Nullable
  @Override
  public String getPath(LibertyRequest request) {
    return request.getRequestUri();
  }

  @Nullable
  @Override
  public String getQuery(LibertyRequest request) {
    return request.getQueryString();
  }
}
