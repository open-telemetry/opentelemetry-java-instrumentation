# Jetty Metrics

Here is the list of metrics based on MBeans exposed by [Jetty](https://jetty.org/).

The metrics captured and their respective attributes depend on the Jetty version:
- [Jetty 12 and later](#jetty-12-and-later)
- [Jetty 9 to 11](#jetty-9-to-11)

## Jetty 12 and later

Those metrics require the following Jetty modules to be enabled : `jmx`, `http` and `statistics`.

| Metric Name             | Type          | Attributes                                 | Description                               |
|-------------------------|---------------|--------------------------------------------|-------------------------------------------|
| jetty.thread.count      | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The current number of threads             |
| jetty.thread.limit      | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The maximum number of threads in the pool |
| jetty.thread.busy.count | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The current number of busy threads        |
| jetty.thread.idle.count | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The current number of idle threads        |
| jetty.thread.queue.size | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The current job queue size                |
| jetty.io.select.count   | Counter       | jetty.selector.resource, jetty.selector.id | The number of select calls                |

## Jetty 9 to 11

Those metrics require the following Jetty modules to be enabled : `jmx`, `http` and `stats`.

| Metric Name             | Type          | Attributes                                 | Description                               |
|-------------------------|---------------|--------------------------------------------|-------------------------------------------|
| jetty.thread.count      | UpDownCounter | jetty.thread.pool.id                       | The current number of threads             |
| jetty.thread.limit      | UpDownCounter | jetty.thread.pool.id                       | The maximum number of threads in the pool |
| jetty.thread.busy.count | UpDownCounter | jetty.thread.pool.id                       | The current number of busy threads        |
| jetty.thread.idle.count | UpDownCounter | jetty.thread.pool.id                       | The current number of idle threads        |
| jetty.thread.queue.size | UpDownCounter | jetty.thread.pool.id                       | The current job queue size                |
| jetty.io.select.count   | Counter       | jetty.selector.resource, jetty.selector.id | The number of select calls                |
