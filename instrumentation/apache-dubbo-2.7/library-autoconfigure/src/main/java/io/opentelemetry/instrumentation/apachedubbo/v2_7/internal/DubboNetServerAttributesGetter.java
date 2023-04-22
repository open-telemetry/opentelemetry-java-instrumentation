/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboNetServerAttributesGetter
    extends InetSocketAddressNetServerAttributesGetter<DubboRequest> {

  @Nullable
  @Override
  public String getHostName(DubboRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getHostPort(DubboRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(DubboRequest request) {
    return request.remoteAddress();
  }

  @Nullable
  @Override
  protected InetSocketAddress getHostSocketAddress(DubboRequest request) {
    return request.localAddress();
  }
}
