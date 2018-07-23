package util

import io.grpc.ClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.Status

import java.util.concurrent.Phaser

class BlockingListener<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
  private final Phaser phaser

  BlockingListener(ClientCall.Listener<RespT> delegate, Phaser phaser) {
    super(delegate)
    this.phaser = phaser
  }

  @Override
  void onClose(final Status status, final Metadata trailers) {
    delegate().onClose(status, trailers)
    phaser.arriveAndAwaitAdvance()
  }
}
