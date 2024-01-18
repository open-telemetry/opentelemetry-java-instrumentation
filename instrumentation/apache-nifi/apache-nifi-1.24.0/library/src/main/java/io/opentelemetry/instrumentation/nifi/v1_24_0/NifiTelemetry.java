///*
// * Copyright The OpenTelemetry Authors
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package io.opentelemetry.instrumentation.nifi.v1_24_0;
//
//import com.google.errorprone.annotations.CanIgnoreReturnValue;
//import io.opentelemetry.api.GlobalOpenTelemetry;
//import io.opentelemetry.api.OpenTelemetry;
//import io.opentelemetry.api.common.AttributeKey;
//import io.opentelemetry.api.common.Attributes;
//import io.opentelemetry.api.trace.Span;
//import io.opentelemetry.api.trace.SpanBuilder;
//import io.opentelemetry.api.trace.StatusCode;
//import io.opentelemetry.api.trace.Tracer;
//import io.opentelemetry.context.Context;
//import io.opentelemetry.context.Scope;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.DynamicDetails;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.StandardIdentifier;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.FlowFileCarrier;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.Identity;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.Fidelity;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.State;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.Nifi;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.AgentLogger;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.Insight;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.NanoClock;
//import org.apache.commons.collections4.CollectionUtils;
//import org.apache.nifi.connectable.Connection;
//import org.apache.nifi.controller.repository.RepositoryRecordType;
//import org.apache.nifi.controller.repository.StandardProcessSession;
//import org.apache.nifi.controller.repository.StandardRepositoryRecord;
//import org.apache.nifi.flowfile.FlowFile;
//import org.apache.nifi.flowfile.attributes.CoreAttributes;
//import org.apache.nifi.logging.LogMessage;
//import org.apache.nifi.processor.Relationship;
//
//import java.util.concurrent.TimeUnit;
//
//import java.util.List;
//import java.util.Locale;
//import java.util.Map;
//import java.util.Iterator;
//import java.util.Objects;
//import java.util.Date;
//
//import static io.opentelemetry.instrumentation.nifi.v1_24_0.model.Instrumentation.INSTRUMENTATION_NAME;
//import static io.opentelemetry.instrumentation.nifi.v1_24_0.model.Instrumentation.INSTRUMENTATION_VERSION;
//
//@SuppressWarnings({"unused"})
//public final class NifiTelemetry {
//
//  private static final OpenTelemetry openTelemetry;
//  private static final Tracer tracer;
//
//  static {
//
//    openTelemetry = GlobalOpenTelemetry.get();
//    tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
//  }
//
//  private NifiTelemetry() {}
//
//  public static String createComponentSpanName(StandardIdentifier standardIdentifier, Relationship relationship) {
//    return standardIdentifier.getIdentity().getName() + " [ " + Nifi.computeRelationshipName(standardIdentifier, relationship) + " ]";
//  }
//
//  public static Span createSpan(long startTime, FlowFile flowFile, Relationship relationship, DynamicDetails dynamicDetails, StandardIdentifier standardIdentifier, @org.jetbrains.annotations.Nullable LogMessage logMessage, List<Span> spans) {
//
//    StandardProcessSession standardProcessSession = standardIdentifier.getStandardProcessSession();
//    FlowFileCarrier carrier = new FlowFileCarrier(standardProcessSession, flowFile);
//
//    Context context = openTelemetry.getPropagators()
//    .getTextMapPropagator()
//    .extract(Context.current(), carrier, FlowFileCarrier.getExtractor());
//
//    String spanName = createComponentSpanName(standardIdentifier, relationship);
//
//    SpanBuilder spanBuilder = tracer
//    .spanBuilder(spanName)
//    .setParent(context)
//    .setStartTimestamp(startTime, TimeUnit.NANOSECONDS);
//
//    spans.stream()
//    .map(Span::getSpanContext)
//    .peek(s -> {
//      System.out.println("Had links " + standardIdentifier.getIdentity().getName());
//    })
//    .forEach(spanBuilder::addLink);
//
//    Span span = spanBuilder.startSpan();
//
//    try(Scope scope = span.makeCurrent()){
//
//      addAttributes(span, flowFile, relationship, dynamicDetails, standardIdentifier, logMessage);
//
//      openTelemetry.getPropagators()
//      .getTextMapPropagator()
//      .inject(Context.current(), carrier, FlowFileCarrier.getInjector());
//    }
//    finally {
//
//      span.end(NanoClock.systemDefaultZone().nanos(), TimeUnit.NANOSECONDS);
//    }
//
//    return span;
//  }
//
//  @CanIgnoreReturnValue
//  public static Span addAttributes(Span span, FlowFile flowFile, Relationship relationship, DynamicDetails dynamicDetails, StandardIdentifier standardIdentifier, @org.jetbrains.annotations.Nullable LogMessage logMessage) {
//
//    span.setAttribute(Nifi.NIFI_PROCESSOR_RELATIONSHIP, Nifi.computeRelationshipName(standardIdentifier, relationship));
//    span.setAttribute(Nifi.NIFI_PROCESSOR_RELATIONSHIP_TERMINATED, standardIdentifier.getConnectable().getConnections(relationship).isEmpty());
//    span.setAttribute(Nifi.NIFI_COMPONENT_ID, standardIdentifier.getIdentity().getId());
//    span.setAttribute(Nifi.NIFI_COMPONENT_NAME, standardIdentifier.getIdentity().getName());
//    span.setAttribute(Nifi.NIFI_FLOWFILE_ENTRY_DATE, new Date(flowFile.getEntryDate()).toString());
//    span.setAttribute(Nifi.NIFI_FLOWFILE_UUID, flowFile.getAttribute(CoreAttributes.UUID.key()));
//    span.setAttribute(Nifi.NIFI_SESSION_ID, standardIdentifier.getSessionId());
//
//    for(Map.Entry<String, String> entry : flowFile.getAttributes().entrySet()) {
//      span.setAttribute(Nifi.NIFI_FLOWFILE_ATTRIBUTE + "." + entry.getKey(), entry.getValue());
//    }
//
//    for(Map.Entry<String, Object> entry : Insight.getAllGetterMethods(flowFile).entrySet()) {
//      span.setAttribute(Nifi.NIFI_FLOWFILE_ATTRIBUTE + "." + entry.getKey(), entry.getValue().toString());
//    }
//
//    for(Map.Entry<String, String> entry : dynamicDetails.getProcessorPropertyMap().entrySet()) {
//      span.setAttribute(Nifi.NIFI_COMPONENT_PROPERTY + ".'" + entry.getKey().toLowerCase(Locale.ENGLISH) + "'", entry.getValue());
//    }
//
//    standardIdentifier.getConnectable().getConnections().stream()
//    .map(Connection::getDestination)
//    .map(destination -> new Identity(destination.getIdentifier(), destination.getName()))
//    .forEach(i -> span.setAttribute(Nifi.NIFI_PROCESSOR_RELATIONSHIP + ".destination-" + i.getId(), i.getName()));
//
//    span.setAttribute(Nifi.NIFI_COMPONENT_COMMENTS, java.util.Objects.requireNonNull(dynamicDetails.getComments()));
//    span.setAttribute(Nifi.NIFI_COMPONENT_LABEL, java.util.Objects.requireNonNull(dynamicDetails.getLabel()));
//    span.setAttribute(Nifi.NIFI_COMPONENT_TYPE, java.util.Objects.requireNonNull(dynamicDetails.getConnectableType()).name().toLowerCase(Locale.ENGLISH));
//    span.setAttribute(Nifi.NIFI_COMPONENT_PROCESS_GROUP, java.util.Objects.requireNonNull(dynamicDetails.getProcessGroup()).getName());
//    span.setAttribute(Nifi.NIFI_COMPONENT_SCHEDULING, java.util.Objects.requireNonNull(dynamicDetails.getSchedulingStrategy()).name().toLowerCase(Locale.ENGLISH));
//    span.setAttribute(Nifi.NIFI_COMPONENT_SCHEDULING_PERIOD, dynamicDetails.getSchedulingStrategy().getDefaultSchedulingPeriod());
//
//    if(dynamicDetails.getConnections() != null) {
//
//      dynamicDetails.getConnections().stream()
//      .map(Connection::getDestination)
//      .map(destination -> new Identity(destination.getIdentifier(), destination.getComponentType()))
//      .forEach(x -> span.setAttribute(Nifi.NIFI_COMPONENT_CONNECTION + "-" + x.getId(), x.getName()));
//    }
//
//    if(logMessage != null) {
//
//      span.setStatus(StatusCode.ERROR, logMessage.getMessage());
//
//      Attributes eventAttributes = Attributes.of(
//        AttributeKey.stringKey(Nifi.NIFI_PROCESSOR_LOG_ERROR), logMessage.getMessage(),
//        AttributeKey.stringKey(Nifi.NIFI_PROCESSOR_LOG_LEVEL), logMessage.getLogLevel().name()
//      );
//
//      span.addEvent(StatusCode.ERROR.name().toLowerCase(Locale.ENGLISH), eventAttributes);
//    }
//
//    return span;
//  }
//
//  public static void computeTracing(Fidelity fidelity, StandardIdentifier standardIdentifier, DynamicDetails dynamicDetails, List<StandardRepositoryRecord> records) {
//
//    switch (fidelity) {
//
//      case ONE_TO_ONE : {
//
//        for(StandardRepositoryRecord record : records) {
//          Nifi.updateTransferred(standardIdentifier, record, dynamicDetails);
//        }
//
//        break;
//      }
//      case ONE_TO_MANY : {
//
//        Map<RepositoryRecordType, List<StandardRepositoryRecord>> groupedRecords = Nifi.groupedByType(records);
//
//        if(groupedRecords == null) {
//          return;
//        }
//
//        List<StandardRepositoryRecord> list = groupedRecords.get(RepositoryRecordType.UPDATE);
//
//        if(list == null || !list.isEmpty()) {
//
//          Iterator<StandardRepositoryRecord> itr = Objects.requireNonNull(list).iterator();
//
//          if (itr.hasNext()) {
//
//            StandardRepositoryRecord primaryRecord = itr.next();
//            Nifi.updateTransferred(standardIdentifier, primaryRecord, dynamicDetails);
//          }
//        }
//
//        if(CollectionUtils.isNotEmpty(groupedRecords.get(RepositoryRecordType.CREATE))) {
//
//          List<StandardRepositoryRecord> cList = groupedRecords.get(RepositoryRecordType.CREATE);
//
//          if(cList != null) {
//
//            for (StandardRepositoryRecord record : cList) {
//              Nifi.updateTransferred(standardIdentifier, record, dynamicDetails);
//            }
//          }
//        }
//
//        break;
//      }
//      case MANY_TO_ONE : {
//
//        Map<RepositoryRecordType, List<StandardRepositoryRecord>> groupedRecords = Nifi.groupedByType(records);
//
//        if(groupedRecords == null || groupedRecords.isEmpty()){
//          break;
//        }
//
//        List<StandardRepositoryRecord> uList = groupedRecords.get(RepositoryRecordType.UPDATE);
//
//        if(uList != null) {
//
//          Span[] completedSpans = uList.stream()
//          .map(record -> Nifi.updateTransferred(standardIdentifier, record, dynamicDetails))
//          .filter(Objects::nonNull)
//          .toArray(Span[]::new);
//        }
//
//        List<StandardRepositoryRecord> cList = groupedRecords.get(RepositoryRecordType.CREATE);
//
//        if(cList == null || !cList.isEmpty()) {
//
//          Iterator<StandardRepositoryRecord> itr = Objects.requireNonNull(cList).iterator();
//
//          if (itr.hasNext()) {
//
//            StandardRepositoryRecord primaryRecord = itr.next();
//            Nifi.updateTransferred(standardIdentifier, primaryRecord, dynamicDetails);
//          }
//        }
//
//        break;
//      }
//      default : {
//
//        AgentLogger.log(State.COMMIT_SESSION, "Unknown implemented processor type (M:M) " + standardIdentifier);
//        break;
//      }
//    }
//  }
//}