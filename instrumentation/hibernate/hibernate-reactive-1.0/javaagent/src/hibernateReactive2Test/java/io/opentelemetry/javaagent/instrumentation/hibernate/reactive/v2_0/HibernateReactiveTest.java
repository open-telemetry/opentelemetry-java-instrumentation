/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v2_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

class HibernateReactiveTest extends AbstractHibernateReactiveTest {

  @Override
  protected EntityManagerFactory createEntityManagerFactory() throws Exception {
    return vertx
        .getOrCreateContext()
        .<EntityManagerFactory>executeBlocking(
            promise -> promise.complete(Persistence.createEntityManagerFactory("test-pu")))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);
  }
}
