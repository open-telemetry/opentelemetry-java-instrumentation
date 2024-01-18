//package io.opentelemetry.instrumentation.nifi.v1_24_0.model;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.Set;
//import java.util.function.Function;
//import java.util.function.Predicate;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//import javax.servlet.http.HttpServletRequest;
//
//import io.opentelemetry.instrumentation.nifi.v1_24_0.NifiTelemetry;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.Insight;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.NanoClock;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.AgentLogger;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.EventTracker;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.PropertyEncoder;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.EventTracker.Event;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.EventTracker.EventType;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.nifi.connectable.Connectable;
//import org.apache.nifi.connectable.Connection;
//import org.apache.nifi.connectable.StandardConnection;
//import org.apache.nifi.controller.repository.RepositoryRecordType;
//import org.apache.nifi.controller.repository.StandardFlowFileRecord;
//import org.apache.nifi.controller.repository.StandardProcessSession;
//import org.apache.nifi.controller.repository.StandardRepositoryRecord;
//import org.apache.nifi.flowfile.FlowFile;
//import org.apache.nifi.flowfile.attributes.CoreAttributes;
//import org.apache.nifi.logging.LogMessage;
//import org.apache.nifi.processor.ProcessSession;
//import org.apache.nifi.processor.Relationship;
//import io.opentelemetry.api.trace.Span;
//import okhttp3.Request;
//
//public abstract class Nifi {
//
//  public static enum Evaluation {DEAD, ACTIVE}
//
//  public static final String NIFI_PROCESSOR_LOG_LEVEL = "nifi.processor.log.level";
//  public static final String NIFI_PROCESSOR_LOG_ERROR = "nifi.processor.log.error";
//  public static final String NIFI_COMPONENT_NAME = "nifi.component.name";
//  public static final String NIFI_COMPONENT_ID = "nifi.component.id";
//  public static final String NIFI_COMPONENT_PROPERTY = "nifi.component.property";
//  public static final String NIFI_PROCESSOR_RELATIONSHIP = "nifi.processor.relationship";
//  public static final String NIFI_PROCESSOR_PROCESSING_START_EPOCH = "nifi.processor.processing.start.epoch";
//  public static final String NIFI_PROCESSOR_RELATIONSHIP_DESTINATION = "nifi.processor.relationship.destination";
//  public static final String NIFI_PROCESSOR_RELATIONSHIP_TERMINATED = "nifi.processor.relationship.terminated";
//  public static final String NIFI_COMPONENT_MAX_CONCURRENT_TASKS = "nifi.processor.max.concurrent.tasks";
//  public static final String NIFI_FLOWFILE_UUID = "nifi.flowfile.uuid";
//  public static final String NIFI_FLOWFILE_ENTRY_DATE = "nifi.flowfile.entry.date";
//  public static final String NIFI_FLOWFILE_ATTRIBUTE = "nifi.flowfile.attribute";
//  public static final String NIFI_SESSION_ID = "nifi.session.id";
//  public static final String NIFI_COMPONENT_IGNORE = "nifi.component.ignore";
//  public static final String NIFI_COMPONENT_COMMENTS = "nifi.component.comments";
//  public static final String NIFI_COMPONENT_LABEL = "nifi.component.label";
//  public static final String NIFI_COMPONENT_RETRY_COUNT = "nifi.component.retry.count";
//  public static final String NIFI_COMPONENT_TYPE = "nifi.component.type";
//  public static final String NIFI_COMPONENT_PROCESS_GROUP = "nifi.component.process.group";
//  public static final String NIFI_COMPONENT_SCHEDULING = "nifi.component.scheduling";
//  public static final String NIFI_COMPONENT_SCHEDULING_PERIOD = "nifi.component.scheduling.period";
//  public static final String NIFI_COMPONENT_CONNECTION = "nifi.component.connection";
//  public static final long NO_SESSION_ID = -1;
//
//  public static Predicate<StandardRepositoryRecord> isDeadRecord = r -> r.getType() == RepositoryRecordType.DELETE || r.getType() == RepositoryRecordType.CONTENTMISSING;
//
//  public static Predicate<StandardRepositoryRecord> isActiveRecord = r -> !isDeadRecord.test(r);
//
//  public static Function<StandardRepositoryRecord, String> toFlowFileUuid = r -> r.getCurrent().getAttribute(CoreAttributes.UUID.key());
//
//  public static Function<StandardRepositoryRecord, RepositoryRecordType> toRecordType = StandardRepositoryRecord::getType;
//
//  public static Function<List<StandardRepositoryRecord>, StandardRepositoryRecord> firstOnly = list -> list.iterator().next();
//
//  public static FlowFile mock(String flowFileUuid) {
//    return new StandardFlowFileRecord.Builder().id(-1).addAttribute(CoreAttributes.UUID.key(), flowFileUuid).entryDate(-1).build();
//  }
//
//  public static String toFlowFileUuid(StandardRepositoryRecord standardRepositoryRecord) {
//    return standardRepositoryRecord.getCurrent().getAttribute(CoreAttributes.UUID.key());
//  }
//
//  public static boolean isRelationshipDisconnected(StandardIdentifier standardIdentifier, StandardRepositoryRecord standardRepositoryRecord) {
//    return standardIdentifier.getConnectable().getConnections(standardRepositoryRecord.getTransferRelationship()).isEmpty();
//  }
//
//  @SuppressWarnings("unchecked")
//  public static List<FlowFile> toFlowFileList(Object object) {
//
//    List<FlowFile> flowFiles = new ArrayList<>();
//
//    if (object instanceof FlowFile) {
//      flowFiles.add((FlowFile) object);
//    }
//    else {
//      flowFiles.addAll((Collection<FlowFile>) object);
//    }
//
//    return flowFiles;
//  }
//
//  @SuppressWarnings("unchecked")
//  public static List<FlowFile> toFlowFileList(Object[] arguments) {
//
//    List<FlowFile> flowFiles = new ArrayList<>();
//
//    if(arguments == null || arguments.length == 0) {
//      return flowFiles;
//    }
//
//    Object target = arguments[0];
//
//    if (target instanceof FlowFile) {
//      flowFiles.add((FlowFile) target);
//    }
//    else {
//      flowFiles.addAll((Collection<FlowFile>) target);
//    }
//
//    return flowFiles;
//  }
//
//  public static Relationship toRelationship(Object[] arguments) {
//
//    return Arrays.stream(arguments)
//    .filter(Relationship.class::isInstance)
//    .map(Relationship.class::cast)
//    .findAny()
//    .orElse(new Relationship.Builder().name("N/A").build());
//  }
//
//  public static Map<Evaluation, List<StandardRepositoryRecord>> group(List<StandardRepositoryRecord> standardRepositoryRecords) {
//
//    return standardRepositoryRecords.stream()
//    .collect(Collectors.groupingBy(new Function<StandardRepositoryRecord, Evaluation>() {
//
//      @Override
//      public Evaluation apply(StandardRepositoryRecord record) {
//
//        if(record.getType() == RepositoryRecordType.DELETE || record.getType() == RepositoryRecordType.CONTENTMISSING) {
//          return Evaluation.DEAD;
//        }
//        else {
//          return Evaluation.ACTIVE;
//        }
//      }
//    }));
//  }
//
//  public static boolean containsUnvisitedRecords(StandardIdentifier standardIdentifier, List<StandardRepositoryRecord> repositoryRecords) {
//    return repositoryRecords.stream().anyMatch(r -> hasNotVisited(standardIdentifier, r.getCurrent()));
//  }
//
//  public static boolean visitedAllRecords(StandardIdentifier standardIdentifier, List<StandardRepositoryRecord> repositoryRecords) {
//    return repositoryRecords.stream().allMatch(r -> hasVisited(standardIdentifier, r.getCurrent()));
//  }
//
//  public static Map<RepositoryRecordType, List<StandardRepositoryRecord>> groupedByType(List<StandardRepositoryRecord> repositoryRecords) {
//    return repositoryRecords.stream().collect(Collectors.groupingBy(StandardRepositoryRecord::getType));
//  }
//
//  public static Long getProcessingStartTime(StandardIdentifier standardIdentifier, FlowFile flowFile) {
//
//    String processingStartTime = flowFile.getAttribute(NIFI_PROCESSOR_PROCESSING_START_EPOCH);
//
//    ProcessSession processSession = standardIdentifier.getStandardProcessSession();
//
//    flowFile = processSession.removeAttribute(flowFile, NIFI_PROCESSOR_PROCESSING_START_EPOCH);
//
//    if(StringUtils.isBlank(processingStartTime) || !StringUtils.isNumeric(processingStartTime)) {
//      return NanoClock.tick();
//    }
//    else {
//      return Long.valueOf(processingStartTime);
//    }
//  }
//
//  public static void putFlowFileStartTime(StandardIdentifier standardIdentifier, FlowFile flowFile) {
//
//    ProcessSession processSession = standardIdentifier.getStandardProcessSession();
//    long processingStartTime = NanoClock.tick();
//
//    flowFile = processSession.putAttribute(flowFile, NIFI_PROCESSOR_PROCESSING_START_EPOCH, Long.toString(processingStartTime));
//  }
//
//  public static void putTrackingAttributes(StandardIdentifier standardIdentifier, FlowFile flowFile) {
//
//    ProcessSession processSession = standardIdentifier.getStandardProcessSession();
//
//    String componentId = standardIdentifier.getIdentity().getId();
//    String sessionId = Long.toString(standardIdentifier.getSessionId());
//
//    Map<String, String> map = Map.of(NIFI_COMPONENT_ID, componentId, NIFI_SESSION_ID, sessionId);
//
//    flowFile = processSession.putAttribute(flowFile, PropertyEncoder.NIFI_AGENT_TRACKING_KEY, PropertyEncoder.fromMap(map));
//  }
//
//  public static boolean hasNotVisited(StandardIdentifier standardIdentifier, FlowFile flowFile) {
//    return !hasVisited(standardIdentifier, flowFile);
//  }
//
//  public static boolean hasVisited(StandardIdentifier standardIdentifier, FlowFile flowFile) {
//
//    String flowFileUuid = flowFile.getAttribute(CoreAttributes.UUID.key());
//
//    Map<String, String> map = PropertyEncoder.toMap(flowFile);
//
//    String componentUuid = map.get(NIFI_COMPONENT_ID);
//    String sessionId = map.get(NIFI_SESSION_ID);
//
//    if(StringUtils.isBlank(flowFileUuid)) {
//      return false;
//    }
//    else if(StringUtils.isBlank(componentUuid)) {
//      return false;
//    }
//    else if(StringUtils.isBlank(sessionId)) {
//      return false;
//    }
//    else if(!StringUtils.isNumeric(sessionId)) {
//      return false;
//    }
//    else {
//      return componentUuid.equals(standardIdentifier.getIdentity().getId()) && Long.parseLong(sessionId) == standardIdentifier.getSessionId();
//    }
//  }
//
//  public static boolean isCycleDetected(StandardProcessSession standardProcessSession, FlowFile flowFile) {
//
//    String uuid = flowFile.getAttribute(CoreAttributes.UUID.key());
//
//    String componentUuid = PropertyEncoder.toMap(flowFile).get(NIFI_COMPONENT_ID);
//
//    if(StringUtils.isBlank(componentUuid)) {
//      return false;
//    }
//
//    try {
//
//      Identity identity = Insight.getComponentIdentity(standardProcessSession);
//
//      boolean cycleDetected = identity.getId().equals(componentUuid);
//
//      if(cycleDetected){
//        AgentLogger.log(State.AGENT_OPERATION, "Cycle detected Component [" + identity.getName() + "] FlowFile [" + uuid + "]");
//      }
//
//      return cycleDetected;
//    }
//    catch (Exception e) {
//
//      AgentLogger.log(State.AGENT_OPERATION, "Failure assessing cycle-detection " + e.getMessage());
//    }
//
//    return false;
//  }
//
//  public static void setParentSpanAttrIfPresent(HttpServletRequest httpServletRequest, ProcessSession processSession, FlowFile flowFile) {
//
//    Optional<String> traceParent = Optional.ofNullable(httpServletRequest.getHeader(Instrumentation.TRACE_PARENT));
//    traceParent.ifPresent(s -> processSession.putAttribute(flowFile, Instrumentation.TRACE_PARENT, s));
//
//    Optional<String> traceState = Optional.ofNullable(httpServletRequest.getHeader(Instrumentation.TRACE_STATE));
//    traceState.ifPresent(s -> processSession.putAttribute(flowFile, Instrumentation.TRACE_STATE, s));
//  }
//
//  public static void traceParentToHeaders(Request.Builder builder, FlowFile flowFile) {
//
//    Optional<String> traceParent = Optional.ofNullable(flowFile.getAttribute(Instrumentation.TRACE_PARENT));
//    traceParent.ifPresent(s -> builder.addHeader(Instrumentation.TRACE_PARENT, s));
//
//    Optional<String> traceState = Optional.ofNullable(flowFile.getAttribute(Instrumentation.TRACE_STATE));
//    traceState.ifPresent(s -> builder.addHeader(Instrumentation.TRACE_STATE, s));
//  }
//
//  public static void traceParentToFlowFile(HttpRequest httpRequest) {
//
//    HttpServletRequest httpServletRequest = httpRequest.getHttpServletRequest();
//    ProcessSession processSession = httpRequest.getStandardProcessSession();
//    FlowFile flowFile = httpRequest.getFlowFile();
//
//    Optional<String> traceParent = Optional.ofNullable(httpServletRequest.getHeader(Instrumentation.TRACE_PARENT));
//    traceParent.ifPresent(s -> processSession.putAttribute(flowFile, Instrumentation.TRACE_PARENT, s));
//
//    Optional<String> traceState = Optional.ofNullable(httpServletRequest.getHeader(Instrumentation.TRACE_STATE));
//    traceState.ifPresent(s -> processSession.putAttribute(flowFile, Instrumentation.TRACE_STATE, s));
//  }
//
//  public static Span updateTransferred(StandardIdentifier standardIdentifier, StandardRepositoryRecord standardRepositoryRecord, DynamicDetails dynamicDetails, Span ...spans) {
//
//    String componentId = standardIdentifier.getIdentity().getId();
//    String flowFileUuid = toFlowFileUuid(standardRepositoryRecord);
//
//    Event<LogMessage> event = EventTracker.getEvent(componentId, flowFileUuid, NO_SESSION_ID, EventType.LOG_MESSAGE);
//    Optional<EventTracker.Event<LogMessage>> optional = Optional.ofNullable(event);
//
//    FlowFile flowFile = standardRepositoryRecord.getCurrent();
//
//    Relationship relationship = standardRepositoryRecord.getTransferRelationship();
//    standardRepositoryRecord.setTransferRelationship(null);
//
//    Long startTime = getProcessingStartTime(standardIdentifier, flowFile);
//
//    LogMessage logMessage = optional
//    .map(Event::getPayload)
//    .map(LogMessage.class::cast)
//    .orElse(null);
//
//    Span span = NifiTelemetry.createSpan(startTime, flowFile, relationship, dynamicDetails, standardIdentifier, logMessage, List.of(spans));
//
//    putTrackingAttributes(standardIdentifier, flowFile);
//
//    standardRepositoryRecord.setTransferRelationship(relationship);
//
//    optional.ifPresent(EventTracker::removeEvent);
//
//    return span;
//  }
//
//  public static String computeRelationshipName(StandardIdentifier standardIdentifier, Relationship relationship) {
//
//    boolean isDisconnected = standardIdentifier.getConnectable().getConnections(relationship).isEmpty();
//
//    Stream<Connection> connectionStream = null;
//
//    if(isDisconnected) {
//
//      connectionStream = Stream.ofNullable(standardIdentifier.getConnectable())
//      .map(Connectable::getConnections)
//      .flatMap(Set::stream);
//    }
//    else {
//
//      connectionStream = Stream.ofNullable(standardIdentifier.getConnectable())
//      .map(s -> s.getConnections(relationship))
//      .flatMap(Set::stream);
//    }
//
//    String destinationNames = connectionStream
//    .map(StandardConnection.class::cast)
//    .map(StandardConnection::getDestination)
//    .map(Connectable::getName)
//    .map(String::toLowerCase)
//    .collect(Collectors.joining(","));
//
//    boolean isProcessor = StandardIdentifier.isProcessorConnectableType(standardIdentifier);
//
//    String relationshipName = null;
//
//    if(!isProcessor) {
//
//      relationshipName = destinationNames;
//    }
//    else {
//
//      if(StringUtils.isBlank(relationship.getName())) {
//        relationshipName = destinationNames + " : STOPPED";
//      }
//      else {
//        relationshipName = Stream.of(relationship.getName()).collect(Collectors.joining(","));
//      }
//    }
//
//    return relationshipName;
//  }
//}