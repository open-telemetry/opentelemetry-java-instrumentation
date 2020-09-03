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

package io.opentelemetry.instrumentation.logback.v1_0_0;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import java.util.Map;
import org.slf4j.Marker;

final class LoggingEventWrapper implements ILoggingEvent {
  private final ILoggingEvent event;
  private final Map<String, String> mdcPropertyMap;
  private final LoggerContextVO vo;

  LoggingEventWrapper(ILoggingEvent event, Map<String, String> mdcPropertyMap) {
    this.event = event;
    this.mdcPropertyMap = mdcPropertyMap;

    final LoggerContextVO oldVo = event.getLoggerContextVO();
    if (oldVo != null) {
      vo = new LoggerContextVO(oldVo.getName(), mdcPropertyMap, oldVo.getBirthTime());
    } else {
      vo = null;
    }
  }

  @Override
  public Object[] getArgumentArray() {
    return event.getArgumentArray();
  }

  @Override
  public Level getLevel() {
    return event.getLevel();
  }

  @Override
  public String getLoggerName() {
    return event.getLoggerName();
  }

  @Override
  public String getThreadName() {
    return event.getThreadName();
  }

  @Override
  public IThrowableProxy getThrowableProxy() {
    return event.getThrowableProxy();
  }

  @Override
  public void prepareForDeferredProcessing() {
    event.prepareForDeferredProcessing();
  }

  @Override
  public LoggerContextVO getLoggerContextVO() {
    return vo;
  }

  @Override
  public String getMessage() {
    return event.getMessage();
  }

  @Override
  public long getTimeStamp() {
    return event.getTimeStamp();
  }

  @Override
  public StackTraceElement[] getCallerData() {
    return event.getCallerData();
  }

  @Override
  public boolean hasCallerData() {
    return event.hasCallerData();
  }

  @Override
  public Marker getMarker() {
    return event.getMarker();
  }

  @Override
  public String getFormattedMessage() {
    return event.getFormattedMessage();
  }

  @Override
  public Map<String, String> getMDCPropertyMap() {
    return mdcPropertyMap;
  }

  /**
   * A synonym for {@link #getMDCPropertyMap}.
   *
   * @deprecated Use {@link #getMDCPropertyMap()}.
   */
  @Override
  @Deprecated
  public Map<String, String> getMdc() {
    return event.getMDCPropertyMap();
  }

  @Override
  public String toString() {
    return event.toString();
  }
}
