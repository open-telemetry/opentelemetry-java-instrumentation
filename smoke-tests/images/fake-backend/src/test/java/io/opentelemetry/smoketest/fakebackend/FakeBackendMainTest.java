/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.fakebackend;

import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import org.junit.jupiter.api.Test;

class FakeBackendMainTest {

  @Test
  void storesHttpProtobufTraceRequest() throws Exception {
    RequestsStorage storage = new RequestsStorage();
    ExportTraceServiceRequest exportRequest =
        ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(ResourceSpans.getDefaultInstance())
            .build();

    AggregatedHttpResponse response = sendTraceRequest(storage, exportRequest.toByteArray());

    assertThat(response.status()).isEqualTo(HttpStatus.OK);
    assertThat(response.contentType()).isEqualTo(MediaType.X_PROTOBUF);
    assertThat(ExportTraceServiceResponse.parseFrom(response.content().array()))
        .isEqualTo(ExportTraceServiceResponse.getDefaultInstance());
    assertThat(storage.getTraceRequests()).containsExactly(exportRequest);
  }

  @Test
  void rejectsMalformedHttpProtobufTraceRequest() throws Exception {
    RequestsStorage storage = new RequestsStorage();

    AggregatedHttpResponse response = sendTraceRequest(storage, new byte[] {0x0a});

    assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(storage.getTraceRequests()).isEmpty();
  }

  private static AggregatedHttpResponse sendTraceRequest(RequestsStorage storage, byte[] content)
      throws Exception {
    HttpRequest request =
        HttpRequest.of(HttpMethod.POST, "/v1/traces", MediaType.X_PROTOBUF, content);
    return FakeBackendMain.traceHttpService(storage)
        .serve(ServiceRequestContext.of(request), request)
        .aggregate()
        .join();
  }
}
