package com.datadog.trace

import com.datadoghq.trace.DDSpan
import com.datadoghq.trace.DDSpanContext

class SpanFactory {
    static def newSpanOf(long timestampMicro) {
        def context = new DDSpanContext(
                1L,
                1L,
                0L,
                "fakeService",
                "fakeOperation",
                "fakeResource",
                Collections.emptyMap(),
                false,
                "fakeType",
                Collections.emptyMap(),
                null,
                null);
        return new DDSpan(timestampMicro, context)
    }
}
