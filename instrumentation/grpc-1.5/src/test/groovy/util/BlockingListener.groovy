/*
 * Copyright 2020, OpenTelemetry Authors
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
