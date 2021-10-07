/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpMethod;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpClientNetAttributesExtractor
    extends NetClientAttributesExtractor<HttpMethod, HttpMethod> {

  @Override
  public String transport(HttpMethod request, @Nullable HttpMethod response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(HttpMethod request, @Nullable HttpMethod response) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getHost() : null;
  }

  @Override
  public @Nullable Integer peerPort(HttpMethod request, @Nullable HttpMethod response) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getPort() : null;
  }

  @Override
  public @Nullable String peerIp(HttpMethod request, @Nullable HttpMethod response) {
    return null;
  }
}
