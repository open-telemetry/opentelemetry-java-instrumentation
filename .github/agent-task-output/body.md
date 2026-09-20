Attaches JMS receive parent context and consumed-message accounting directly to each physical JMS message, removing the mutable ambient handoff across listeners and routes.

Camel JMS transfers only the delivery-accounting state when it wraps or refills messages. This prevents duplicate consumed-message counts while preserving ordinary `Context`-based suppression for nested synchronous operations across JMS 1.1, JMS 3.0, Spring JMS, and Camel JMS.
