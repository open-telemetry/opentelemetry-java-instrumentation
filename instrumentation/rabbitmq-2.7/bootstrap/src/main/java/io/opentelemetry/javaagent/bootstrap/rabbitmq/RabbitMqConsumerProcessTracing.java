/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

/** Coordinates process telemetry between Spring Rabbit and RabbitMQ instrumentations. */
public final class RabbitMqConsumerProcessTracing {

  private static final ThreadLocal<Registration> currentRegistration = new ThreadLocal<>();

  public static Registration startSpringProcessTelemetry() {
    Registration registration = new Registration(currentRegistration.get());
    currentRegistration.set(registration);
    return registration;
  }

  public static boolean shouldTraceProcess() {
    return currentRegistration.get() == null;
  }

  public static final class Registration implements AutoCloseable {

    private final Registration previous;
    private boolean closed;

    private Registration(Registration previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      if (previous == null) {
        currentRegistration.remove();
      } else {
        currentRegistration.set(previous);
      }
    }
  }

  private RabbitMqConsumerProcessTracing() {}
}
