/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import java.util.Map;

public interface PeerServiceResolver {

  public boolean isEmpty();

  public String resolveService(String host, Integer port, String path);

  static PeerServiceResolver create(Map<String, String> mapping) {
    return new PeerServiceResolverImpl(mapping);
  }
}
