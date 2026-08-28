# IBM MQ Instrumentation

Adds IBM MQ's Queue Manager Identifier (QMID) to messaging spans, as the opt-in attribute
`messaging.ibmmq.queue_manager.id`. Queue manager *names* are not globally unique across hosts or
customers, so a monitoring backend cannot use them to reliably join an application's spans to the
queue manager infrastructure it talked to; QMID is IBM's own globally-unique identifier for the queue
manager, generated at creation time.

## Supported libraries

- IBM MQ javax JMS provider: `com.ibm.mq:com.ibm.mq.allclient` 9.0.4.0+
- IBM MQ jakarta JMS provider: `com.ibm.mq:com.ibm.mq.jakarta.client` 9.3.0.0+

The javax and jakarta providers are handled by two independent `InstrumentationModule`s
(`IbmMqInstrumentationModule` / `IbmMqJakartaInstrumentationModule`) sharing the primary
instrumentation name `ibmmq`, so configuration (including the opt-in flag below) applies to both.
They are kept separate because the two client jars are mutually exclusive artifacts whose type
references must never land in the same muzzle reference set.

## Opt-in

The QMID attribute is disabled by default. Enable it with:

```
-Dotel.instrumentation.ibmmq.experimental-span-attributes=true
```

## How it works

Applications using `javax.jms`/`jakarta.jms` already get a messaging span from the generic JMS
instrumentation (`messaging.system=jms`). This module additively enriches that span -- it never
creates, ends, or otherwise alters it:

- **Producer**: the QMID is read directly off the producer/connection's already-cached, resolved
  connection property (populated locally by IBM's client during `MQCONN`) -- a local `Map` lookup,
  not an `MQINQ` round trip -- and added to the producer span.
- **Asynchronous consumer via `setMessageListener`**: the QMID-bearing consumer is associated with
  the registered listener at registration time, then re-read fresh and added to the `onMessage`
  process span on every delivery (never cached across deliveries, since an automatic client
  reconnect can resolve to a different queue manager).
- **Consumers driven by `receive()` + direct listener invocation, without ever calling
  `setMessageListener`** (e.g. Spring's default `JmsListenerContainerFactory`, which polls with
  `receive()` and hands the message straight to the listener): the QMID is captured at `receive()`
  exit and carried forward on the returned message, so the `onMessage` process span can still be
  enriched even though no `setMessageListener` registration ever happened. This never touches the
  synchronous `receive()` call's own span, which remains unreachable by design -- the generic JMS
  instrumentation creates and ends that span in one call, never making it current, so no advice can
  write to it.

## Captured attributes

- `messaging.ibmmq.queue_manager.id` -- the QMID, trimmed (IBM's `MQCA_Q_MGR_IDENTIFIER` is a fixed
  48-byte, space-padded field). Opt-in; absent unless the flag above is set.
