/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpMethod;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpClientNetAttributesExtractor
    extends NetAttributesExtractor<HttpMethod, HttpMethod> {

  @Override
  protected String transport(HttpMethod request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  protected @Nullable String peerName(HttpMethod request, @Nullable HttpMethod response) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getHost() : null;
  }

  @Override
  protected @Nullable Integer peerPort(HttpMethod request, @Nullable HttpMethod response) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getPort() : null;
  }

  @Override
  protected @Nullable String peerIp(HttpMethod request, @Nullable HttpMethod response) {
    return null;
  }
}
