# Wildfly Metrics

Here is the list of metrics based on MBeans exposed by Wildfly.

| Metric Name                                        | Type          | Attributes                           | Description                                                             |
|----------------------------------------------------|---------------|--------------------------------------|-------------------------------------------------------------------------|
| wildfly.network.io                                 | Counter       | wildfly.server, network.io.direction | Total number of bytes transferred                                       |
| wildfly.error.count                                | Counter       | wildfly.server, wildfly.listener     | The number of requests that have resulted in a 5xx response             |
| wildfly.request.count                              | Counter       | wildfly.server,wildfly.listener      | The number of requests this listener has served                         |
| wildfly.request.duration.sum                       | Counter       | wildfly.server,wildfly.listener      | The total amount of time spent processing requests                      |
| wildfly.session.expired                            | Counter       | wildfly.deployment                   | The number of expired sessions                                          |
| wildfly.session.rejected                           | Counter       | wildfly.deployment                   | The number of rejected sessions                                         |
| wildfly.session.created                            | Counter       | wildfly.deployment                   | The number of sessions created                                          |
| wildfly.session.count                              | UpDownCounter | wildfly.deployment                   | The number of active sessions                                           |
| wildfly.db.client.connections.usage                | Gauge         | data_source, state                   | The number of open jdbc connections                                     |
| wildfly.db.client.connections.WaitCount            | Counter       | data_source                          | The number of requests that had to wait to obtain a physical connection |
| wildfly.db.client.rollback.count                   | Counter       | cause                                | The total number of transactions rolled back                            |
| wildfly.db.client.transaction.NumberOfTransactions | Counter       |                                      | The total number of transactions (top-level and nested) created         |
