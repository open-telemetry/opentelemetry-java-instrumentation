/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

  private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<LogEvent>());

  public ListAppender() {
    super("ListAppender", null, null, true);
  }

  public List<LogEvent> getEvents() {
    return events;
  }

  public void clearEvents() {
    events.clear();
  }

  @Override
  public void append(LogEvent logEvent) {
    events.add(logEvent);
  }

  @PluginFactory
  public static ListAppender createAppender(@PluginAttribute("name") String name) {
    if (!name.equals("ListAppender")) {
      throw new IllegalArgumentException(
          "Use name=\"ListAppender\" in log4j2-test.xml instead of " + name);
    }
    return INSTANCE;
  }
}
