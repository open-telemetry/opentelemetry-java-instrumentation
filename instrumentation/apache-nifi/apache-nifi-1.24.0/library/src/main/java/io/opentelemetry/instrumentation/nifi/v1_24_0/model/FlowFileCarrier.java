//package io.opentelemetry.instrumentation.nifi.v1_24_0.model;
//
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.Insight;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.nifi.flowfile.FlowFile;
//import org.apache.nifi.processor.ProcessSession;
//
//import io.opentelemetry.context.propagation.TextMapGetter;
//import io.opentelemetry.context.propagation.TextMapSetter;
//import org.jetbrains.annotations.Nullable;
//
//import static io.opentelemetry.instrumentation.nifi.v1_24_0.model.Instrumentation.TRACE_PARENT;
//import static io.opentelemetry.instrumentation.nifi.v1_24_0.model.Instrumentation.TRACE_PARENT_PREVIOUS;
//
//public class FlowFileCarrier {
//
//  private final FlowFile flowFile;
//  private final ProcessSession processSession;
//  private final boolean cycleDetected;
//
//  public FlowFileCarrier(ProcessSession processSession, FlowFile flowFile) {
//    this.processSession = processSession;
//    this.flowFile = flowFile;
//    this.cycleDetected = Nifi.isCycleDetected(Insight.autoCast(processSession), flowFile);
//  }
//
//  public ProcessSession getProcessSession() {
//    return processSession;
//  }
//
//  public FlowFile getFlowFile() {
//    return flowFile;
//  }
//
//  public boolean isCycleDetected() {
//    return cycleDetected;
//  }
//
//  public static FlowFileInjector getInjector() {
//    return new FlowFileInjector();
//  }
//
//  public static FlowFileExtractor getExtractor() {
//    return new FlowFileExtractor();
//  }
//
//  public static class FlowFileInjector implements TextMapSetter<FlowFileCarrier> {
//
//    @Nullable
//    private String traceParentId = null;
//
//    @Nullable
//    private FlowFile flowFile = null;
//
//    @Nullable
//    public String getTraceParentId() {
//      return traceParentId;
//    }
//
//    @Nullable
//    public FlowFile getFlowFile() {
//      return flowFile;
//    }
//
//    @Override
//    public void set(@Nullable FlowFileCarrier flowFileCarrier, String key, String value) {
//
//      if(flowFileCarrier == null) {
//        return;
//      }
//
//      ProcessSession processSession = flowFileCarrier.getProcessSession();
//
//      flowFile = flowFileCarrier.getFlowFile();
//
//      if (key.equalsIgnoreCase(TRACE_PARENT)) {
//
//        traceParentId = value;
//
//        String previousTraceId = flowFile.getAttribute(TRACE_PARENT_PREVIOUS);
//        String currentTraceId = flowFile.getAttribute(TRACE_PARENT);
//
//        if(StringUtils.isBlank(currentTraceId)) {
//          currentTraceId = value;
//        }
//
//        if(!flowFileCarrier.isCycleDetected() || StringUtils.isBlank(previousTraceId)) {
//          flowFile = processSession.putAttribute(flowFile, TRACE_PARENT_PREVIOUS, currentTraceId);
//          traceParentId = currentTraceId;
//        }
//
//        flowFile = processSession.putAttribute(flowFile, TRACE_PARENT, value);
//      }
//      else {
//
//        flowFile = processSession.putAttribute(flowFile, key, value);
//      }
//    }
//  }
//
//  public static class FlowFileExtractor implements TextMapGetter<FlowFileCarrier> {
//
//    @Nullable
//    private String forcedParentId = null;
//
//    public FlowFileExtractor() {}
//
//    public FlowFileExtractor(@org.jetbrains.annotations.Nullable String forcedParentId) {
//      this.forcedParentId = forcedParentId;
//    }
//
//    @Override
//    public String get(@Nullable FlowFileCarrier flowFileCarrier, @org.jetbrains.annotations.NotNull String key) {
//
//      if(flowFileCarrier == null) {
//        return org.apache.commons.lang3.StringUtils.EMPTY;
//      }
//
//      FlowFile flowFile = flowFileCarrier.getFlowFile();
//
//      if(flowFile == null) {
//        return org.apache.commons.lang3.StringUtils.EMPTY;
//      }
//
//      if(StringUtils.isNotBlank(forcedParentId)) {
//        return forcedParentId;
//      }
//
//      if(flowFileCarrier.isCycleDetected()) {
//        return flowFile.getAttribute(TRACE_PARENT_PREVIOUS);
//      }
//      else {
//        return flowFile.getAttribute(key);
//      }
//    }
//
//    @Override
//    public Iterable<String> keys(FlowFileCarrier flowFileCarrier) {
//      return flowFileCarrier.getFlowFile().getAttributes().keySet();
//    }
//  }
//}