/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Jetty8NetAttributesExtractor
    extends NetAttributesExtractor<HttpServletRequest, HttpServletResponse> {
  @Override
  public @Nullable String transport(HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  public @Nullable String peerName(
      HttpServletRequest httpServletRequest, @Nullable HttpServletResponse httpServletResponse) {
    return null;
  }

  @Override
  public @Nullable Integer peerPort(
      HttpServletRequest httpServletRequest, @Nullable HttpServletResponse httpServletResponse) {
    return null;
  }

  @Override
  public @Nullable String peerIp(
      HttpServletRequest httpServletRequest, @Nullable HttpServletResponse httpServletResponse) {
    return null;
  }
}
