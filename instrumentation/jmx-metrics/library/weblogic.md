# WebLogic Metrics

Here is the list of metrics based on MBeans exposed by Oracle WebLogic Server (tested with WebLogic Server 14.1.1 on JDK 8 and 11, 14.1.2 on JDK 17 and 21, and 15.1.1 on JDK 17 and 21).

## Prerequisites

WebLogic Server must have `PlatformMBeanServerUsed` enabled so that `com.bea:*` runtime MBeans are registered on the JVM platform `MBeanServer` (this is `true` by default for WebLogic Server 10.3.3.0 and later domains).

If you also pass `-Dcom.sun.management.jmxremote` to the JVM, add `-Djavax.management.builder.initial=weblogic.management.jmx.mbeanserver.WLSMBeanServerBuilder` so WebLogic initializes the platform `MBeanServer` with its security hooks intact.

## Metrics

| Metric Name                              | Type          | Unit          | Attributes                                                   | Description                                                               |
| ---------------------------------------- | ------------- | ------------- | ------------------------------------------------------------ | ------------------------------------------------------------------------- |
| weblogic.thread.count                    | UpDownCounter | {thread}      |                                                              | The total number of threads in the pool.                                  |
| weblogic.thread.idle.count               | UpDownCounter | {thread}      |                                                              | The number of idle threads in the pool.                                   |
| weblogic.thread.standby.count            | UpDownCounter | {thread}      |                                                              | The number of threads in the standby pool.                                |
| weblogic.thread.stuck.count              | UpDownCounter | {thread}      |                                                              | Number of stuck threads in the thread pool.                               |
| weblogic.thread.queue.size               | UpDownCounter | {request}     |                                                              | The number of pending requests in the priority queue.                     |
| weblogic.session.active.count            | UpDownCounter | {session}     | weblogic.context                                             | The number of currently active sessions.                                  |
| weblogic.session.created                 | Counter       | {session}     | weblogic.context                                             | The number of sessions opened.                                            |
| weblogic.request.count                   | Counter       | {request}     | weblogic.application, weblogic.context, weblogic.servlet     | The number of times the servlet has been invoked.                         |
| weblogic.request.duration.sum            | Counter       | s             | weblogic.application, weblogic.context, weblogic.servlet     | The total amount of time spent executing servlet requests.                |
| weblogic.request.duration.max            | Gauge         | s             | weblogic.application, weblogic.context, weblogic.servlet     | The longest servlet request execution time.                               |
| weblogic.network.io                      | Counter       | By            | weblogic.channel, network.io.direction                       | Total number of bytes transferred on the channel.                         |
| weblogic.db.client.connection.count      | UpDownCounter | {connection}  | db.client.connection.pool.name, db.client.connection.state   | The number of open physical database connections.                         |
| weblogic.db.client.connection.wait.count | Counter       | {request}     | db.client.connection.pool.name                               | The number of connection requests that had to wait to obtain a connection. |
| weblogic.transaction.count               | UpDownCounter | {transaction} |                                                              | The number of active transactions.                                        |
| weblogic.transaction.completed           | Counter       | {transaction} |                                                              | The total number of transactions processed.                               |
| weblogic.transaction.committed           | Counter       | {transaction} |                                                              | The total number of transactions committed.                               |
| weblogic.transaction.rollback            | Counter       | {transaction} | weblogic.rollback.cause                                      | The total number of transactions rolled back.                             |
| weblogic.transaction.abandoned           | Counter       | {transaction} |                                                              | The total number of transactions abandoned.                               |
