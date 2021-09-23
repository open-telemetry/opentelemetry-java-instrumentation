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
 * only difference between two instrumentations, thus jetty instrumentation largely reuses servlet
 * instrumentation.
 */
package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;
