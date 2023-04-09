/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientRequestTracingHandler.HTTP_CLIENT_REQUEST;

import com.twitter.finagle.context.Contexts;
import com.twitter.finagle.context.LocalContext;
import com.twitter.util.Future;
import com.twitter.util.Local;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelInitializerDelegate;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContext;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyHttpServerResponseBeforeCommitHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyServerSingletons;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import java.lang.invoke.MethodHandle;
import java.util.Deque;
import java.util.concurrent.Callable;
import scala.Function0;
import scala.Option;

public class Helpers {

  private Helpers() {}

  public static final LocalCallGuard LOOP_GUARD = new LocalCallGuard();

  public static final LocalCallGuard WRITE_GUARD = new LocalCallGuard();

  // used for finagle's LocalContext: carries a reference to the Context observed at the start
  // of the server request processing or the client Service call
  static class ContextRef {

    private final LocalContext.Key<Context> key;
    public static final ContextRef INSTANCE = new ContextRef(Contexts.local().newKey());

    private ContextRef(LocalContext.Key<Context> var1) {
      this.key = var1;
    }

    public LocalContext.Key<Context> getKey() {
      return this.key;
    }
  }

  // uses twitter util's Local bc the finagle/twitter stack is essentially incompatible with
  // java-native TLVs;
  // as these are referenced across twitter Future compositions, no assumptions are made and they
  // are used to adhere strictly to the interface, allowing the guard test to succeed at execution
  // time
  public static class LocalCallGuard {

    private final Local<Object> guard = new Local<>();

    // is the current thread presently inside a call guarded by this LocalCallGuard?
    public boolean isRecursed() {
      return guard.apply().isDefined();
    }

    // safely guard the Function0 call;
    // (Function0 -> Java Supplier, in our cases -- it wraps existing calls)
    public <T> T guardedCall(Function0<T> f, T defaultVal) {
      if (isRecursed()) {
        return defaultVal;
      }
      return guard.let(null, f);
    }
  }

  @SuppressWarnings({"ThrowSpecificExceptions", "CheckedExceptionNotThrown"})
  public static Future<?> loopAdviceExit(MethodHandle handle, Future<?> ret) {
    return LOOP_GUARD.guardedCall(
        () ->
            Contexts.local()
                .let(
                    ContextRef.INSTANCE.getKey(),
                    // this works bc at this point in the server evaluation, the netty
                    // instrumentation has already gone to work and assigned the context to the
                    // local thread;
                    //
                    // this works specifically in finagle's netty stack bc at this point the loop()
                    // method is running on a netty thread with the necessary access to the
                    // java-native ThreadLocal where the Context is stored
                    Context.current(),
                    () -> {
                      try {
                        // all access to Context.current() from this point forward should now
                        // succeed as expected
                        return (Future<?>) handle.invoke();
                      } catch (Throwable e) {
                        throw new RuntimeException(e);
                      }
                    }),
        ret);
  }

  @SuppressWarnings("ThrowSpecificExceptions")
  public static Future<?> callWrite(MethodHandle handle, Future<?> ret) {
    return WRITE_GUARD.guardedCall(
        () -> {
          Option<Context> ref = Contexts.local().get(ContextRef.INSTANCE.getKey());
          Callable<Future<?>> call =
              () -> {
                try {
                  return (Future<?>) handle.invoke();
                } catch (Exception e) {
                  // don't wrap needlessly
                  throw e;
                } catch (Throwable e) {
                  throw new RuntimeException(e);
                }
              };
          if (ref.isDefined()) {
            // wrap the call if ContextRef contains a set Context
            call = AgentContextStorage.toApplicationContext(ref.get()).wrap(call);
          }
          try {
            return call.call();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        },
        ret);
  }

  public static <C extends Channel> ChannelInitializer<C> wrapServer(ChannelInitializer<C> inner) {
    return new ChannelInitializerDelegate<C>(inner) {

      @Override
      protected void initChannel(C channel) throws Exception {
        // do all the needful up front, as this may add necessary handlers -- see below
        super.initChannel(channel);

        // the parent channel is the original http/1.1 channel and has the contexts stored in it;
        // we assign to this new channel as the old one will not be evaluated in the upgraded h2c
        // chain
        Deque<ServerContext> serverContexts =
            channel.parent().attr(AttributeKeys.SERVER_CONTEXT).get();
        channel.attr(AttributeKeys.SERVER_CONTEXT).set(serverContexts);

        // todo add way to propagate the protocol version override up to the netty instrumentation;
        //  why: the netty instrumentation extracts the http protocol version from the HttpRequest
        //  object which in this case is _always_ http/1.1 due to the use of this adapter codec,
        //  Http2StreamFrameToHttpObjectCodec
        ChannelHandlerContext codecCtx =
            channel.pipeline().context(Http2StreamFrameToHttpObjectCodec.class);
        if (codecCtx != null) {
          if (channel.pipeline().get(HttpServerTracingHandler.class) == null) {
            VirtualField<ChannelHandler, ChannelHandler> virtualField =
                VirtualField.find(ChannelHandler.class, ChannelHandler.class);
            HttpServerTracingHandler ourHandler =
                new HttpServerTracingHandler(
                    NettyServerSingletons.instrumenter(),
                    NettyHttpServerResponseBeforeCommitHandler.INSTANCE);

            channel
                .pipeline()
                .addAfter(codecCtx.name(), ourHandler.getClass().getName(), ourHandler);
            // attach this in this way to match up with how netty instrumentation expects things
            virtualField.set(codecCtx.handler(), ourHandler);
          }
        }
      }
    };
  }

  public static <C extends Channel> ChannelInitializer<C> wrapClient(ChannelInitializer<C> inner) {
    return new ChannelInitializerDelegate<C>(inner) {

      // wraps everything for roughly the same reasons as in wrapServer(), above
      @Override
      protected void initChannel(C channel) throws Exception {
        super.initChannel(channel);

        channel
            .attr(AttributeKeys.CLIENT_PARENT_CONTEXT)
            .set(channel.parent().attr(AttributeKeys.CLIENT_PARENT_CONTEXT).get());
        channel
            .attr(AttributeKeys.CLIENT_CONTEXT)
            .set(channel.parent().attr(AttributeKeys.CLIENT_CONTEXT).get());
        channel.attr(HTTP_CLIENT_REQUEST).set(channel.parent().attr(HTTP_CLIENT_REQUEST).get());

        // todo add way to propagate the protocol version override up to the netty instrumentation;
        //  why: the netty instrumentation extracts the http protocol version from the HttpRequest
        //  object which in this case is _always_ http/1.1 due to the use of this adapter codec,
        //  Http2StreamFrameToHttpObjectCodec
        ChannelHandlerContext codecCtx =
            channel.pipeline().context(Http2StreamFrameToHttpObjectCodec.class);
        if (codecCtx != null) {
          if (channel.pipeline().get(HttpClientTracingHandler.class) == null) {
            VirtualField<ChannelHandler, ChannelHandler> virtualField =
                VirtualField.find(ChannelHandler.class, ChannelHandler.class);
            HttpClientTracingHandler ourHandler =
                new HttpClientTracingHandler(NettyServerSingletons.instrumenter());

            channel
                .pipeline()
                .addAfter(codecCtx.name(), ourHandler.getClass().getName(), ourHandler);
            // attach this in this way to match up with how netty instrumentation expects things
            virtualField.set(codecCtx.handler(), ourHandler);
          }
        }
      }
    };
  }
}
