/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboNetClientAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<DubboRequest, Result> {

  @Override
  @Nullable
  public String transport(DubboRequest request, @Nullable Result response) {
    return null;
  }

  @Nullable
  @Override
  public String peerName(DubboRequest request) {
    return request.url().getHost();
  }

  @Override
  public Integer peerPort(DubboRequest request) {
    return request.url().getPort();
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(
      DubboRequest request, @Nullable Result response) {
    return request.remoteAddress();
  }
}
