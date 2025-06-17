# Jetty Metrics

Here is the list of metrics based on MBeans exposed by Jetty.

| Metric Name             | Type          | Attributes | Description                               |
|-------------------------|---------------|------------|-------------------------------------------|
| jetty.thread.count      | UpDownCounter |            | The current number of threads             |
| jetty.thread.limit      | UpDownCounter |            | The maximum number of threads in the pool |
| jetty.thread.busy.count | UpDownCounter |            | The current number of busy threads        |
| jetty.thread.idle.count | UpDownCounter |            | The current number of idle threads        |
| jetty.thread.queue.size | UpDownCounter |            | The current job queue size                |
