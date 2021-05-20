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
    extends NetAttributesExtractor<HttpMethod, Void> {

  @Override
  protected String transport(HttpMethod httpMethod) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  protected @Nullable String peerName(HttpMethod httpMethod, @Nullable Void unused) {
    HostConfiguration hostConfiguration = httpMethod.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getHost() : null;
  }

  @Override
  protected @Nullable Integer peerPort(HttpMethod httpMethod, @Nullable Void unused) {
    HostConfiguration hostConfiguration = httpMethod.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getPort() : null;
  }

  @Override
  protected @Nullable String peerIp(HttpMethod httpMethod, @Nullable Void unused) {
    return null;
  }
}
