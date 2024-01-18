//package io.opentelemetry.instrumentation.nifi.v1_24_0.model;
//
//import javax.servlet.http.HttpServletRequest;
//
//import org.apache.nifi.controller.repository.StandardProcessSession;
//import org.apache.nifi.flowfile.FlowFile;
//
//public class HttpRequest {
//
//  @io.opentelemetry.testing.internal.io.micrometer.common.lang.NonNull
//  private final HttpServletRequest httpServletRequest;
//  @io.opentelemetry.testing.internal.io.micrometer.common.lang.NonNull
//  private final StandardProcessSession standardProcessSession;
//  @io.opentelemetry.testing.internal.io.micrometer.common.lang.NonNull
//  private final FlowFile flowFile;
//
//  private HttpRequest(Builder builder) {
//    this.httpServletRequest = java.util.Objects.requireNonNull(builder.httpServletRequest);
//    this.standardProcessSession = java.util.Objects.requireNonNull(builder.standardProcessSession);
//    this.flowFile = java.util.Objects.requireNonNull(builder.flowFile);
//  }
//
////  private HttpRequest(HttpServletRequest httpServletRequest, StandardProcessSession standardProcessSession, FlowFile flowFile) {
////    this.httpServletRequest = httpServletRequest;
////    this.standardProcessSession = standardProcessSession;
////    this.flowFile = flowFile;
////  }
//
//  public HttpServletRequest getHttpServletRequest() {
//    return httpServletRequest;
//  }
//
//  public StandardProcessSession getStandardProcessSession() {
//    return standardProcessSession;
//  }
//
//  public FlowFile getFlowFile() {
//    return flowFile;
//  }
//
//  public static Builder builder() {
//    return new Builder();
//  }
//
//  public static final class Builder {
//
//    @org.jetbrains.annotations.Nullable
//    private HttpServletRequest httpServletRequest = null;
//
//    @org.jetbrains.annotations.Nullable
//    private StandardProcessSession standardProcessSession = null;
//
//    @org.jetbrains.annotations.Nullable
//    private FlowFile flowFile = null;
//
//    private Builder() {
//    }
//
//    @com.google.errorprone.annotations.CanIgnoreReturnValue
//    public Builder withHttpServletRequest(HttpServletRequest httpServletRequest) {
//      this.httpServletRequest = httpServletRequest;
//      return this;
//    }
//
//    @com.google.errorprone.annotations.CanIgnoreReturnValue
//    public Builder withStandardProcessSession(StandardProcessSession standardProcessSession) {
//      this.standardProcessSession = standardProcessSession;
//      return this;
//    }
//
//    @com.google.errorprone.annotations.CanIgnoreReturnValue
//    public Builder withFlowFile(FlowFile flowFile) {
//      this.flowFile = flowFile;
//      return this;
//    }
//
//    public HttpRequest build() {
//      return new HttpRequest(this);
//    }
//  }
//
//  @com.google.errorprone.annotations.CanIgnoreReturnValue
//  public static HttpRequest toHttpRequest(Object[] arguments) {
//
//    HttpServletRequest httpServletRequest = (HttpServletRequest) arguments[0];
//    StandardProcessSession standardProcessSession = (StandardProcessSession) arguments[1];
//    FlowFile flowFile = (FlowFile) arguments[4];
//
//    return HttpRequest.builder()
//    .withHttpServletRequest(httpServletRequest)
//    .withStandardProcessSession(standardProcessSession)
//    .withFlowFile(flowFile)
//    .build();
//  }
//}