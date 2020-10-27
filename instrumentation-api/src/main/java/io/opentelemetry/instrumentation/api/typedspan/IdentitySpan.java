/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import static io.opentelemetry.trace.attributes.SemanticAttributes.*;

import io.opentelemetry.context.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class IdentitySpan extends DelegatingSpan implements IdentitySemanticConvention {

  protected IdentitySpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link IdentitySpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link IdentitySpan} object.
   */
  public static IdentitySpanBuilder createIdentitySpan(Tracer tracer, String spanName) {
    return new IdentitySpanBuilder(tracer, spanName);
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  public void end() {
    delegate.end();
  }

  /**
   * Sets enduser.id.
   *
   * @param enduserId Username or client_id extracted from the access token or Authorization header
   *     in the inbound request from outside the system.
   */
  @Override
  public IdentitySemanticConvention setEnduserId(String enduserId) {
    delegate.setAttribute(ENDUSER_ID, enduserId);
    return this;
  }

  /**
   * Sets enduser.role.
   *
   * @param enduserRole Actual/assumed role the client is making the request under extracted from
   *     token or application security context.
   */
  @Override
  public IdentitySemanticConvention setEnduserRole(String enduserRole) {
    delegate.setAttribute(ENDUSER_ROLE, enduserRole);
    return this;
  }

  /**
   * Sets enduser.scope.
   *
   * @param enduserScope Scopes or granted authorities the client currently possesses extracted from
   *     token or application security context. The value would come from the scope associated with
   *     an OAuth 2.0 Access Token or an attribute value in a SAML 2.0 Assertion.
   */
  @Override
  public IdentitySemanticConvention setEnduserScope(String enduserScope) {
    delegate.setAttribute(ENDUSER_SCOPE, enduserScope);
    return this;
  }

  /** Builder class for {@link IdentitySpan}. */
  public static class IdentitySpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected IdentitySpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public IdentitySpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public IdentitySpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public IdentitySpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public IdentitySpan start() {
      // check for sampling relevant field here, but there are none.
      return new IdentitySpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets enduser.id.
     *
     * @param enduserId Username or client_id extracted from the access token or Authorization
     *     header in the inbound request from outside the system.
     */
    public IdentitySpanBuilder setEnduserId(String enduserId) {
      internalBuilder.setAttribute(ENDUSER_ID, enduserId);
      return this;
    }

    /**
     * Sets enduser.role.
     *
     * @param enduserRole Actual/assumed role the client is making the request under extracted from
     *     token or application security context.
     */
    public IdentitySpanBuilder setEnduserRole(String enduserRole) {
      internalBuilder.setAttribute(ENDUSER_ROLE, enduserRole);
      return this;
    }

    /**
     * Sets enduser.scope.
     *
     * @param enduserScope Scopes or granted authorities the client currently possesses extracted
     *     from token or application security context. The value would come from the scope
     *     associated with an OAuth 2.0 Access Token or an attribute value in a SAML 2.0 Assertion.
     */
    public IdentitySpanBuilder setEnduserScope(String enduserScope) {
      internalBuilder.setAttribute(ENDUSER_SCOPE, enduserScope);
      return this;
    }
  }
}
