# Jetty Metrics

Here is the list of metrics based on MBeans exposed by Jetty.

| Metric Name             | Type          | Attributes                                 | Description                               |
|-------------------------|---------------|--------------------------------------------|-------------------------------------------|
| jetty.thread.count      | UpDownCounter | jetty.thread.pool.id                       | The current number of threads             |
| jetty.thread.limit      | UpDownCounter | jetty.thread.pool.id                       | The maximum number of threads in the pool |
| jetty.thread.busy.count | UpDownCounter | jetty.thread.pool.id                       | The current number of busy threads        |
| jetty.thread.idle.count | UpDownCounter | jetty.thread.pool.id                       | The current number of idle threads        |
| jetty.thread.queue.size | UpDownCounter | jetty.thread.pool.id                       | The current job queue size                |
| jetty.io.select.count   | Counter       | jetty.selector.resource, jetty.selector.id | The number of select calls                |
