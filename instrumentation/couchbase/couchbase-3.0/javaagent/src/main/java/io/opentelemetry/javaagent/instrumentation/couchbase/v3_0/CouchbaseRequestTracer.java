/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_0;

import static com.couchbase.client.core.cnc.RequestTracer.DISPATCH_SPAN_NAME;
import static com.couchbase.client.core.cnc.RequestTracer.PAYLOAD_ENCODING_SPAN_NAME;
import static com.couchbase.client.core.cnc.RequestTracer.SERVICE_IDENTIFIER_ANALYTICS;
import static com.couchbase.client.core.cnc.RequestTracer.SERVICE_IDENTIFIER_KV;
import static com.couchbase.client.core.cnc.RequestTracer.SERVICE_IDENTIFIER_QUERY;
import static com.couchbase.client.core.cnc.RequestTracer.SERVICE_IDENTIFIER_SEARCH;
import static com.couchbase.client.core.cnc.RequestTracer.SERVICE_IDENTIFIER_VIEW;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.couchbase.client.core.cnc.InternalSpan;
import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.RequestTracer;
import com.couchbase.client.core.msg.RequestContext;
import com.couchbase.client.core.msg.kv.BaseKeyValueRequest;
import com.couchbase.client.core.service.ServiceType;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0.CouchbaseSpan;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0.CouchbaseTracer;
import java.time.Duration;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class CouchbaseRequestTracer implements RequestTracer {

  private final CouchbaseTracer tracer;

  public static RequestTracer create(Tracer tracer) {
    return new CouchbaseRequestTracer(new CouchbaseTracer(tracer, false, INTERNAL, true));
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
      RequestContext context = requireNonNull(requestContext);
      span.setRawAttribute("peer.service", mapServiceType(context.request().serviceType()));
      String operationId = context.request().operationId();
      if (operationId != null) {
        span.setRawAttribute("couchbase.operation_id", operationId);
      }
      if (context.request() instanceof BaseKeyValueRequest) {
        span.setRawAttribute(
            "couchbase.document_id",
            new String(((BaseKeyValueRequest) context.request()).key(), UTF_8));
      }
      if (context.clientContext() != null) {
        context
            .clientContext()
            .forEach(
                (key, value) ->
                    span.setRawAttribute("couchbase.client_context." + key, value.toString()));
      }
      span.end();
    }

    @Override
    public void requestContext(RequestContext requestContext) {
      this.requestContext = requestContext;
    }

    @Override
    public RequestContext requestContext() {
      return requestContext;
    }

    @Override
    public void startPayloadEncoding() {
      encodingSpan = tracer.startSpan(PAYLOAD_ENCODING_SPAN_NAME, span);
    }

    @Override
    public void stopPayloadEncoding() {
      requireNonNull(encodingSpan).end();
    }

    @Override
    public void startDispatch() {
      dispatchSpan = tracer.startSpan(DISPATCH_SPAN_NAME, span);
    }

    @Override
    public void stopDispatch() {
      CouchbaseSpan dispatch = requireNonNull(dispatchSpan);
      RequestContext context = requireNonNull(requestContext);
      long serverLatency = context.serverLatency();
      if (serverLatency > 0) {
        dispatch.setRawAttribute("peer.latency", serverLatency);
      }
      dispatch.end();
    }

    @Override
    public RequestSpan toRequestSpan() {
      return new AgentRequestSpan(span);
    }

    @Nullable
    private static String mapServiceType(ServiceType serviceType) {
      switch (serviceType) {
        case KV:
          return SERVICE_IDENTIFIER_KV;
        case QUERY:
          return SERVICE_IDENTIFIER_QUERY;
        case ANALYTICS:
          return SERVICE_IDENTIFIER_ANALYTICS;
        case VIEWS:
          return SERVICE_IDENTIFIER_VIEW;
        case SEARCH:
          return SERVICE_IDENTIFIER_SEARCH;
        default:
          return null;
      }
    }
  }
}
