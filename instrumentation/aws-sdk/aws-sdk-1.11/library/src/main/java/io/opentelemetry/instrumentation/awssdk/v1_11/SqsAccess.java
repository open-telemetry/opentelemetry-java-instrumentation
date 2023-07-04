/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;

final class SqsAccess {
  private SqsAccess() {}

  private static final boolean enabled = PluginImplUtil.isImplPresent("SqsImpl");

  @NoMuzzle
  static boolean afterResponse(
      Request<?> request,
      Response<?> response,
      Instrumenter<Request<?>, Response<?>> consumerInstrumenter) {
    return enabled && SqsImpl.afterResponse(request, response, consumerInstrumenter);
  }

  @NoMuzzle
  static boolean beforeMarshalling(AmazonWebServiceRequest request) {
    return enabled && SqsImpl.beforeMarshalling(request);
  }
}
