/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;

final class ApacheHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<HttpMethod, HttpMethod> {

  @Override
  public String getProtocolName(HttpMethod request, @Nullable HttpMethod response) {
    return "http";
  }

  @Nullable
  @Override
  public String getProtocolVersion(HttpMethod request, @Nullable HttpMethod response) {
    if (request instanceof HttpMethodBase) {
      return ((HttpMethodBase) request).isHttp11() ? "1.1" : "1.0";
    }
    return null;
  }

  @Override
  @Nullable
  public String getPeerName(HttpMethod request) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getHost() : null;
  }

  @Override
  @Nullable
  public Integer getPeerPort(HttpMethod request) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getPort() : null;
  }
}
