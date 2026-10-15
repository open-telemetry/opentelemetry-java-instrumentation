/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import org.apache.thrift.AsyncProcessFunction;
import org.apache.thrift.AsyncProcessorUtil;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AsyncMethodCallbackUtil {

  public static <T> AsyncMethodCallback<T> wrap(
      AsyncMethodCallback<T> callback, ClientCallContext clientCallContext) {
    Context context = Context.current();
    return new AsyncMethodCallback<T>() {

      @Override
      public void onComplete(T response) {
        clientCallContext.endSpan(null);
        try (Scope ignore = context.makeCurrent()) {
          callback.onComplete(response);
        }
      }

      @Override
      public void onError(Exception exception) {
        clientCallContext.endSpan(exception);
        try (Scope ignore = context.makeCurrent()) {
          callback.onError(exception);
        }
      }
    };
  }

  public static <T> AsyncMethodCallback<T> wrap(
      AsyncMethodCallback<T> callback,
      ServerInProtocolDecorator serverInProtocolDecorator,
      ServerOutProtocolDecorator serverOutProtocolDecorator) {
    Context context = Context.current();
    return new AsyncMethodCallback<T>() {

      @Override
      public void onComplete(T response) {
        Throwable error = null;
        try (Scope ignore = context.makeCurrent()) {
          callback.onComplete(response);
        } catch (Throwable e) {
          error = e;
        }
        serverInProtocolDecorator.endSpan(error, serverOutProtocolDecorator.hasException());
      }

      @Override
      public void onError(Exception exception) {
        try (Scope ignore = context.makeCurrent()) {
          callback.onError(exception);
        } finally {
          serverInProtocolDecorator.endSpan(exception, serverOutProtocolDecorator.hasException());
        }
      }
    };
  }

  public static <I, T extends TBase<?, ?>, R, A extends TBase<?, ?>>
      AsyncProcessFunction<I, T, R, A> wrap(AsyncProcessFunction<I, T, R, A> function) {
    return new AsyncProcessFunction<I, T, R, A>(function.getMethodName()) {

      @Override
      public boolean isOneway() {
        return AsyncProcessorUtil.isOneWay(function);
      }

      @Override
      public void start(I iface, T args, AsyncMethodCallback<R> resultHandler) throws TException {
        function.start(iface, args, resultHandler);
      }

      @Override
      public T getEmptyArgsInstance() {
        return function.getEmptyArgsInstance();
      }

      @NoMuzzle
      @Override
      public A getEmptyResultInstance() {
        return function.getEmptyResultInstance();
      }

      @Override
      public AsyncMethodCallback<R> getResultHandler(
          AbstractNonblockingServer.AsyncFrameBuffer fb, int seqid) {
        AsyncMethodCallback<R> callback = function.getResultHandler(fb, seqid);
        if (!(fb.getInputProtocol() instanceof ServerInProtocolDecorator)
            || !(fb.getOutputProtocol() instanceof ServerOutProtocolDecorator)) {
          return callback;
        }

        ServerInProtocolDecorator serverInProtocolDecorator =
            (ServerInProtocolDecorator) fb.getInputProtocol();
        ServerOutProtocolDecorator serverOutProtocolDecorator =
            (ServerOutProtocolDecorator) fb.getOutputProtocol();
        return AsyncMethodCallbackUtil.wrap(
            callback, serverInProtocolDecorator, serverOutProtocolDecorator);
      }
    };
  }

  private AsyncMethodCallbackUtil() {}
}
