/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datadoghq.trace.agent;

import io.opentracing.ActiveSpan;
import io.opentracing.BaseSpan;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

/** This class provides helper capabilities to the byteman rules. */
public class OpenTracingHelper extends Helper {

  private static final Logger log = Logger.getLogger(OpenTracingHelper.class.getName());

  private static Tracer tracer;

  private static final Map<Object, Span> spanAssociations =
      Collections.synchronizedMap(new WeakHashMap<Object, Span>());
  private static final Map<Object, Span> finished =
      Collections.synchronizedMap(new WeakHashMap<Object, Span>());

  private static final Map<Object, Integer> state =
      Collections.synchronizedMap(new WeakHashMap<Object, Integer>());

  private static final Object SYNC = new Object();

  public OpenTracingHelper(Rule rule) {
    super(rule);
  }

  /**
   * This method returns the OpenTracing tracer.
   *
   * @return The tracer
   */
  public Tracer getTracer() {
    if (tracer == null) {
      // Initialize on first use
      initTracer();
    }
    return tracer;
  }

  protected void initTracer() {
    synchronized (SYNC) {
      if (tracer == null) {
        if (!GlobalTracer.isRegistered()) {
          // Try to obtain a tracer using the TracerResolver
          Tracer resolved = TracerResolver.resolveTracer();
          if (resolved != null) {
            try {
              GlobalTracer.register(resolved);
            } catch (RuntimeException re) {
              log.log(Level.WARNING, "Failed to register tracer '" + resolved + "'", re);
            }
          }
        }
        // Initialize the tracer even if one has not been registered
        // (i.e. it will use a NoopTracer under the covers)
        tracer = new AgentTracer(GlobalTracer.get());
      }
    }
  }

  /**
   * This method establishes an association between an application object (i.e. the subject of the
   * instrumentation) and a span. Once the application object is no longer being used, the
   * association with the span will automatically be discarded.
   *
   * @param obj The application object to be associated with the span
   * @param span The span
   */
  public void associateSpan(Object obj, Span span) {
    spanAssociations.put(obj, span);
  }

  /**
   * This method retrieves the span associated with the supplied application object.
   *
   * @param obj The application object
   * @return The span, or null if no associated span exists
   */
  public Span retrieveSpan(Object obj) {
    return spanAssociations.get(obj);
  }

  /** ******************************************* */
  /** Needs to be replaced by span.isFinished() */
  public void finishedSpan(Object key, Span span) {
    finished.put(key, span);
  }

  public boolean isFinished(Object key) {
    return finished.containsKey(key);
  }
  /** ******************************************* */

  /**
   * This method enables an instrumentation rule to record a 'state' number against an application
   * object. This can be used in situations where rules are only applicable in certain states. For
   * example, the simple case for rules responsible for installing filters would be states
   * representing NOT_INSTALLED and INSTALLED. This means that the filter would only be installed if
   * the application object (target of the instrumentation rule) was in the NOT_INSTALLED state.
   * However other more complex scenarios may be encountered where more than two states are
   * required.
   *
   * @param obj The application object
   * @param value The state value
   */
  public void setState(Object obj, int value) {
    state.put(obj, new Integer(value));
  }

  /**
   * This method retrieves the current 'state' number associated with the supplied application
   * object.
   *
   * @param obj The application object
   * @return The state, or 0 if no state currently exists
   */
  public int getState(Object obj) {
    Integer value = state.get(obj);
    return value == null ? 0 : value.intValue();
  }

  /**
   * This method determines whether the instrumentation point, associated with the supplied object,
   * should be ignored.
   *
   * @param obj The instrumentation target
   * @return Whether the instrumentation point should be ignored
   */
  public boolean ignore(Object obj) {
    boolean ignore = false;

    if (obj instanceof HttpURLConnection) {
      String value = ((HttpURLConnection) obj).getRequestProperty("opentracing.ignore");
      ignore = value != null && value.equalsIgnoreCase("true");
    }

    // TODO: If other technologies need to use this feature,
    // then create an Adapter to wrap the specifics of each
    // technology and provide access to their properties

    if (ignore && log.isLoggable(Level.FINEST)) {
      log.finest("Ignoring request because the property [opentracing.ignore] is present.");
    }

    return ignore;
  }

  /**
   * Proxy tracer used for one purpose - to enable the rules to define a ChildOf relationship
   * without being concerned whether the supplied Span is null. If the spec (and Tracer
   * implementations) are updated to indicate a null should be ignored, then this proxy can be
   * removed.
   */
  public static class AgentTracer implements Tracer {

    private Tracer tracer;

    public AgentTracer(Tracer tracer) {
      this.tracer = tracer;
    }

    @Override
    public SpanBuilder buildSpan(String operation) {
      return new AgentSpanBuilder(tracer.buildSpan(operation));
    }

    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
      return tracer.extract(format, carrier);
    }

    @Override
    public <C> void inject(SpanContext ctx, Format<C> format, C carrier) {
      tracer.inject(ctx, format, carrier);
    }

    @Override
    public ActiveSpan activeSpan() {
      return tracer.activeSpan();
    }

    @Override
    public ActiveSpan makeActive(Span span) {
      return tracer.makeActive(span);
    }
  }

  public static class AgentSpanBuilder implements SpanBuilder {

    private SpanBuilder spanBuilder;

    public AgentSpanBuilder(SpanBuilder spanBuilder) {
      this.spanBuilder = spanBuilder;
    }

    @Override
    public SpanBuilder addReference(String type, SpanContext ctx) {
      if (ctx != null) {
        spanBuilder.addReference(type, ctx);
      }
      return this;
    }

    @Override
    public SpanBuilder asChildOf(SpanContext ctx) {
      if (ctx != null) {
        spanBuilder.asChildOf(ctx);
      }
      return this;
    }

    @Override
    public SpanBuilder asChildOf(BaseSpan<?> span) {
      if (span != null) {
        spanBuilder.asChildOf(span);
      }
      return this;
    }

    @Override
    public Span start() {
      return spanBuilder.start();
    }

    @Override
    public SpanBuilder withStartTimestamp(long ts) {
      spanBuilder.withStartTimestamp(ts);
      return this;
    }

    @Override
    public SpanBuilder withTag(String name, String value) {
      spanBuilder.withTag(name, value);
      return this;
    }

    @Override
    public SpanBuilder withTag(String name, boolean value) {
      spanBuilder.withTag(name, value);
      return this;
    }

    @Override
    public SpanBuilder withTag(String name, Number value) {
      spanBuilder.withTag(name, value);
      return this;
    }

    @Override
    public SpanBuilder ignoreActiveSpan() {
      spanBuilder.ignoreActiveSpan();
      return this;
    }

    @Override
    public ActiveSpan startActive() {
      return spanBuilder.startActive();
    }

    @Override
    public Span startManual() {
      return spanBuilder.startManual();
    }
  }
}
