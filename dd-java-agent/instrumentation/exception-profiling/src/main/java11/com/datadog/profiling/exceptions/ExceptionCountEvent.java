package com.datadog.profiling.exceptions;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;

@Name("datadog.ExceptionCount")
@Label("ExceptionCount")
@Description("Datadog exception count event.")
@Category("Datadog")
@Period(value = "endChunk")
@StackTrace(false)
@Enabled
public class ExceptionCountEvent extends Event {
  @Label("Exception type")
  private String type;

  @Label("Exception count")
  private long count;

  public ExceptionCountEvent(String type, long count) {
    this.type = type;
    this.count = count;
  }
}
