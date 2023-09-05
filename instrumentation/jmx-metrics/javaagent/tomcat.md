# Tomcat Metrics

Here is the list of metrics based on MBeans exposed by Tomcat.

| Metric Name                                | Type          | Attributes      | Description                                                     |
| ------------------------------------------ | ------------- | --------------- | --------------------------------------------------------------- |
| http.server.tomcat.sessions.activeSessions | UpDownCounter | context         | The number of active sessions                                   |
| http.server.tomcat.errorCount              | Gauge         | name            | The number of errors per second on all request processors       |
| http.server.tomcat.requestCount            | Gauge         | name            | The number of requests per second across all request processors |
| http.server.tomcat.maxTime                 | Gauge         | name            | The longest request processing time                             |
| http.server.tomcat.processingTime          | Counter       | name            | Represents the total time for processing all requests           |
| http.server.tomcat.traffic                 | Counter       | name, direction | The number of bytes transmitted                                 |
| http.server.tomcat.threads                 | UpDownCounter | name, state     | Thread Count of the Thread Pool                                 |
