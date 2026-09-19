Prevents Kafka instrumentation from creating duplicate process spans for records consumed through Camel when stable messaging semantic conventions are enabled.

Camel claims each Kafka batch before iteration and moves consumed-message accounting to the Camel message. Kafka consumer calls made inside a route remain independently instrumented. Legacy messaging behavior does not change.
