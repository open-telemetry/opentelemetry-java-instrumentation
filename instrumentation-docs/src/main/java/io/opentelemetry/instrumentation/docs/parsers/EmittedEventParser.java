/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static io.opentelemetry.instrumentation.docs.parsers.TelemetryParser.normalizeWhenCondition;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.instrumentation.docs.internal.EmittedEvents;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * This class is responsible for parsing events-* files from the `.telemetry` directory of an
 * instrumentation module and converting them into the {@link EmittedEvents} format.
 */
public class EmittedEventParser {
  private static final Logger logger = Logger.getLogger(EmittedEventParser.class.getName());

  /**
   * Looks for event files in the .telemetry directory, and combines them into a single map.
   *
   * @param instrumentationDirectory the directory to traverse
   * @return contents of aggregated files
   */
  public static Map<String, EmittedEvents> getEventsByScopeFromFiles(
      Path rootDir, String instrumentationDirectory) throws JsonProcessingException {
    Map<String, StringBuilder> eventsByScope = new HashMap<>();
    Path telemetryDir = rootDir.resolve(instrumentationDirectory).resolve(".telemetry");

    if (Files.exists(telemetryDir) && Files.isDirectory(telemetryDir)) {
      try (Stream<Path> files = Files.list(telemetryDir)) {
        files
            .filter(path -> path.getFileName().toString().startsWith("events-"))
            .forEach(
                path -> {
                  String content = FileManager.readFileToString(path);
                  if (content != null) {
                    String whenKey = normalizeWhenCondition(content);

                    eventsByScope.putIfAbsent(whenKey, new StringBuilder("events_by_scope:\n"));

                    // Skip the events_by_scope line so we can aggregate into one list
                    int eventIndex = content.indexOf("events_by_scope:\n");
                    if (eventIndex != -1) {
                      String contentAfter =
                          content.substring(eventIndex + "events_by_scope:\n".length());
                      eventsByScope.get(whenKey).append(contentAfter);
                    }
                  }
                });
      } catch (IOException e) {
        logger.severe(
            "Error reading event files from " + instrumentationDirectory + ": " + e.getMessage());
      }
    }

    return parseEvents(eventsByScope);
  }

  /**
   * Takes in a raw string representation of the aggregated EmittedEvents yaml map, separated by the
   * `when`, indicating the conditions under which the telemetry is emitted. deduplicates by name
   * and severity and then returns a new map.
   *
   * @param input raw string representation of EmittedEvents yaml
   * @return {@code Map<String, EmittedEvents>} where the key is the `when` condition
   */
  private static Map<String, EmittedEvents> parseEvents(Map<String, StringBuilder> input)
      throws JsonProcessingException {
    Map<String, EmittedEvents> result = new HashMap<>();

    for (Map.Entry<String, StringBuilder> entry : input.entrySet()) {
      String when = entry.getKey().strip();
      StringBuilder content = entry.getValue();

      EmittedEvents events = YamlHelper.emittedEventsParser(content.toString());
      if (events.getEventsByScope().isEmpty()) {
        continue;
      }

      Map<String, Map<EventKey, AggregatedEvent>> eventsByScopeAndKey = new HashMap<>();

      for (EmittedEvents.EventsByScope eventsByScopeEntry : events.getEventsByScope()) {
        Map<EventKey, AggregatedEvent> eventsByKey =
            eventsByScopeAndKey.computeIfAbsent(
                eventsByScopeEntry.getScope(), s -> new HashMap<>());

        for (EmittedEvents.Event event : eventsByScopeEntry.getEvents()) {
          AggregatedEvent aggregated =
              eventsByKey.computeIfAbsent(EventKey.of(event), k -> new AggregatedEvent());

          if (event.getAttributes() != null) {
            for (TelemetryAttribute attr : event.getAttributes()) {
              aggregated.attributes.add(new TelemetryAttribute(attr.getName(), attr.getType()));
            }
          }
        }
      }

      result.put(when, getEmittedEvents(eventsByScopeAndKey, when));
    }

    return result;
  }

  /**
   * Takes in a map of aggregated events by scope and event identity, and returns an {@link
   * EmittedEvents} object with deduplicated events.
   *
   * @param eventsByScopeAndKey the map of aggregated events by scope and event identity
   * @param when the condition under which the telemetry is emitted
   * @return an {@link EmittedEvents} object with deduplicated events
   */
  private static EmittedEvents getEmittedEvents(
      Map<String, Map<EventKey, AggregatedEvent>> eventsByScopeAndKey, String when) {
    List<EmittedEvents.EventsByScope> deduplicatedEventsByScope = new ArrayList<>();

    for (Map.Entry<String, Map<EventKey, AggregatedEvent>> scopeEntry :
        eventsByScopeAndKey.entrySet()) {
      deduplicatedEventsByScope.add(
          new EmittedEvents.EventsByScope(scopeEntry.getKey(), toEvents(scopeEntry.getValue())));
    }

    return new EmittedEvents(when, deduplicatedEventsByScope);
  }

  /**
   * Converts aggregated events into the {@link EmittedEvents.Event} representation, sorted by name
   * and severity so that the result does not depend on hash iteration order.
   */
  static List<EmittedEvents.Event> toEvents(Map<EventKey, AggregatedEvent> eventsByKey) {
    List<Map.Entry<EventKey, AggregatedEvent>> sortedEntries =
        new ArrayList<>(eventsByKey.entrySet());
    sortedEntries.sort(
        Comparator.<Map.Entry<EventKey, AggregatedEvent>, String>comparing(e -> e.getKey().name())
            .thenComparing(
                e -> e.getKey().severity(), Comparator.nullsFirst(Comparator.naturalOrder())));

    List<EmittedEvents.Event> events = new ArrayList<>();
    for (Map.Entry<EventKey, AggregatedEvent> event : sortedEntries) {
      events.add(
          new EmittedEvents.Event(
              event.getKey().name(),
              event.getKey().severity(),
              new ArrayList<>(event.getValue().attributes)));
    }
    return events;
  }

  /**
   * The identity of an event: its name together with its severity. A single instrumentation scope
   * can emit the same event name at more than one severity - the default {@code exception} event,
   * for example, is {@code ERROR} for server and consumer operations and {@code WARN} for client
   * and producer operations - and each of those is a distinct documented shape.
   */
  record EventKey(String name, @Nullable String severity) {
    static EventKey of(EmittedEvents.Event event) {
      return new EventKey(event.getName(), event.getSeverity());
    }
  }

  /** Accumulates the union of attributes seen for one event identity. */
  static class AggregatedEvent {
    final Set<TelemetryAttribute> attributes = new LinkedHashSet<>();
  }

  private EmittedEventParser() {}
}
