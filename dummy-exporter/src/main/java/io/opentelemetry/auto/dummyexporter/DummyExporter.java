package io.opentelemetry.auto.dummyexporter;

import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.List;

public class DummyExporter implements SpanExporter {
  private final String prefix;

  public DummyExporter(final String prefix) {
    this.prefix = prefix;
  }

  @Override
  public ResultCode export(final List<SpanData> list) {
    for (final SpanData span : list) {
      System.out.println(prefix + " " + span.getName() + " " + span.getResource().toString());
    }
    return ResultCode.SUCCESS;
  }

  @Override
  public void shutdown() {}
}
