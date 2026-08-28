# Tomcat Metrics

Here is the list of metrics based on MBeans exposed by Tomcat.

| Metric Name                            | Type          | Unit         | Attributes                                                 | Description                                                |
| -------------------------------------- | ------------- | ------------ | ---------------------------------------------------------- | ---------------------------------------------------------- |
| tomcat.session.active.count            | UpDownCounter | {session}    | tomcat.context                                             | The number of currently active sessions.                   |
| tomcat.session.active.limit            | UpDownCounter | {session}    | tomcat.context                                             | Maximum possible number of active sessions.                |
| tomcat.session.duration.max            | Gauge         | s            | tomcat.context                                             | The maximum observed session lifetime.                     |
| tomcat.session.created                 | Counter       | {session}    | tomcat.context                                             | The number of sessions created.                            |
| tomcat.session.duration.mean           | Gauge         | s            | tomcat.context                                             | The average observed session lifetime.                     |
| tomcat.session.processing.duration.sum | Counter       | s            | tomcat.context                                             | The total time spent processing sessions.                  |
| tomcat.session.active.max              | Gauge         | {session}    | tomcat.context                                             | The maximum number of concurrent active sessions observed. |
| tomcat.session.expired                 | Counter       | {session}    | tomcat.context                                             | The number of expired sessions.                            |
| tomcat.session.rejected                | Counter       | {session}    | tomcat.context                                             | The number of rejected sessions.                           |
| tomcat.db.client.connection.initial    | Gauge         | {connection} | db.client.connection.pool.name                             | The configured initial size of the JDBC connection pool.   |
| tomcat.db.client.connection.count      | UpDownCounter | {connection} | db.client.connection.pool.name, db.client.connection.state | The number of active JDBC connections.                     |
| tomcat.db.client.connection.limit      | Gauge         | {connection} | db.client.connection.pool.name                             | The configured maximum size of the JDBC connection pool.   |
| tomcat.error.count                     | Counter       | {error}      | tomcat.request.processor.name                              | The number of errors.                                      |
| tomcat.request.count                   | Counter       | {request}    | tomcat.request.processor.name                              | The number of requests processed.                          |
| tomcat.request.duration.max            | Gauge         | s            | tomcat.request.processor.name                              | The longest request processing time.                       |
| tomcat.request.duration.sum            | Counter       | s            | tomcat.request.processor.name                              | Total time of processing all requests.                     |
| tomcat.network.io                      | Counter       | By           | tomcat.request.processor.name, network.io.direction        | The number of bytes transmitted.                           |
| tomcat.thread.count                    | UpDownCounter | {thread}     | tomcat.thread.pool.name                                    | Total thread count of the thread pool.                     |
| tomcat.thread.limit                    | UpDownCounter | {thread}     | tomcat.thread.pool.name                                    | Maximum possible number of threads in the thread pool.     |
| tomcat.thread.busy.count               | UpDownCounter | {thread}     | tomcat.thread.pool.name                                    | Number of busy threads in the thread pool.                 |
