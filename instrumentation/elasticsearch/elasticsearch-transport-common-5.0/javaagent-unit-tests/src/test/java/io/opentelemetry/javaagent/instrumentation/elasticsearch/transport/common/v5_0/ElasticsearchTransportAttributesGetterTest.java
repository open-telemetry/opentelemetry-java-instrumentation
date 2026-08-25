/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.io.IOException;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.transport.NodeDisconnectedException;
import org.elasticsearch.transport.RemoteTransportException;
import org.elasticsearch.transport.TransportException;
import org.junit.jupiter.api.Test;

class ElasticsearchTransportAttributesGetterTest {

  @Test
  void nodeDisconnectUsesExceptionClassErrorType() {
    NodeDisconnectedException error = new NodeDisconnectedException(null, "GetAction");

    assertThat(extractEndAttributes(error).get(ERROR_TYPE))
        .isEqualTo(emitStableDatabaseSemconv() ? NodeDisconnectedException.class.getName() : null);
  }

  @Test
  void noNodeAvailableUsesExceptionClassErrorType() {
    NoNodeAvailableException error = new NoNodeAvailableException("no nodes are available");

    assertThat(extractEndAttributes(error).get(ERROR_TYPE))
        .isEqualTo(emitStableDatabaseSemconv() ? NoNodeAvailableException.class.getName() : null);
  }

  @Test
  void plainElasticsearchExceptionUsesExceptionClassErrorType() {
    ElasticsearchException error = new ElasticsearchException("plain error");

    assertThat(extractEndAttributes(error).get(ERROR_TYPE))
        .isEqualTo(emitStableDatabaseSemconv() ? ElasticsearchException.class.getName() : null);
  }

  @Test
  void transportExceptionWithGenericCauseUsesExceptionClassErrorType() {
    TransportException error =
        new TransportException("transport error", new IOException("connection refused"));

    // TransportException does not implement ElasticsearchWrapperException, so Elasticsearch does
    // not treat it as a wrapper and unwrapCause() leaves it alone. The transport failure itself is
    // the most specific description of what went wrong here, so it is reported as-is.
    assertThat(extractEndAttributes(error).get(ERROR_TYPE))
        .isEqualTo(emitStableDatabaseSemconv() ? TransportException.class.getName() : null);
  }

  @Test
  void elasticsearchStatusExceptionUsesExceptionClassErrorType() {
    ElasticsearchStatusException error =
        new ElasticsearchStatusException("status error", RestStatus.INTERNAL_SERVER_ERROR);

    assertThat(extractEndAttributes(error).get(ERROR_TYPE))
        .isEqualTo(
            emitStableDatabaseSemconv() ? ElasticsearchStatusException.class.getName() : null);
  }

  @Test
  void remoteTransportExceptionUsesUnwrappedCauseErrorType() {
    RemoteTransportException error =
        new RemoteTransportException("remote error", new IndexNotFoundException("invalid-index"));

    assertThat(extractEndAttributes(error).get(ERROR_TYPE))
        .isEqualTo(emitStableDatabaseSemconv() ? IndexNotFoundException.class.getName() : null);
  }

  private static Attributes extractEndAttributes(Throwable error) {
    AttributesExtractor<ElasticTransportRequest, ActionResponse> extractor =
        DbClientAttributesExtractor.create(new ElasticsearchTransportAttributesGetter());
    AttributesBuilder attributes = Attributes.builder();
    extractor.onEnd(
        attributes,
        Context.root(),
        ElasticTransportRequest.create(new Object(), new Object(), null, null),
        null,
        error);
    return attributes.build();
  }
}
