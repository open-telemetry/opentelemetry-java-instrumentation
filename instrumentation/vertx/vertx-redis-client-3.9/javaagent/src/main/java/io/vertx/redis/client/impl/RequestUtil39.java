/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.redis.client.impl;

import io.vertx.redis.client.Request;
import java.util.Collections;
import java.util.List;

/**
 * Utility class to extract arguments from Redis Request (3.9 version)
 * Named RequestUtil39 to avoid conflicts with the 4.0 version
 */
public final class RequestUtil39 {

  public static List<byte[]> getArgs(Request request) {
//    System.out.println("manooo: here: "+request);
    if (request instanceof RequestImpl) {
      return ((RequestImpl) request).getArgs();
    }
    return Collections.emptyList();
  }

  private RequestUtil39() {}
}
