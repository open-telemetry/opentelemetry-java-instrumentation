/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.redis.client.impl;

import static java.util.Collections.emptyList;

import io.vertx.redis.client.Request;
import java.util.List;

public final class RequestUtil {

  public static List<byte[]> getArgs(Request request) {
    if (request instanceof RequestImpl) {
      return ((RequestImpl) request).getArgs();
    }
    return emptyList();
  }

  private RequestUtil() {}
}
