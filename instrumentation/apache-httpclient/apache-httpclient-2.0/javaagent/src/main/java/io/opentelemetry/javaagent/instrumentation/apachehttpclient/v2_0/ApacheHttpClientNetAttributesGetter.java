/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpMethod;

final class ApacheHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<HttpMethod, HttpMethod> {

  @Override
  public String transport(HttpMethod request, @Nullable HttpMethod response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(HttpMethod request, @Nullable HttpMethod response) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getHost() : null;
  }

  @Override
  @Nullable
  public Integer peerPort(HttpMethod request, @Nullable HttpMethod response) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getPort() : null;
  }

  @Override
  @Nullable
  public String peerIp(HttpMethod request, @Nullable HttpMethod response) {
    return null;
  }
}
