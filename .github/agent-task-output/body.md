Attach receive context and consumed-message accounting to each physical JMS message instead of passing mutable state through the current `Context`.

Treat reused messages and Camel wrappers as distinct deliveries, so JMS 1.1, JMS 3.0, Spring JMS, and Camel JMS count each delivery once while preserving suppression for nested synchronous processing. Clean up delivery state when listener setup fails.
