/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
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
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NettyServerHeaderSelectorTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void capturesEveryHeaderNotExcluded() {
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

  @Test
  void capturesHeadersConfiguredByName() {
    NettyServerTelemetry telemetry =
        NettyServerTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setIncluded("X-Test-Request").build())
            .setResponseHeaders(IncludeExclude.builder().setIncluded("X-Test-Response").build())
            .build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request")))
        .isEqualTo(singletonList("test"));
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response")))
        .isEqualTo(singletonList("test"));
  }

  @Test
  void doesNotCaptureHeadersByDefault() {
    NettyServerTelemetry telemetry =
        NettyServerTelemetry.builder(testing.getOpenTelemetry()).build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringArrayKey("http.request.header.x-test-request"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.request.header.authorization"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.request.header.host"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.x-test-response"))).isNull();
  }

  @Test
  void acceptsBroadAttributesExtractor() {
    NettyServerTelemetry telemetry =
        NettyServerTelemetry.builder(testing.getOpenTelemetry())
            .addAttributesExtractor(
                new AttributesExtractor<Object, Object>() {
                  @Override
                  public void onStart(
                      AttributesBuilder attributes, Context parentContext, Object request) {
                    assertThat(request).isInstanceOf(NettyRequest.class);
                    assertThat(attributes.build().get(stringKey("http.request.method")))
                        .isEqualTo("GET");
                    NettyRequest nettyRequest = (NettyRequest) request;
                    assertThat(nettyRequest.getChannel()).isNotNull();
                    attributes.put(
                        stringKey("test.request.method"),
                        nettyRequest.getRequest().method().name());
                  }

                  @Override
                  public void onEnd(
                      AttributesBuilder attributes,
                      Context context,
                      Object request,
                      @Nullable Object response,
                      @Nullable Throwable error) {
                    assertThat(response).isInstanceOf(HttpResponse.class);
                    attributes.put(stringKey("test.response"), "extracted");
                  }
                })
            .build();

    Attributes attributes = handleRequest(telemetry);

    assertThat(attributes.get(stringKey("http.request.method"))).isEqualTo("GET");
    assertThat(attributes.get(stringKey("test.request.method"))).isEqualTo("GET");
    assertThat(attributes.get(stringKey("test.response"))).isEqualTo("extracted");
  }

  private static Attributes handleRequest(NettyServerTelemetry telemetry) {
    EmbeddedChannel channel =
        new EmbeddedChannel(
            telemetry.createCombinedHandler(),
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                try {
                  FullHttpResponse response =
                      new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.EMPTY_BUFFER);
                  response.headers().set("Content-Type", "text/plain");
                  response.headers().set("X-Test-Response", "test");
                  ctx.writeAndFlush(response);
                } finally {
                  ReferenceCountUtil.release(msg);
                }
              }
            });

    FullHttpRequest request =
        new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/test", Unpooled.EMPTY_BUFFER);
    request.headers().set("Host", "localhost");
    request.headers().set("Authorization", "Bearer secret");
    request.headers().set("X-Test-Request", "test");

    channel.writeInbound(request);
    channel.finishAndReleaseAll();

    return testing.waitForTraces(1).get(0).get(0).getAttributes();
  }
}
