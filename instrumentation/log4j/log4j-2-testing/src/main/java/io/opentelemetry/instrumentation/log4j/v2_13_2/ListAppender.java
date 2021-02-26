/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

@Plugin(
    name = "ListAppender",
    category = "Core",
    elementType = Appender.ELEMENT_TYPE,
    printObject = true)
public class ListAppender extends AbstractAppender {

  public static ListAppender get() {
    return INSTANCE;
  }

  private static final ListAppender INSTANCE = new ListAppender();

  private final List<LoggedEvent> events =
      Collections.synchronizedList(new ArrayList<LoggedEvent>());

  public ListAppender() {
    super("ListAppender", null, null, true);
  }

  public List<LoggedEvent> getEvents() {
    return events;
  }

  public void clearEvents() {
    events.clear();
  }

  @Override
  public void append(LogEvent logEvent) {
    // Event object may be reused by the framework so copy the data we need.
    LoggedEvent copied =
        new LoggedEvent(
            logEvent.getMessage().getFormattedMessage(),
            new HashMap<>(logEvent.getContextData().toMap()));
    events.add(copied);
  }

  @PluginFactory
  public static ListAppender createAppender(@PluginAttribute("name") String name) {
    if (!name.equals("ListAppender")) {
      throw new IllegalArgumentException(
          "Use name=\"ListAppender\" in log4j2-test.xml instead of " + name);
    }
    return INSTANCE;
  }

  public static class LoggedEvent {
    private final String message;
    private final Map<String, String> contextData;

    LoggedEvent(String message, Map<String, String> contextData) {
      this.message = message;
      this.contextData = contextData;
    }

    public String getMessage() {
      return message;
    }

    public Map<String, String> getContextData() {
      return contextData;
    }
  }
}
