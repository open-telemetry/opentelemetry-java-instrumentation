# Tomcat Metrics

Here is the list of metrics based on MBeans exposed by Tomcat.

| Metric Name                 | Type          | Unit      | Attributes                                          | Description                                            |
|-----------------------------|---------------|-----------|-----------------------------------------------------|--------------------------------------------------------|
| tomcat.session.active.count | UpDownCounter | {session} | tomcat.context                                      | The number of currently active sessions.               |
| tomcat.session.active.limit | UpDownCounter | {session} | tomcat.context                                      | Maximum possible number of active sessions.            |
| tomcat.error.count          | Counter       | {error}   | tomcat.request.processor.name                       | The number of errors.                                  |
| tomcat.request.count        | Counter       | {request} | tomcat.request.processor.name                       | The number of requests processed.                      |
| tomcat.request.duration.max | Gauge         | s         | tomcat.request.processor.name                       | The longest request processing time.                   |
| tomcat.request.duration.sum | Counter       | s         | tomcat.request.processor.name                       | Total time of processing all requests.                 |
| tomcat.network.io           | Counter       | By        | tomcat.request.processor.name, network.io.direction | The number of bytes transmitted.                       |
| tomcat.thread.count         | UpDownCounter | {thread}  | tomcat.thread.pool.name                             | Total thread count of the thread pool.                 |
| tomcat.thread.limit         | UpDownCounter | {thread}  | tomcat.thread.pool.name                             | Maximum possible number of threads in the thread pool. |
| tomcat.thread.busy.count    | UpDownCounter | {thread}  | tomcat.thread.pool.name                             | Number of busy threads in the thread pool.             |
