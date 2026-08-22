# IBM MQ Instrumentation

This instrumentation enables tracing of IBM MQ message producers and captures the Queue Manager ID for distributed trace correlation.

## Supported libraries

- IBM MQ: 9.x+

## How it works

The instrumentation hooks into the IBM MQ client to capture the Queue Manager Identifier (QMID) on the first connection and attaches it to all subsequent message send operations. This allows correlating messages across different queue managers in a distributed MQ deployment.

### VirtualField Caching

The QMID is retrieved via a single `MQINQ` (Queue Manager Inquire) operation with selector `2016` (`MQCA_Q_MGR_IDENTIFIER`) on the first connection and cached in a `VirtualField` to avoid repeated expensive network calls.

### Captured Attributes

- `messaging.ibmmq.queue_manager.id` - The 48-byte Queue Manager Identifier
- `messaging.destination` - The queue name
- `messaging.system` - Always set to "ibm_mq"
