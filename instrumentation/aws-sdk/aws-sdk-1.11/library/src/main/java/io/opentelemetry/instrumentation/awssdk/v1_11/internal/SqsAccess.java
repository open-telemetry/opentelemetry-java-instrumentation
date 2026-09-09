/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static java.util.Collections.emptyList;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

final class SqsAccess {
  private static final boolean enabled = PluginImplUtil.isImplPresent("SqsImpl");

  @NoMuzzle
  static boolean afterResponse(
      Request<?> request,
      Response<?> response,
      Timer timer,
      Context parentContext,
      TracingRequestHandler requestHandler) {
    return enabled
        && SqsImpl.afterResponse(request, response, timer, parentContext, requestHandler);
  }

  @NoMuzzle
  static AmazonWebServiceRequest beforeMarshalling(
      AmazonWebServiceRequest request,
      Instrumenter<SqsCreateRequest, Void> producerCreateInstrumenter,
      boolean messageCreateSpansEnabled) {
    return enabled
        ? SqsImpl.beforeMarshalling(request, producerCreateInstrumenter, messageCreateSpansEnabled)
        : request;
  }

  @NoMuzzle
  static boolean isBatchRequest(Request<?> request) {
    return enabled && SqsImpl.isBatchRequest(request);
  }

  @NoMuzzle
  static List<Context> getBatchMessageContexts(Request<?> request) {
    return enabled ? SqsImpl.getBatchMessageContexts(request) : emptyList();
  }

  @NoMuzzle
  @Nullable
  static Long getBatchMessageCount(Request<?> request) {
    return enabled ? SqsImpl.getBatchMessageCount(request) : null;
  }

  @NoMuzzle
  @Nullable
  static String getMessageAttribute(Request<?> request, String name) {
    return enabled ? SqsImpl.getMessageAttribute(request, name) : null;
  }

  @NoMuzzle
  static Collection<String> getMessageAttributeNames(Request<?> request) {
    return enabled ? SqsImpl.getMessageAttributeNames(request) : emptyList();
  }

  @NoMuzzle
  @Nullable
  static String getMessageId(@Nullable Response<?> response) {
    return enabled ? SqsImpl.getMessageId(response) : null;
  }

  private SqsAccess() {}
}
