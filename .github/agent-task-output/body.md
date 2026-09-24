Ensure each JMS delivery produces one Process span and one process-duration measurement even when javax or Jakarta JMS, Spring JMS, Camel, and SJMS observe the same listener callback.

Attach receive context and lifecycle state to the message and carry them across framework handoffs. The actual listener invocation owns processing telemetry, while distinct nested deliveries retain independent parentage and reused messages begin fresh lifecycles. Listener setup failures also release temporary processing state.

Add Camel SJMS support and preserve consumed-message metrics for each instrumentation layer, including expected cross-layer duplication.
