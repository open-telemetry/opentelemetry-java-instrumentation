/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import com.alibaba.nacos.api.remote.request.Request;
import javax.annotation.Nullable;

public class NacosClientRequest {
  private final Request request;
  private final String category;
  private final String operation;
  private final String peerAddress;
  @Nullable private final String peerHost;
  @Nullable private final Integer peerPort;

  public NacosClientRequest(
      Request request,
      String category,
      String operation,
      String peerAddress,
      @Nullable String peerHost,
      @Nullable Integer peerPort) {
    this.request = request;
    this.category = category;
    this.operation = operation;
    this.peerAddress = peerAddress;
    this.peerHost = peerHost;
    this.peerPort = peerPort;
  }

  public Request request() {
    return request;
  }

  public String category() {
    return category;
  }

  public String operation() {
    return operation;
  }

  public String spanName() {
    return "Nacos/" + operation;
  }

  public String peerAddress() {
    return peerAddress;
  }

  @Nullable
  public String peerHost() {
    return peerHost;
  }

  @Nullable
  public Integer peerPort() {
    return peerPort;
  }
}
