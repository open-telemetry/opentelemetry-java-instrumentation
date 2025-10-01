/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.universal;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Universal instrumentation module for Vertx context persistence across all components.
 *
 * <p>This module provides comprehensive context propagation for: - Server context persistence
 * (RESTEasy, Vertx Web, HTTP Server, Context Management) - Client context persistence (SQL, Redis,
 * Web Client, Cassandra)
 *
 * <p>Uses a tuple-based configuration system to target specific methods that accept Handler
 * parameters, wrapping them with UniversalContextPreservingHandler.
 *
 * <p>Muzzle configuration is handled in build.gradle.kts for version compatibility.
 */
@AutoService(InstrumentationModule.class)
public class VertxUniversalContextPersistenceInstrumentationModule extends InstrumentationModule {

  /**
   * Complete configuration of all instrumentation targets based on comprehensive source code
   * analysis. Updated with verified method signatures, class types, and private method handling.
   */
  private static final List<InstrumentationTarget> TARGETS =
      Arrays.asList(
          // === HTTP REQUEST ENTRY POINT (CRITICAL) ===
          // Http1xServerConnection.handleMessage() - Generate request ID when HTTP request arrives
//          new InstrumentationTarget(
//              "io.vertx.core.http.impl", "Http1xServerConnection", "handleMessage", 1, -1, ClassType.CONCRETE, false),
          
          // HttpServerRequest.pause() - Inject custom header when request is paused
          new InstrumentationTarget(
              "io.vertx.core.http", "HttpServerRequest", "pause", 0, -1, ClassType.INTERFACE, false),
          
          // === VERTX CONTEXT EXECUTION (EVENT LOOP SWITCHING) ===
          // ContextImpl.runOnContext() - Handler wrapping for context switching
          new InstrumentationTarget(
              "io.vertx.core.impl", "ContextImpl", "runOnContext", 1, 0, ClassType.CONCRETE, false),
          
          // ContextImpl.executeBlocking() - Handler wrapping for blocking operations (5-arg private method)
          new InstrumentationTarget(
              "io.vertx.core.impl", "ContextImpl", "executeBlocking", 5, 1, ClassType.CONCRETE, false),
          
          // EventLoopContext.execute() - Handler wrapping for immediate execution
          new InstrumentationTarget(
              "io.vertx.core.impl", "EventLoopContext", "execute", 2, 1, ClassType.CONCRETE, false),
          
          // WorkerContext.wrapTask() - Handler wrapping for worker thread tasks
          new InstrumentationTarget(
              "io.vertx.core.impl", "WorkerContext", "wrapTask", 3, 1, ClassType.CONCRETE, false),
          
          // === THREAD POOL EXECUTION (THREAD SWITCHING) ===
          // ThreadPoolExecutor.execute() - Runnable wrapping for thread pool execution
          new InstrumentationTarget(
              "java.util.concurrent", "ThreadPoolExecutor", "execute", 1, 0, ClassType.CONCRETE, false),
          
          // === HTTP REQUEST ROUTING (YOUR CODE PATHS) ===
          // Router.handle(HttpServerRequest) - Main routing entry point
          new InstrumentationTarget(
              "io.vertx.ext.web.impl", "RouterImpl", "handle", 1, 0, ClassType.CONCRETE, false),
          
          // VertxRequestHandler.handle(HttpServerRequest) - RESTEasy entry point  
          new InstrumentationTarget(
              "org.jboss.resteasy.plugins.server.vertx", "VertxRequestHandler", "handle", 1, 0, ClassType.CONCRETE, false),
          
          // Route.handler(Handler<RoutingContext>) - Individual route handlers
          new InstrumentationTarget(
              "io.vertx.ext.web.impl", "RouteImpl", "handler", 1, 0, ClassType.CONCRETE, false),
          
          // AbstractRoute.handle(RoutingContext) - Route execution entry point (concrete method)
          new InstrumentationTarget(
              "com.dream11.rest", "AbstractRoute", "handle", 1, 0, ClassType.ABSTRACT, false),
          
          // === CLIENT OPERATIONS (EXISTING) ===
          // SQL Client - SqlClientBase.schedule() method
          new InstrumentationTarget(
              "io.vertx.sqlclient.impl", "SqlClientBase", "schedule", 2, 1, ClassType.CONCRETE, false),
          
          // Redis Client - RedisConnection.send() method
          new InstrumentationTarget(
              "io.vertx.redis.client.impl", "RedisConnectionImpl", "send", 2, 1, ClassType.CONCRETE, false),
          
          // Web Client - HttpRequestImpl.send() method (3-arg PRIVATE method)
          new InstrumentationTarget(
              "io.vertx.ext.web.client.impl", "HttpRequestImpl", "send", 3, 2, ClassType.CONCRETE, true),
          
          // Cassandra Client - Util.handleOnContext() method
          new InstrumentationTarget(
              "io.vertx.cassandra.impl", "Util", "handleOnContext", 4, 3, ClassType.CONCRETE, false),
          
          // Cassandra Client - CassandraClientImpl.getSession() method
          new InstrumentationTarget(
              "io.vertx.cassandra.impl", "CassandraClientImpl", "getSession", 2, 1, ClassType.CONCRETE, false),
//Previously added handlers
          // === SERVER CONTEXT PERSISTENCE (CRITICAL FOR CONTEXT BRIDGING) ===
          // REMOVED: RouteImpl.handleContext - method doesn't exist
          // REMOVED: HttpServerRequestImpl.setHandler - method doesn't exist
          // REMOVED: VertxRequestHandler.handle - wrong signature (takes HttpServerRequest, not
          // Handler)

          // Vertx Web Framework (Priority 1 - Underlying Web Framework)
          new InstrumentationTarget(
              "io.vertx.ext.web", "Route", "handler", 1, 0, ClassType.INTERFACE, false),

          // Context Management (Priority 4 - Event Loop Safety Net)
          new InstrumentationTarget(
              "io.vertx.core.impl", "ContextImpl", "executeTask", 1, 0, ClassType.CONCRETE, false),

//          // === CLIENT CONTEXT PERSISTENCE (IO OPERATIONS) ===
//          // SQL Client (Universal Scheduler - 12x efficiency improvement!)
//          new InstrumentationTarget(
//              "io.vertx.sqlclient.impl", "SqlClientBase", "schedule", 2, 1, ClassType.ABSTRACT),

          // Redis Client (Interface + Connection)
          new InstrumentationTarget(
              "io.vertx.redis.client", "RedisConnection", "send", 2, 1, ClassType.INTERFACE, false),
          new InstrumentationTarget(
              "io.vertx.redis.client", "Redis", "connect", 1, 0, ClassType.INTERFACE, false),

////           Web Client (Perfect Convergence)
//          new InstrumentationTarget(
//              "io.vertx.ext.web.client.impl", "HttpRequestImpl", "send", 3, 2, ClassType.CONCRETE),

          // Cassandra Client (Universal Utility)
          new InstrumentationTarget(
              "io.vertx.cassandra.impl", "Util", "handleOnContext", 4, 3, ClassType.CONCRETE, false),
          new InstrumentationTarget(
              "io.vertx.cassandra.impl", "CassandraClientImpl", "getSession", 2, 1, ClassType.CONCRETE, false));

  public VertxUniversalContextPersistenceInstrumentationModule() {
    super("vertx-universal-context-persistence", "vertx-universal-context-persistence-3.9");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Only apply to class loaders that have Vertx core classes
    return AgentElementMatchers.hasClassesNamed("io.vertx.core.Handler");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    List<TypeInstrumentation> instrumentations = new ArrayList<>();

    // Add the context storage instrumentation (like SQL did)
    instrumentations.add(new VertxContextStorageInstrumentation());

    // Add all the handler wrapping instrumentations
//    System.out.println(
//        "UNIVERSAL-MODULE: Adding " + TARGETS.size() + " universal handler instrumentations");
    instrumentations.addAll(
        TARGETS.stream().map(UniversalHandlerInstrumentation::new).collect(Collectors.toList()));

    return instrumentations;
  }
}
