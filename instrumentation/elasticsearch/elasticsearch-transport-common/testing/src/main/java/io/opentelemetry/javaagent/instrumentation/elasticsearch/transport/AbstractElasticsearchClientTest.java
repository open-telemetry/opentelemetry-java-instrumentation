/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.http.BindHttpException;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.BindTransportException;
import org.elasticsearch.transport.RemoteTransportException;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractElasticsearchClientTest {

  protected static final long TIMEOUT = TimeUnit.SECONDS.toMillis(10);

  protected static final AttributeKey<String> ELASTICSEARCH_ACTION =
      AttributeKey.stringKey("elasticsearch.action");
  protected static final AttributeKey<String> ELASTICSEARCH_REQUEST =
      AttributeKey.stringKey("elasticsearch.request");
  protected static final AttributeKey<String> ELASTICSEARCH_REQUEST_INDICES =
      AttributeKey.stringKey("elasticsearch.request.indices");
  protected static final AttributeKey<String> ELASTICSEARCH_TYPE =
      AttributeKey.stringKey("elasticsearch.type");
  protected static final AttributeKey<String> ELASTICSEARCH_ID =
      AttributeKey.stringKey("elasticsearch.id");
  protected static final AttributeKey<Long> ELASTICSEARCH_VERSION =
      AttributeKey.longKey("elasticsearch.version");

  protected static final String EXPERIMENTAL_FLAG =
      "otel.instrumentation.elasticsearch.experimental-span-attributes";

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  protected static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected abstract Client client();

  protected static void startNode(Node node) {
    // retry when starting elasticsearch fails with
    // org.elasticsearch.http.BindHttpException: Failed to resolve host [[]]
    // Caused by: java.net.SocketException: No such device (getFlags() failed)
    // or
    // org.elasticsearch.transport.BindTransportException: Failed to resolve host null
    // Caused by: java.net.SocketException: No such device (getFlags() failed)
    await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptionsMatching(
            it -> it instanceof BindHttpException || it instanceof BindTransportException)
        .until(
            () -> {
              node.start();
              return true;
            });
  }

  protected ClusterHealthStatus clusterHealthSync() throws Exception {
    ActionFuture<ClusterHealthResponse> result =
        client().admin().cluster().health(new ClusterHealthRequest());
    return testing.runWithSpan("callback", () -> result.get().getStatus());
  }

  protected ClusterHealthStatus clusterHealthAsync() {
    Result<ClusterHealthResponse> result = new Result<>();
    client().admin().cluster().health(new ClusterHealthRequest(), new ResultListener<>(result));
    return result.get().getStatus();
  }

  protected GetResponse prepareGetSync(String indexName, String indexType, String id) {
    try {
      return client().prepareGet(indexName, indexType, id).get();
    } finally {
      testing.runWithSpan("callback", () -> {});
    }
  }

  protected GetResponse prepareGetAsync(String indexName, String indexType, String id) {
    Result<GetResponse> result = new Result<>();
    client().prepareGet(indexName, indexType, id).execute(new ResultListener<>(result));
    return result.get();
  }

  protected static String experimental(String value) {
    if (!Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
      return null;
    }
    return value;
  }

  protected static Long experimental(long value) {
    if (!Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
      return null;
    }
    return value;
  }

  static class Result<RESPONSE> {
    private final CountDownLatch latch = new CountDownLatch(1);
    private RESPONSE response;
    private Throwable failure;

    void setResponse(RESPONSE response) {
      this.response = response;
      latch.countDown();
    }

    void setFailure(Throwable failure) {
      this.failure = failure;
      latch.countDown();
    }

    RESPONSE get() {
      try {
        latch.await(1, TimeUnit.MINUTES);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
      if (response != null) {
        return response;
      }
      if (failure instanceof RuntimeException) {
        throw (RuntimeException) failure;
      }
      throw new IllegalStateException(failure);
    }
  }

  static class ResultListener<T> implements ActionListener<T> {
    final Result<T> result;

    ResultListener(Result<T> result) {
      this.result = result;
    }

    @Override
    public void onResponse(T response) {
      testing.runWithSpan("callback", () -> result.setResponse(response));
    }

    @Override
    public void onFailure(Exception exception) {
      Throwable throwable = exception;
      if (throwable instanceof RemoteTransportException) {
        throwable = throwable.getCause();
      }
      Throwable finalThrowable = throwable;
      testing.runWithSpan("callback", () -> result.setFailure(finalThrowable));
    }
  }
}
