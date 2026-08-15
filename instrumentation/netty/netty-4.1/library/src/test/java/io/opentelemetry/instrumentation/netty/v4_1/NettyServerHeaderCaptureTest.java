/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NettyServerHeaderCaptureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void capturesEnumeratedHeadersWhenOnlyExcludingHeaders() {
    NettyServerTelemetry telemetry =
        NettyServerTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setExcluded(singletonList("host")).build())
            .setResponseHeaders(
                IncludeExclude.builder().setExcluded(singletonList("content-*")).build())
            .build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.response.header.content-type"))).isNull();
  }

  @Test
  void capturesHeadersMatchingWildcardPattern() {
    NettyServerTelemetry telemetry =
        NettyServerTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(
                IncludeExclude.builder().setIncluded(singletonList("x-test-*")).build())
            .setResponseHeaders(
                IncludeExclude.builder()
                    .setIncluded(asList("x-test-*", "content-*"))
                    .setExcluded(singletonList("content-type"))
                    .build())
            .build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.response.header.content-type"))).isNull();
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void capturesHeadersConfiguredByName() {
    NettyServerTelemetry telemetry =
        NettyServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("X-Test-Request"))
            .setCapturedResponseHeaders(singletonList("X-Test-Response"))
            .build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .isEqualTo(singletonList("test"));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void doesNotTreatConfiguredNamesAsPatterns() {
    NettyServerTelemetry telemetry =
        NettyServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response"))).isNull();
  }

  private static Attributes handleRequest(NettyServerTelemetry telemetry) {
    EmbeddedChannel channel =
        new EmbeddedChannel(
            telemetry.createCombinedHandler(),
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                FullHttpResponse response =
                    new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.EMPTY_BUFFER);
                response.headers().set("Content-Type", "text/plain");
                response.headers().set("X-Test-Response", "test");
                ctx.writeAndFlush(response);
              }
            });

    FullHttpRequest request =
        new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/test", Unpooled.EMPTY_BUFFER);
    request.headers().set("Host", "localhost");
    request.headers().set("X-Test-Request", "test");

    channel.writeInbound(request);
    channel.finishAndReleaseAll();

    return testing.waitForTraces(1).get(0).get(0).getAttributes();
  }
}
