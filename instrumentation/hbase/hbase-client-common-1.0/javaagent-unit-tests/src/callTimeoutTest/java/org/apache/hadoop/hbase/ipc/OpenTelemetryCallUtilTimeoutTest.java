/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.hadoop.hbase.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.RequestAndContext;
import java.io.IOException;
import org.apache.hadoop.hbase.client.MetricsConnection;
import org.junit.jupiter.api.Test;

class OpenTelemetryCallUtilTimeoutTest {

  @Test
  void clearsAcceptedTimeoutStateOnce() {
    Call call = newCall();
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);
    IOException timeoutError = new IOException("timeout");

    call.setTimeout(timeoutError);

    assertThat(
            OpenTelemetryCallUtil.getAndClearRequestAndContextIfError(
                call, call.error, timeoutError))
        .isSameAs(requestAndContext);
    assertThat(
            OpenTelemetryCallUtil.getAndClearRequestAndContextIfError(
                call, call.error, timeoutError))
        .isNull();
  }

  @Test
  void keepsStateWhenTimeoutIsRejected() {
    Call call = newCall();
    call.setResponse(null, null);
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);
    IOException timeoutError = new IOException("timeout");

    call.setTimeout(timeoutError);

    assertThat(
            OpenTelemetryCallUtil.getAndClearRequestAndContextIfError(
                call, call.error, timeoutError))
        .isNull();
    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call))
        .isSameAs(requestAndContext);
  }

  @Test
  void keepsStateWhenAnotherErrorCompletedTheCall() {
    Call call = newCall();
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);
    call.setException(new IOException("failure"));
    IOException timeoutError = new IOException("timeout");

    call.setTimeout(timeoutError);

    assertThat(
            OpenTelemetryCallUtil.getAndClearRequestAndContextIfError(
                call, call.error, timeoutError))
        .isNull();
    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call))
        .isSameAs(requestAndContext);
  }

  @Test
  void rejectsTimeoutWithDifferentErrorIdentity() {
    Call call = newCall();
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);
    IOException timeoutError = new IOException("timeout");

    call.setTimeout(timeoutError);

    assertThat(
            OpenTelemetryCallUtil.getAndClearRequestAndContextIfError(
                call, call.error, new IOException("timeout")))
        .isNull();
    assertThat(
            OpenTelemetryCallUtil.getAndClearRequestAndContextIfError(
                call, call.error, timeoutError))
        .isSameAs(requestAndContext);
  }

  @Test
  void clearsNormalCompletionState() {
    Call call = newCall();
    RequestAndContext requestAndContext = requestAndContext();
    OpenTelemetryCallUtil.setRequestAndContext(call, requestAndContext);

    call.setResponse(null, null);

    assertThat(OpenTelemetryCallUtil.getAndClearRequestAndContext(call))
        .isSameAs(requestAndContext);
  }

  private static Call newCall() {
    return new Call(
        1, null, null, null, null, 0, 0, ignored -> {}, new MetricsConnection.CallStats());
  }

  private static RequestAndContext requestAndContext() {
    HbaseRequest request = HbaseRequest.create("Get", null, null, null, null, null, null);
    return RequestAndContext.create(request, () -> {}, Context.root());
  }
}
