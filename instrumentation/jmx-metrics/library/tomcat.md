# Tomcat Metrics

Here is the list of metrics based on MBeans exposed by Tomcat.

| Metric Name                 | Type          | Attributes                                          | Description                                            |
|-----------------------------|---------------|-----------------------------------------------------|--------------------------------------------------------|
| tomcat.session.active.count | UpDownCounter | tomcat.context                                      | The number of currently active sessions.               |
| tomcat.session.active.limit | UpDownCounter | tomcat.context                                      | Maximum possible number of active sessions.            |
| tomcat.error.count          | Counter       | tomcat.request.processor.name                       | The number of errors.                                  |
| tomcat.request.count        | Counter       | tomcat.request.processor.name                       | The number of requests processed.                      |
| tomcat.request.duration.max | Gauge         | tomcat.request.processor.name                       | The longest request processing time.                   |
| tomcat.request.duration.sum | Counter       | tomcat.request.processor.name                       | Total time of processing all requests.                 |
| tomcat.network.io           | Counter       | tomcat.request.processor.name, network.io.direction | The number of bytes transmitted.                       |
| tomcat.thread.count         | UpDownCounter | tomcat.thread.pool.name                             | Total thread count of the thread pool.                 |
| tomcat.thread.limit         | UpDownCounter | tomcat.thread.pool.name                             | Maximum possible number of threads in the thread pool. |
| tomcat.thread.busy.count    | UpDownCounter | tomcat.thread.pool.name                             | Number of busy threads in the thread pool.             |
