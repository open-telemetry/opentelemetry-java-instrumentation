package util

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor

import java.util.concurrent.Phaser

/**
 * Interceptor that blocks client from returning until server trace is reported.
 */
class BlockingInterceptor implements ClientInterceptor {
  private final Phaser phaser

  BlockingInterceptor(Phaser phaser) {
    this.phaser = phaser
    phaser.register()
  }

  @Override
  <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall(next.newCall(method, callOptions)) {
      @Override
      void start(final ClientCall.Listener responseListener, final Metadata headers) {
        super.start(new BlockingListener(responseListener, phaser), headers)
      }
    }
  }
}
