package io.opentelemetry.auto.exporters.dummyexporter;

import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.trace.AttributeValue;
import java.util.List;
import java.util.Map;

public class DummyExporter implements SpanExporter {
  private final String prefix;

  public DummyExporter(final String prefix) {
    this.prefix = prefix;
  }

  @Override
  public ResultCode export(final List<SpanData> list) {
    for (final SpanData span : list) {
      System.out.print(
          prefix + " " + span.getName() + " " + span.getSpanId().toLowerBase16() + " ");
      for (final Map.Entry<String, AttributeValue> attr : span.getAttributes().entrySet()) {
        System.out.print(attr.getKey() + "=");
        final AttributeValue value = attr.getValue();
        switch (value.getType()) {
          case STRING:
            System.out.print('"' + value.getStringValue() + '"');
            break;
          case BOOLEAN:
            System.out.print(value.getBooleanValue());
            break;
          case LONG:
            System.out.print(value.getLongValue());
            break;
          case DOUBLE:
            System.out.print(value.getDoubleValue());
            break;
        }
        System.out.print(" ");
      }
    }
    System.out.println();
    return ResultCode.SUCCESS;
  }

  @Override
  public void shutdown() {}
}
