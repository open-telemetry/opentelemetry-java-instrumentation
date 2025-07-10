# Wildfly Metrics

Here is the list of metrics based on MBeans exposed by Wildfly.

| Metric Name                             | Type          | Attributes                                                 | Description                                                     |
|-----------------------------------------|---------------|------------------------------------------------------------|-----------------------------------------------------------------|
| wildfly.network.io                      | Counter       | wildfly.server, wildfly.listener, network.io.direction     | Total number of bytes transferred                               |
| wildfly.error.count                     | Counter       | wildfly.server, wildfly.listener                           | The number of requests that have resulted in a 5xx response     |
| wildfly.request.count                   | Counter       | wildfly.server, wildfly.listener                           | The number of requests served                                   |
| wildfly.request.duration.sum            | Counter       | wildfly.server, wildfly.listener                           | The total amount of time spent processing requests              |
| wildfly.session.expired                 | Counter       | wildfly.deployment                                         | The number of expired sessions                                  |
| wildfly.session.rejected                | Counter       | wildfly.deployment                                         | The number of rejected sessions                                 |
| wildfly.session.created                 | Counter       | wildfly.deployment                                         | The number of sessions created                                  |
| wildfly.session.active.count            | UpDownCounter | wildfly.deployment                                         | The number of active sessions                                   |
| wildfly.session.active.limit            | UpDownCounter | wildfly.deployment                                         | The maximum supported number of active sessions                 |
| wildfly.db.client.connection.count      | UpDownCounter | db.client.connection.pool.name, db.client.connection.state | The number of open physical database connections                |
| wildfly.db.client.connection.wait.count | Counter       | db.client.connection.pool.name                             | The number of connection requests that had to wait to obtain it |
| wildfly.transaction.count               | UpDownCounter |                                                            | The number of in-flight transactions                            |
| wildfly.transaction.created             | Counter       |                                                            | The total number of transactions created                        |
| wildfly.transaction.committed           | Counter       |                                                            | The total number of transactions committed                      |
| wildfly.transaction.rollback            | Counter       | wildfly.rollback.cause                                     | The total number of transactions rolled back                    |
