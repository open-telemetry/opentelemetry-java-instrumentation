/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import org.elasticsearch.action.ActionListener
import org.elasticsearch.transport.RemoteTransportException

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class AbstractElasticsearchClientTest extends AgentInstrumentationSpecification {

  static class Result<RESPONSE> {
    CountDownLatch latch = new CountDownLatch(1)
    RESPONSE response
    Exception failure

    void setResponse(RESPONSE response) {
      this.response = response
      latch.countDown()
    }

    void setFailure(Exception failure) {
      this.failure = failure
      latch.countDown()
    }

    RESPONSE get() {
      latch.await(1, TimeUnit.MINUTES)
      if (response != null) {
        return response
      }
      throw failure
    }
  }

  static class ResultListener<T> implements ActionListener<T> {
    final Result<T> result
    final InstrumentationSpecification spec

    ResultListener(InstrumentationSpecification spec, Result<T> result) {
      this.spec = spec
      this.result = result
    }

    @Override
    void onResponse(T response) {
      spec.runWithSpan("callback") {
        result.setResponse(response)
      }
    }

    @Override
    void onFailure(Exception e) {
      if (e instanceof RemoteTransportException) {
        e = e.getCause()
      }
      spec.runWithSpan("callback") {
        result.setFailure(e)
      }
    }
  }
}
