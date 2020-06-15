/**
 * The majority of monitoring needs of Vert.x application is covered by generic instrumentations.
 * Such as those of netty or JDBC.
 *
 * <p>{@link io.opentelemetry.auto.instrumentation.vertx.RouteInstrumentation} wraps all Vert.x
 * route handlers in order to update the name of the currently active SERVER span with the name of
 * route. This is, arguably, a much more user-friendly name that defaults provided by HTTP server
 * instrumentations.
 *
 * <p>{@link io.opentelemetry.auto.instrumentation.vertx.reactive.VertxRxInstrumentation} wraps
 * {code AsyncResultSingle} classes from Vert.x RxJava library to ensure proper span context
 * propagation in reactive Vert.x applications.
 */
package io.opentelemetry.auto.instrumentation.vertx;
