/**
 * The majority of monitoring needs of Vert.x application is covered by generic instrumentations.
 * Such as those of netty or JDBC.
 *
 * <p>{@link io.opentelemetry.javaagent.instrumentation.vertx.VertxWebInstrumentationModule} wraps
 * all Vert.x route handlers in order to update the name of the currently active SERVER span with
 * the name of route. This is, arguably, a much more user-friendly name that defaults provided by
 * HTTP server instrumentations.
 */
package io.opentelemetry.javaagent.instrumentation.vertx;
