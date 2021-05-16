/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpMethod;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpClientNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<HttpMethod, Void> {

  @Override
  protected String transport(HttpMethod httpMethod) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  protected InetSocketAddress getAddress(HttpMethod httpMethod, @Nullable Void unused) {
    HostConfiguration hostConfiguration = httpMethod.getHostConfiguration();
    return hostConfiguration != null
        ? new InetSocketAddress(hostConfiguration.getHost(), hostConfiguration.getPort())
        : null;
  }
}
