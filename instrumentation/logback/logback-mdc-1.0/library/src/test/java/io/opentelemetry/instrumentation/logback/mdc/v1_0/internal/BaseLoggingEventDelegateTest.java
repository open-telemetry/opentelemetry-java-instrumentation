package io.opentelemetry.instrumentation.logback.mdc.v1_0.internal;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Marker;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaseLoggingEventDelegateTest {
  ILoggingEvent event = mock(ILoggingEvent.class);
  BaseLoggingEventDelegate delegate = new WrappedEvent(event);

  @BeforeEach
  void beforeEach() {
    Mockito.reset(event);
  }

  @Test
  void testGetThreadName() {
    when(event.getThreadName()).thenReturn("testThread");

    assertEquals("testThread", delegate.getThreadName());
    verify(event).getThreadName();
  }

  @Test
  void testGetLevel() {
    when(event.getLevel()).thenReturn(Level.INFO);

    assertEquals(Level.INFO, delegate.getLevel());
    verify(event).getLevel();
  }

  @Test
  void testGetMessage() {
    when(event.getMessage()).thenReturn("testMessage");

    assertEquals("testMessage", delegate.getMessage());
    verify(event).getMessage();
  }

  @Test
  void testGetFormattedMessage() {
    when(event.getFormattedMessage()).thenReturn("formattedTestMessage");

    assertEquals("formattedTestMessage", delegate.getFormattedMessage());
    verify(event).getFormattedMessage();
  }

  @Test
  void testGetLoggerName() {
    when(event.getLoggerName()).thenReturn("testLogger");

    assertEquals("testLogger", delegate.getLoggerName());
    verify(event).getLoggerName();
  }

  @Test
  void testGetTimeStamp() {
    ILoggingEvent event = mock(ILoggingEvent.class);
    when(event.getTimeStamp()).thenReturn(123456L);

    BaseLoggingEventDelegate delegate = new WrappedEvent(event);

    assertEquals(123456L, delegate.getTimeStamp());
    verify(event).getTimeStamp();
  }

  @Test
  void testGetLoggerContextVO() {
    LoggerContextVO loggerContextVO = mock(LoggerContextVO.class);
    when(event.getLoggerContextVO()).thenReturn(loggerContextVO);

    assertEquals(loggerContextVO, delegate.getLoggerContextVO());
    verify(event).getLoggerContextVO();
  }

  @Test
  void testGetArgumentArray() {
    Object[] argumentArray = new Object[] {"arg1", "arg2"};
    when(event.getArgumentArray()).thenReturn(argumentArray);

    assertSame(argumentArray, delegate.getArgumentArray());
    verify(event).getArgumentArray();
  }

  @Test
  void testGetThrowableProxy() {
    IThrowableProxy throwableProxy = mock(IThrowableProxy.class);
    when(event.getThrowableProxy()).thenReturn(throwableProxy);

    assertSame(throwableProxy, delegate.getThrowableProxy());
    verify(event).getThrowableProxy();
  }

  @Test
  void testGetCallerData() {
    StackTraceElement[] callerData = new StackTraceElement[] {};
    when(event.getCallerData()).thenReturn(callerData);

    assertSame(callerData, delegate.getCallerData());
    verify(event).getCallerData();
  }

  @Test
  void testGetMarker() {
    Marker marker = mock(Marker.class);
    when(event.getMarker()).thenReturn(marker);

    assertSame(marker, delegate.getMarker());
    verify(event).getMarker();
  }

  @Test
  void testGetMDCPropertyMap() {
    Map<String, String> mdcPropertyMap = new HashMap<>();
    when(event.getMDCPropertyMap()).thenReturn(mdcPropertyMap);

    assertSame(mdcPropertyMap, delegate.getMDCPropertyMap());
    verify(event).getMDCPropertyMap();
  }

  @Test
  void testHasCallerData() {
    when(event.hasCallerData()).thenReturn(true);

    assertTrue(delegate.hasCallerData());
    verify(event).hasCallerData();
  }

  @Test
  void testPrepareForDeferredProcessing() {
    delegate.prepareForDeferredProcessing();

    verify(event).prepareForDeferredProcessing();
  }

  private static class WrappedEvent extends BaseLoggingEventDelegate {
    public WrappedEvent(ILoggingEvent event) {
      super(event);
    }
  }
}
