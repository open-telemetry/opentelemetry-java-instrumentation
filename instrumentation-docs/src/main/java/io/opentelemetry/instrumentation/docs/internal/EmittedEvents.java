/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Representation of events emitted by an instrumentation. Includes context about whether emitted by
 * default or via a configuration option. This class is internal and is hence not for public use.
 * Its APIs are unstable and can change at any time.
 */
public class EmittedEvents {
  // Condition in which the telemetry is emitted (ex: default, or configuration option names).
  private String when;

  @JsonProperty("events_by_scope")
  private List<EventsByScope> eventsByScope;

  public EmittedEvents() {
    this.when = "";
    this.eventsByScope = new ArrayList<>();
  }

  public EmittedEvents(String when, List<EventsByScope> eventsByScope) {
    this.when = when;
    this.eventsByScope = copyList(eventsByScope);
  }

  public String getWhen() {
    return when;
  }

  public void setWhen(String when) {
    this.when = when;
  }

  @JsonProperty("events_by_scope")
  public List<EventsByScope> getEventsByScope() {
    return eventsByScope;
  }

  @JsonProperty("events_by_scope")
  public void setEventsByScope(List<EventsByScope> events) {
    this.eventsByScope = copyList(events);
  }

  private static <T> List<T> copyList(@Nullable List<T> values) {
    return values == null ? new ArrayList<>() : new ArrayList<>(values);
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class EventsByScope {
    private String scope;
    private List<Event> events;

    public EventsByScope(String scopeName, List<Event> events) {
      this.scope = scopeName;
      this.events = copyList(events);
    }

    public EventsByScope() {
      this.scope = "";
      this.events = new ArrayList<>();
    }

    public String getScope() {
      return scope;
    }

    public void setScope(String scope) {
      this.scope = scope;
    }

    public List<Event> getEvents() {
      return events;
    }

    public void setEvents(List<Event> events) {
      this.events = copyList(events);
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Event {
    private String name;

    @Nullable private String severity;

    private List<TelemetryAttribute> attributes;

    public Event(String name, @Nullable String severity, List<TelemetryAttribute> attributes) {
      this.name = name;
      this.severity = severity;
      this.attributes = copyList(attributes);
    }

    public Event() {
      this.name = "";
      this.attributes = new ArrayList<>();
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    @Nullable
    public String getSeverity() {
      return severity;
    }

    public void setSeverity(@Nullable String severity) {
      this.severity = severity;
    }

    public List<TelemetryAttribute> getAttributes() {
      return attributes;
    }

    public void setAttributes(List<TelemetryAttribute> attributes) {
      this.attributes = copyList(attributes);
    }
  }
}
