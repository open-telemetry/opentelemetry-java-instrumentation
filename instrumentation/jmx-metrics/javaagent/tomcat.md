# Tomcat Metrics

Here is the list of metrics based on MBeans exposed by Tomcat.

| Metric Name                       | Type          | Attributes                 | Description                                                     |
|-----------------------------------|---------------|----------------------------|-----------------------------------------------------------------|
| tomcat.sessions.activeSessions    | UpDownCounter | context                    | The number of active sessions                                   |
| tomcat.errorCount                 | Gauge         | name                       | The number of errors per second on all request processors       |
| tomcat.requestCount               | Gauge         | name                       | The number of requests per second across all request processors |
| tomcat.maxTime                    | Gauge         | name                       | The longest request processing time                             |
| tomcat.processingTime             | Counter       | name                       | Represents the total time for processing all requests           |
| tomcat.network.io                 | Counter       | name, network.io.direction | The number of bytes transmitted                                 |
| tomcat.threads.currentThreadCount | UpDownCounter | name                       | Total thread count of the thread pool                           |
| tomcat.threads.currentThreadsBusy | UpDownCounter | name                       | Busy thread count of the thread pool                            |
