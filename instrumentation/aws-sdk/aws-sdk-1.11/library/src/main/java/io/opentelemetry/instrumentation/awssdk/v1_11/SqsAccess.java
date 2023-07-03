/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;

final class SqsAccess {
  private SqsAccess() {}

  private static final boolean enabled = PluginImplUtil.isImplPresent("SqsImpl");

  @NoMuzzle
  public static boolean isReceiveMessageRequest(AmazonWebServiceRequest originalRequest) {
    return enabled && SqsImpl.isReceiveMessageRequest(originalRequest);
  }
}
