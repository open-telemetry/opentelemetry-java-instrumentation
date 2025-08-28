# Auto-instrumentation for NATS version 2.17

Provides OpenTelemetry auto-instrumentation for [NATS 2.17](https://github.com/nats-io/nats.java).

### Trace propagation

It's recommended to provide `Message` with a writable `Header` structure
to allow propagation between publishers and subscribers. Without headers,
the tracing context will not be propagated in the headers.

```java
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;

// don't
Message msg = NatsMessage.builder().subject("sub").build();

// do
Message msg = NatsMessage.builder().subject("sub").headers(new Headers()).build();
```
