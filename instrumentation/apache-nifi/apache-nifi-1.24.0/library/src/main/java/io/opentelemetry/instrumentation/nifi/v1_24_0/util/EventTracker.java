//package io.opentelemetry.instrumentation.nifi.v1_24_0.util;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.locks.ReentrantLock;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.Collection;
//import java.util.UUID;
//import java.util.Map;
//import java.util.Objects;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.Locale;
//
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.Nifi;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.StandardIdentifier;
//import org.apache.nifi.controller.repository.StandardRepositoryRecord;
//import org.apache.nifi.flowfile.FlowFile;
//import org.apache.nifi.flowfile.attributes.CoreAttributes;
//
//public class EventTracker {
//
//  public enum EventType {LOG_MESSAGE, EPOCH_START, ALL}
//
//  private static final Map<String, Event<?>> eventMap = new HashMap<>();
//
//  private static final ReentrantLock lock = new ReentrantLock();
//
//  private EventTracker(){}
//
//  public static void putEvent(Event<?> event) {
//
//    lock.lock();
//
//    try {
//
//      eventMap.put(event.getEventKey(), event);
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  public static void removeEvent(Event<?> event) {
//
//    lock.lock();
//
//    try {
//
//      eventMap.remove(event.getEventKey());
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  public static void pruneEvents(String componentUuid, Collection<String> flowFileUuids, Long sessionId, EventType eventType) {
//
//    lock.lock();
//
//    try {
//
//      flowFileUuids.stream()
//      .flatMap(uuid -> {
//
//        if(eventType == EventType.ALL) {
//          return Stream.of(EventType.EPOCH_START, EventType.LOG_MESSAGE).map(e -> Event.toEventKey(componentUuid, uuid, sessionId, e));
//        }
//        else {
//          return Stream.of(eventType).map(e -> Event.toEventKey(componentUuid, uuid, sessionId, e));
//        }
//      })
//      .forEach(eventMap::remove);
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  public static void pruneEvents(StandardIdentifier standardIdentifier, List<StandardRepositoryRecord> records, EventType eventType) {
//
//    lock.lock();
//
//    try {
//
//      records.stream()
//      .map(StandardRepositoryRecord::getCurrent)
//      .map(ff -> ff.getAttribute(CoreAttributes.UUID.key()))
//      .flatMap(uuid -> {
//
//        if(eventType == EventType.ALL) {
//          return Stream.of(EventType.EPOCH_START, EventType.LOG_MESSAGE).map(e -> Event.toEventKey(standardIdentifier.getIdentity().getId(), uuid, standardIdentifier.getSessionId(), e));
//        }
//        else {
//          return Stream.of(eventType).map(e -> Event.toEventKey(standardIdentifier.getIdentity().getId(), uuid, standardIdentifier.getSessionId(), e));
//        }
//      })
//      .forEach(eventMap::remove);
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  public static Map<EventType, Optional<Event<?>>> getEvents(StandardIdentifier standardIdentifier, StandardRepositoryRecord standardRepositoryRecord) {
//
//    lock.lock();
//
//    try {
//
//      String componentUuid = standardIdentifier.getIdentity().getId();
//      String flowFileUuid = standardRepositoryRecord.getCurrent().getAttribute(CoreAttributes.UUID.key());
//      long sessionId = standardIdentifier.getSessionId();
//
//      return Stream.of(EventType.LOG_MESSAGE, EventType.EPOCH_START)
//      .map(event -> {
//
//        if(event == EventType.LOG_MESSAGE) {
//          return eventMap.get(Event.toEventKey(componentUuid, flowFileUuid, Nifi.NO_SESSION_ID, event));
//        }
//        else {
//          return eventMap.get(Event.toEventKey(componentUuid, flowFileUuid, sessionId, event));
//        }
//      })
//      .filter(Objects::nonNull)
//      .collect(Collectors.groupingBy(Event::getEventType, Collectors.collectingAndThen(Collectors.toList(), e -> Optional.ofNullable(e.iterator().next()))));
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  public static Map<EventType, Event<?>> getEvents(String componentUuid, String flowFileUuid, Long sessionId) {
//
//    lock.lock();
//
//    try {
//
//      return Stream.of(EventType.LOG_MESSAGE, EventType.EPOCH_START)
//      .map(e -> eventMap.get(Event.toEventKey(componentUuid, flowFileUuid, sessionId, e)))
//      .collect(Collectors.groupingBy(Event::getEventType, Collectors.collectingAndThen(Collectors.toList(), e -> e.iterator().next())));
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  @SuppressWarnings("unchecked")
//  @org.jetbrains.annotations.Nullable
//  public static <T> Event<T> getEvent(String componentUuid, String flowFileUuid, Long sessionId, EventType eventType) {
//
//    lock.lock();
//
//    try {
//
//      return (Event<T>) eventMap.get(Event.toEventKey(componentUuid, flowFileUuid, sessionId, eventType));
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  @SuppressWarnings("unchecked")
//  public static <T> Optional<Event<T>> getEvent(String key) {
//
//    lock.lock();
//
//    try {
//
//      return Optional.ofNullable((Event<T>) eventMap.get(key));
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  @SuppressWarnings("unchecked")
//  public static <T> Optional<Event<T>>getEvent(StandardIdentifier standardIdentifier, FlowFile flowFile, EventType eventType) {
//
//    lock.lock();
//
//    try {
//
//      return Optional.ofNullable((Event<T>) eventMap.get(Event.toEventKey(standardIdentifier.getIdentity().getId(), flowFile.getAttribute(CoreAttributes.UUID.key()), standardIdentifier.getSessionId(), eventType)));
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  public static List<Event<?>> getAllEvents() {
//
//    lock.lock();
//
//    try {
//
//      return new ArrayList<>(eventMap.values());
//    }
//    finally {
//      lock.unlock();
//    }
//  }
//
//  public static class StandardEvent {
//
//    private final Map<String, Event<?>> events = new HashMap<>();
//
//    public <T> void putEvent(String key, Event<T> value) {
//      events.put(key, value);
//    }
//
//    @SuppressWarnings({"unchecked", "nullable"})
//    @io.opentelemetry.testing.internal.io.micrometer.common.lang.Nullable
//    public <T> Event<T> getEvent(String key){
//      return (Event<T>) events.get(key);
//    }
//  }
//
//  public static void monitorEventsLifeCycle() {
//
//    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
//
//    Runnable runnable = () -> {
//
//      List<Event<?>> events = getAllEvents();
//
//      long total = events.size();
//
//      if(total > 0) {
//
//        long seconds = events.stream()
//        .reduce((a, b) -> a.getTimeExisted().compareTo(b.getTimeExisted()) > 0 ? a : b).get()
//        .getTimeExisted()
//        .toSeconds();
//
//        System.out.println(">>>> [" + total + "] Undispatched Events. Longest undispatched event has been held for [" + seconds + "] sec");
//      }
//      else {
//
//        System.out.println(">>>> EventTracker is empty");
//      }
//    };
//
//    Thread thread = new Thread(runnable);
//    thread.setName(">>>>Nifi-Agent-EventMonitor");
//
//    executorService.scheduleAtFixedRate(thread, 0, 5, TimeUnit.SECONDS);
//  }
//
//  public static class Event<T> {
//
//    private final UUID uuid = UUID.randomUUID();
//    private final Instant created = Instant.now();
//
//    private final String eventKey;
//
//    private final EventType eventType;
//    private final String componentUuid;
//    private final String flowFileUuid;
//    private final Long sessionId;
//
//    private final T payload;
//
//    public Event(EventType eventType, String componentUuid, String flowFileUuid, Long sessionId, T payload) {
//      this.eventType = eventType;
//      this.componentUuid = componentUuid;
//      this.flowFileUuid = flowFileUuid;
//      this.sessionId = sessionId;
//      this.payload = payload;
//      this.eventKey = toEventKey(componentUuid, flowFileUuid, sessionId, eventType);
//    }
//
//    public String getId() {
//      return uuid.toString();
//    }
//
//    public Duration getTimeExisted() {
//      return Duration.between(created, Instant.now());
//    }
//
//    public T getPayload() {
//      return payload;
//    }
//
//    public String getComponentUuid() {
//      return componentUuid;
//    }
//
//    public String getFlowFileUuid() {
//      return flowFileUuid;
//    }
//
//    public EventType getEventType() {
//      return eventType;
//    }
//
//    public Long getSessionId() {
//      return sessionId;
//    }
//
//    public String getEventKey() {
//      return toEventKey(componentUuid, flowFileUuid, sessionId, eventType);
//    }
//
//    public static String toEventKey(String componentUuid, String flowFileUuid, Long sessionId, EventType eventType) {
//
//      StringBuilder stringBuilder = new StringBuilder();
//
//      stringBuilder.append(eventType.name().toLowerCase(Locale.ENGLISH)).append("-");
//      stringBuilder.append(componentUuid).append("-");
//      stringBuilder.append(flowFileUuid);
//
//      if(sessionId != null) {
//        stringBuilder.append("-").append(sessionId);
//      }
//
//      return stringBuilder.toString();
//    }
//
//    @Override
//    public int hashCode() {
//      return Objects.hash(componentUuid, flowFileUuid, payload, sessionId, eventType, eventKey);
//    }
//
//    @Override
//    @SuppressWarnings("EqualsGetClass")
//    public boolean equals(Object obj) {
//
//      if (obj == null) {
//        return false;
//      }
//
//      if(!this.getClass().equals(obj.getClass())) {
//        return false;
//      }
//
//      Event<?> other = (Event<?>) obj;
//
//      return Objects.equals(componentUuid, other.componentUuid)
//      && Objects.equals(flowFileUuid, other.flowFileUuid)
//      && Objects.equals(eventType, other.eventType)
//      && Objects.equals(eventKey, other.eventKey)
//      && Objects.equals(payload, other.payload)
//      && Objects.equals(sessionId, other.sessionId);
//    }
//
//    @Override
//    public String toString() {
//      return this.getEventKey();
//    }
//  }
//}