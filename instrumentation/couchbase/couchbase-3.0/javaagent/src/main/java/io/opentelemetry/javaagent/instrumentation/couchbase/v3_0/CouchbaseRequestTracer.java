/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_0;

import static com.couchbase.client.core.cnc.RequestTracer.DISPATCH_SPAN_NAME;
import static com.couchbase.client.core.cnc.RequestTracer.PAYLOAD_ENCODING_SPAN_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.couchbase.client.core.cnc.InternalSpan;
import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.RequestTracer;
import com.couchbase.client.core.msg.RequestContext;
import com.couchbase.client.core.msg.kv.BaseKeyValueRequest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbSchemaUrls;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0.CouchbaseSpan;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0.CouchbaseTracer;
import java.time.Duration;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class CouchbaseRequestTracer implements RequestTracer {

  private final CouchbaseTracer tracer;

  public static RequestTracer create(OpenTelemetry openTelemetry) {
    String instrumentationName = "io.opentelemetry.couchbase-3.0";
    Tracer tracer =
        openTelemetry
            .tracerBuilder(instrumentationName)
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
            .build();
    Logger exceptionLogger =
        openTelemetry
            .getLogsBridge()
            .loggerBuilder(instrumentationName)
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
            .build();
    return new CouchbaseRequestTracer(
        new CouchbaseTracer(tracer, exceptionLogger, false, true, true));
  }

  private CouchbaseRequestTracer(CouchbaseTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public InternalSpan internalSpan(String operationName, RequestSpan requestSpan) {
    if (operationName == null || operationName.isEmpty()) {
      throw new IllegalArgumentException("OperationName must not be null or empty");
    }
    return new AgentInternalSpan(tracer, operationName, unwrap(requestSpan));
  }

  @Override
  public RequestSpan requestSpan(String operationName, RequestSpan parent) {
    return new AgentRequestSpan(tracer.startSpan(operationName, unwrap(parent)));
  }

  @Nullable
  private static CouchbaseSpan unwrap(@Nullable RequestSpan requestSpan) {
    if (requestSpan == null) {
      return null;
    }
    if (requestSpan instanceof AgentRequestSpan) {
      return ((AgentRequestSpan) requestSpan).delegate;
    }
    throw new IllegalArgumentException("RequestSpan must be created by the OpenTelemetry agent");
  }

  @Override
  public Mono<Void> start() {
    return Mono.empty();
  }

  @Override
  public Mono<Void> stop(Duration timeout) {
    return Mono.empty();
  }

  private static final class AgentRequestSpan implements RequestSpan {

    private final CouchbaseSpan delegate;

    private AgentRequestSpan(CouchbaseSpan delegate) {
      this.delegate = delegate;
    }

    @Override
    public void finish() {
      delegate.end();
    }
  }

  private static final class AgentInternalSpan implements InternalSpan {

    private final CouchbaseTracer tracer;
    private final CouchbaseSpan span;
    @Nullable private volatile RequestContext requestContext;
    @Nullable private volatile CouchbaseSpan dispatchSpan;
    @Nullable private volatile CouchbaseSpan encodingSpan;

    private AgentInternalSpan(
        CouchbaseTracer tracer, String operationName, @Nullable CouchbaseSpan parent) {
      this.tracer = tracer;
      this.span = tracer.startSpan(operationName, parent);
    }

    @Override
    public void finish() {
      endEncodingSpan();
      endDispatchSpan();
      RequestContext context = requireNonNull(requestContext);
      String operationId = context.request().operationId();
      if (operationId != null) {
        span.setExperimentalAttribute("couchbase.operation_id", operationId);
      }
      if (context.request() instanceof BaseKeyValueRequest) {
        span.setExperimentalAttribute(
            "couchbase.document_id",
            new String(((BaseKeyValueRequest) context.request()).key(), UTF_8));
      }
      if (context.clientContext() != null) {
        context
            .clientContext()
            .forEach(
                (key, value) -> {
                  if (value != null) {
                    span.setExperimentalAttribute(
                        "couchbase.client_context." + key, value.toString());
                  }
                });
      }
      span.end();
    }

    @Override
    public void requestContext(RequestContext requestContext) {
      this.requestContext = requestContext;
    }

    @Override
    @Nullable
    public RequestContext requestContext() {
      return requestContext;
    }

    @Override
    public void startPayloadEncoding() {
      endEncodingSpan();
      encodingSpan = tracer.startSpan(PAYLOAD_ENCODING_SPAN_NAME, span);
    }

    @Override
    public void stopPayloadEncoding() {
      CouchbaseSpan encoding = requireNonNull(encodingSpan);
      encodingSpan = null;
      encoding.end();
    }

    @Override
    public void startDispatch() {
      endDispatchSpan();
      dispatchSpan = tracer.startSpan(DISPATCH_SPAN_NAME, span);
    }

    @Override
    public void stopDispatch() {
      CouchbaseSpan dispatch = requireNonNull(dispatchSpan);
      dispatchSpan = null;
      RequestContext context = requireNonNull(requestContext);
      long serverLatency = context.serverLatency();
      if (serverLatency > 0) {
        dispatch.setExperimentalAttribute("peer.latency", serverLatency);
      }
      dispatch.end();
    }

    @Override
    public RequestSpan toRequestSpan() {
      return new AgentRequestSpan(span);
    }

    private void endEncodingSpan() {
      CouchbaseSpan encoding = encodingSpan;
      encodingSpan = null;
      if (encoding != null) {
        encoding.end();
      }
    }

    private void endDispatchSpan() {
      CouchbaseSpan dispatch = dispatchSpan;
      dispatchSpan = null;
      if (dispatch != null) {
        dispatch.end();
      }
    }
  }
}
