/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class AbstractNettyAdvice {
  public static void muzzleCheck(HttpRequest httpRequest) {
    HttpHeaders headers = httpRequest.headers();
  }
}
