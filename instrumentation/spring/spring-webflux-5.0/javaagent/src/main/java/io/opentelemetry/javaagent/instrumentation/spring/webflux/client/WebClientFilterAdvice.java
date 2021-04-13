/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.client;

import net.bytebuddy.asm.Advice;
import org.springframework.web.reactive.function.client.WebClient;

public class WebClientFilterAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onBuild(@Advice.This WebClient.Builder builder) {
    builder.filters(WebClientHelper::addFilter);
  }
}
