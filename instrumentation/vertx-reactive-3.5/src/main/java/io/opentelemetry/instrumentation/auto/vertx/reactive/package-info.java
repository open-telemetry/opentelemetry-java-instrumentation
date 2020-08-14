/**
 * The majority of monitoring needs of Vert.x application is covered by generic instrumentations.
 * Such as those of netty or JDBC.
 *
 * <p>{@link io.opentelemetry.instrumentation.auto.vertx.reactive.VertxRxInstrumentation} wraps
 * {code AsyncResultSingle} classes from Vert.x RxJava library to ensure proper span context
 * propagation in reactive Vert.x applications.
 */
package io.opentelemetry.instrumentation.auto.vertx.reactive;
