/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import java.util.Map;
import javax.annotation.Nullable;

public interface PeerServiceResolver {

  public boolean isEmpty();

  @Nullable
  public String resolveService(String host, @Nullable Integer port, @Nullable String path);

  static PeerServiceResolver create(Map<String, String> mapping) {
    return new PeerServiceResolverImpl(mapping);
  }
}
