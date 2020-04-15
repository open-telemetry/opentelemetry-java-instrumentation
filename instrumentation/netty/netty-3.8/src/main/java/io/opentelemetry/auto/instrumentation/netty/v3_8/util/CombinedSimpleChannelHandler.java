/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.netty.v3_8.util;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChildChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.WriteCompletionEvent;

public class CombinedSimpleChannelHandler<
        Upstream extends SimpleChannelUpstreamHandler,
        Downstream extends SimpleChannelDownstreamHandler>
    extends SimpleChannelHandler {

  private final Upstream upstream;
  private final Downstream downstream;

  public CombinedSimpleChannelHandler(final Upstream upstream, final Downstream downstream) {
    this.upstream = upstream;
    this.downstream = downstream;
  }

  @Override
  public void handleUpstream(final ChannelHandlerContext ctx, final ChannelEvent e)
      throws Exception {
    upstream.handleUpstream(ctx, e);
  }

  @Override
  public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e)
      throws Exception {
    upstream.messageReceived(ctx, e);
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e)
      throws Exception {
    upstream.exceptionCaught(ctx, e);
  }

  @Override
  public void channelOpen(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    upstream.channelOpen(ctx, e);
  }

  @Override
  public void channelBound(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    upstream.channelBound(ctx, e);
  }

  @Override
  public void channelConnected(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    upstream.channelConnected(ctx, e);
  }

  @Override
  public void channelInterestChanged(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    upstream.channelInterestChanged(ctx, e);
  }

  @Override
  public void channelDisconnected(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    upstream.channelDisconnected(ctx, e);
  }

  @Override
  public void channelUnbound(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    upstream.channelUnbound(ctx, e);
  }

  @Override
  public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    upstream.channelClosed(ctx, e);
  }

  @Override
  public void writeComplete(final ChannelHandlerContext ctx, final WriteCompletionEvent e)
      throws Exception {
    upstream.writeComplete(ctx, e);
  }

  @Override
  public void childChannelOpen(final ChannelHandlerContext ctx, final ChildChannelStateEvent e)
      throws Exception {
    upstream.childChannelOpen(ctx, e);
  }

  @Override
  public void childChannelClosed(final ChannelHandlerContext ctx, final ChildChannelStateEvent e)
      throws Exception {
    upstream.childChannelClosed(ctx, e);
  }

  @Override
  public void handleDownstream(final ChannelHandlerContext ctx, final ChannelEvent e)
      throws Exception {
    downstream.handleDownstream(ctx, e);
  }

  @Override
  public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent e)
      throws Exception {
    downstream.writeRequested(ctx, e);
  }

  @Override
  public void bindRequested(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    downstream.bindRequested(ctx, e);
  }

  @Override
  public void connectRequested(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    downstream.connectRequested(ctx, e);
  }

  @Override
  public void setInterestOpsRequested(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    downstream.setInterestOpsRequested(ctx, e);
  }

  @Override
  public void disconnectRequested(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    downstream.disconnectRequested(ctx, e);
  }

  @Override
  public void unbindRequested(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    downstream.unbindRequested(ctx, e);
  }

  @Override
  public void closeRequested(final ChannelHandlerContext ctx, final ChannelStateEvent e)
      throws Exception {
    downstream.closeRequested(ctx, e);
  }
}
