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
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
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

    AggregatedHttpResponse response =
        sendRequest(
            new FakeTraceCollectorServiceHttp(storage), "/v1/traces", exportRequest.toByteArray());

    assertThat(response.status()).isEqualTo(HttpStatus.OK);
    assertThat(response.contentType()).isEqualTo(MediaType.X_PROTOBUF);
    assertThat(ExportTraceServiceResponse.parseFrom(response.content().array()))
        .isEqualTo(ExportTraceServiceResponse.getDefaultInstance());
    assertThat(storage.getTraceRequests()).containsExactly(exportRequest);
  }

  @Test
  void storesHttpProtobufMetricsRequest() throws Exception {
    RequestsStorage storage = new RequestsStorage();
    ExportMetricsServiceRequest exportRequest =
        ExportMetricsServiceRequest.newBuilder()
            .addResourceMetrics(ResourceMetrics.getDefaultInstance())
            .build();

    AggregatedHttpResponse response =
        sendRequest(
            new FakeMetricsCollectorServiceHttp(storage),
            "/v1/metrics",
            exportRequest.toByteArray());

    assertThat(response.status()).isEqualTo(HttpStatus.OK);
    assertThat(response.contentType()).isEqualTo(MediaType.X_PROTOBUF);
    assertThat(ExportMetricsServiceResponse.parseFrom(response.content().array()))
        .isEqualTo(ExportMetricsServiceResponse.getDefaultInstance());
    assertThat(storage.getMetricsRequests()).containsExactly(exportRequest);
  }

  @Test
  void storesHttpProtobufLogsRequest() throws Exception {
    RequestsStorage storage = new RequestsStorage();
    ExportLogsServiceRequest exportRequest =
        ExportLogsServiceRequest.newBuilder()
            .addResourceLogs(ResourceLogs.getDefaultInstance())
            .build();

    AggregatedHttpResponse response =
        sendRequest(
            new FakeLogsCollectorServiceHttp(storage), "/v1/logs", exportRequest.toByteArray());

    assertThat(response.status()).isEqualTo(HttpStatus.OK);
    assertThat(response.contentType()).isEqualTo(MediaType.X_PROTOBUF);
    assertThat(ExportLogsServiceResponse.parseFrom(response.content().array()))
        .isEqualTo(ExportLogsServiceResponse.getDefaultInstance());
    assertThat(storage.getLogsRequests()).containsExactly(exportRequest);
  }

  @Test
  void rejectsMalformedHttpProtobufTraceRequest() throws Exception {
    RequestsStorage storage = new RequestsStorage();

    AggregatedHttpResponse response =
        sendRequest(new FakeTraceCollectorServiceHttp(storage), "/v1/traces", new byte[] {0x0a});

    assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(storage.getTraceRequests()).isEmpty();
  }

  @Test
  void rejectsMalformedHttpProtobufMetricsRequest() throws Exception {
    RequestsStorage storage = new RequestsStorage();

    AggregatedHttpResponse response =
        sendRequest(new FakeMetricsCollectorServiceHttp(storage), "/v1/metrics", new byte[] {0x0a});

    assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(storage.getMetricsRequests()).isEmpty();
  }

  @Test
  void rejectsMalformedHttpProtobufLogsRequest() throws Exception {
    RequestsStorage storage = new RequestsStorage();

    AggregatedHttpResponse response =
        sendRequest(new FakeLogsCollectorServiceHttp(storage), "/v1/logs", new byte[] {0x0a});

    assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(storage.getLogsRequests()).isEmpty();
  }

  private static AggregatedHttpResponse sendRequest(
      HttpService service, String path, byte[] content) throws Exception {
    HttpRequest request = HttpRequest.of(HttpMethod.POST, path, MediaType.X_PROTOBUF, content);
    return service.serve(ServiceRequestContext.of(request), request).aggregate().join();
  }
}
