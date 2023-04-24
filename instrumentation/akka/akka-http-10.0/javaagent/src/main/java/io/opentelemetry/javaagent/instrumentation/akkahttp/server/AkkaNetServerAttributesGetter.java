/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.scaladsl.model.HttpRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import javax.annotation.Nullable;

// TODO (trask) capture net attributes?
class AkkaNetServerAttributesGetter implements NetServerAttributesGetter<HttpRequest> {

  @Nullable
  @Override
  public String getProtocolName(HttpRequest request) {
    return AkkaHttpUtil.protocolName(request);
  }

  @Nullable
  @Override
  public String getProtocolVersion(HttpRequest request) {
    return AkkaHttpUtil.protocolVersion(request);
  }

  @Nullable
  @Override
  public String getHostName(HttpRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getHostPort(HttpRequest request) {
    return null;
  }
}
