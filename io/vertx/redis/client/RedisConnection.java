/*
 * Copyright 2019 Red Hat, Inc.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 * <p>
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 * <p>
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.redis.client;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.*;
import io.vertx.core.streams.ReadStream;

import java.util.List;

/**
 * A simple Redis client.
 */
@VertxGen
public interface RedisConnection extends ReadStream<Response> {

  /**
   * {@inheritDoc}
   */
  @Fluent
  @Override
  RedisConnection exceptionHandler(Handler<Throwable> handler);

  /**
   * {@inheritDoc}
   */
  @Fluent
  @Override
  RedisConnection handler(Handler<Response> handler);

  /**
   * {@inheritDoc}
   */
  @Fluent
  @Override
  RedisConnection pause();

  /**
   * {@inheritDoc}
   */
  @Fluent
  @Override
  RedisConnection resume();

  /**
   * {@inheritDoc}
   */
  @Fluent
  @Override
  RedisConnection fetch(long amount);

  /**
   * {@inheritDoc}
   */
  @Fluent
  @Override
  RedisConnection endHandler(@Nullable Handler<Void> endHandler);


  /**
   * Send the given command to the redis server or cluster.
   * @param command the command to send
   * @param onSend the asynchronous result handler.
   * @return fluent self.
   */
  @Fluent
  RedisConnection send(Request command, Handler<AsyncResult<@Nullable Response>> onSend);

  /**
   * Sends a list of commands in a single IO operation, this prevents any inter twinning to happen from other
   * client users.
   *
   * @param commands list of command to send
   * @param onSend the asynchronous result handler.
   * @return fluent self.
   */
  @Fluent
  RedisConnection batch(List<Request> commands, Handler<AsyncResult<List<@Nullable Response>>> onSend);

  /**
   * Closes the connection or returns to the pool.
   */
  void close();

  /**
   * Flag to notify if the pending message queue (commands in transit) is full.
   *
   * When the pending message queue is full and a new send command is issued it will result in a exception to be thrown.
   * Checking this flag before sending can allow the application to wait before sending the next message.
   *
   * @return true is queue is full.
   */
  boolean pendingQueueFull();
}
