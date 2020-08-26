/**
 * This module provides support for creating server spans for Jetty Handlers.
 *
 * <p>It is possible to write web application running on <a
 * href="https://www.eclipse.org/jetty/">Eclipse Jetty</a> without actually writing any servlets.
 * Instead one can use <a
 * href="https://www.eclipse.org/jetty/documentation//current/jetty-handlers.html">Jetty
 * Handlers</a>.
 *
 * <p>As instrumentation points differ between servlet instrumentations and this one, this module
 * has its own {@code JettyHandlerInstrumentation} and {@code JettyHandlerAdvice}. But this is the
 * only difference between two instrumentations, thus {@link
 * io.opentelemetry.instrumentation.auto.jetty.Jetty8HttpServerTracer} is a very thin subclass of
 * {@link io.opentelemetry.instrumentation.auto.servlet.v3.Servlet3HttpServerTracer}.
 */
package io.opentelemetry.instrumentation.auto.jetty;
