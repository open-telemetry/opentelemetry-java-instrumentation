/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.fakebackend;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.protobuf.InvalidProtocolBufferException;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.server.healthcheck.HealthCheckService;
import io.netty.buffer.ByteBufOutputStream;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import java.io.IOException;
import java.io.OutputStream;
import org.curioswitch.common.protobuf.json.MessageMarshaller;

public class FakeBackendMain {

  private static final JsonMapper OBJECT_MAPPER;

  static {
    var marshaller =
        MessageMarshaller.builder()
            .register(ExportTraceServiceRequest.getDefaultInstance())
            .register(ExportMetricsServiceRequest.getDefaultInstance())
            .register(ExportLogsServiceRequest.getDefaultInstance())
            .build();

    var mapper = JsonMapper.builder();
    var module = new SimpleModule();
    var serializers = new SimpleSerializers();
    serializers.addSerializer(
        new StdSerializer<>(ExportTraceServiceRequest.class) {
          @Override
          public void serialize(
              ExportTraceServiceRequest value, JsonGenerator gen, SerializerProvider provider)
              throws IOException {
            marshaller.writeValue(value, gen);
          }
        });
    serializers.addSerializer(
        new StdSerializer<>(ExportMetricsServiceRequest.class) {
          @Override
          public void serialize(
              ExportMetricsServiceRequest value, JsonGenerator gen, SerializerProvider provider)
              throws IOException {
            marshaller.writeValue(value, gen);
          }
        });
    serializers.addSerializer(
        new StdSerializer<>(ExportLogsServiceRequest.class) {
          @Override
          public void serialize(
              ExportLogsServiceRequest value, JsonGenerator gen, SerializerProvider provider)
              throws IOException {
            marshaller.writeValue(value, gen);
          }
        });
    module.setSerializers(serializers);
    mapper.addModule(module);
    OBJECT_MAPPER = mapper.build();
  }

  public static void main(String[] args) {
    RequestsStorage storage = new RequestsStorage();
    var grpcTraceService = new FakeTraceCollectorServiceGrpc(storage);
    var grpcMetricsService = new FakeMetricsCollectorServiceGrpc(storage);
    var grpcLogsService = new FakeLogsCollectorServiceGrpc(storage);
    var httpTraceService = new FakeTraceCollectorServiceHttp(storage);
    var server =
        Server.builder()
            .http(8080)
            .service("/v1/traces", httpTraceService)
            .service(
                "/v1/metrics",
                (ctx, req) -> {
                  return HttpResponse.of(HttpStatus.OK);
                })
            .service(
                "/v1/logs",
                (ctx, req) -> {
                  return HttpResponse.of(HttpStatus.OK);
                })
            .service(
                GrpcService.builder()
                    .addService(grpcTraceService)
                    .addService(grpcMetricsService)
                    .addService(grpcLogsService)
                    .build())
            .service(
                "/clear",
                (ctx, req) -> {
                  grpcTraceService.clearRequests();
                  grpcMetricsService.clearRequests();
                  grpcLogsService.clearRequests();
                  return HttpResponse.of(HttpStatus.OK);
                })
            .service(
                "/get-traces",
                (ctx, req) -> {
                  var requests = grpcTraceService.getRequests();
                  var buf = new ByteBufOutputStream(ctx.alloc().buffer());
                  OBJECT_MAPPER.writeValue((OutputStream) buf, requests);
                  return HttpResponse.of(
                      HttpStatus.OK, MediaType.JSON, HttpData.wrap(buf.buffer()));
                })
            .service(
                "/get-metrics",
                (ctx, req) -> {
                  var requests = grpcMetricsService.getRequests();
                  var buf = new ByteBufOutputStream(ctx.alloc().buffer());
                  OBJECT_MAPPER.writeValue((OutputStream) buf, requests);
                  return HttpResponse.of(
                      HttpStatus.OK, MediaType.JSON, HttpData.wrap(buf.buffer()));
                })
            .service(
                "/get-logs",
                (ctx, req) -> {
                  var requests = grpcLogsService.getRequests();
                  var buf = new ByteBufOutputStream(ctx.alloc().buffer());
                  OBJECT_MAPPER.writeValue((OutputStream) buf, requests);
                  return HttpResponse.of(
                      HttpStatus.OK, MediaType.JSON, HttpData.wrap(buf.buffer()));
                })
            .service("/health", HealthCheckService.of())
            .build();

    server.start().join();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop().join()));
  }


  private FakeBackendMain() {}
}
