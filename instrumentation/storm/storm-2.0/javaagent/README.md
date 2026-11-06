# Apache Storm Instrumentation

This instrumentation enables messaging spans for Apache Storm spouts and bolts. A producer span is
created whenever a tuple is emitted through a spout or bolt output collector, and a consumer span is
created whenever a bolt processes a tuple.

The instrumentation propagates context through the tuple itself, using the tuple's in-memory
identity. As a result, context propagation works within a single worker; tuples that are serialized
and sent to a different worker start a new trace on the receiving side. Storm's internal streams
(ack, tick and metrics tuples, whose names start with `__`) are not instrumented.
