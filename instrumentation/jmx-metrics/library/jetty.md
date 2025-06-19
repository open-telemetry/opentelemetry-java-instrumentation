# Jetty Metrics

Here is the list of metrics based on MBeans exposed by [Jetty](https://jetty.org/).

The metrics captured and their respective attributes depend on the Jetty version:
- [Jetty 12 and later](#jetty-12-and-later)
- [Jetty 9 to 11](#jetty-9-to-11)

## Jetty 12 and later

Those metrics require the following Jetty modules to be enabled : `jmx`, `http`, `statistics`, `sessions` and at least one of `ee8-deploy`, `ee9-deploy` or `ee10-deploy`.

| Metric Name             | Type          | Attributes                                 | Description                               |
|-------------------------|---------------|--------------------------------------------|-------------------------------------------|
| jetty.thread.count      | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The current number of threads             |
| jetty.thread.limit      | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The maximum number of threads in the pool |
| jetty.thread.busy.count | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The current number of busy threads        |
| jetty.thread.idle.count | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The current number of idle threads        |
| jetty.thread.queue.size | UpDownCounter | jetty.thread.pool.id, jetty.thread.context | The current job queue size                |
| jetty.io.select.count   | Counter       | jetty.selector.context, jetty.selector.id  | The number of select calls                |
| jetty.session.count     | UpDownCounter | jetty.context, jetty.session.cache.id      | Current number of active sessions         |
| jetty.session.count.max | Gauge         | jetty.context, jetty.session.cache.id      | Maximum number of active sessions         |

- `jetty.context` corresponds to the deployed application subfolder in `webapps` folder.
- `jetty.selector.context` is a technical string identifier, high cardinality with values like `HTTP_1_1@7674f035` but stable per Jetty process instance
- `jetty.selector.id` is a technical numeric identifier, usually with low cardinality between `0` and `9`.
- `jetty.session.cache.id` is a technical numeric identifier, usually single `0` value is used
- `jetty.thread.context` is a technical string identifier, high cardinality with values like `Server@5a411614` but stable per Jetty process instance
- `jetty.thread.pool.id` is a technical numeric identifier, usually single `0` value is used

## Jetty 9 to 11

Those metrics require the following Jetty modules to be enabled : `jmx`, `http` and `stats`.

| Metric Name                 | Type          | Attributes                                | Description                               |
|-----------------------------|---------------|-------------------------------------------|-------------------------------------------|
| jetty.thread.count          | UpDownCounter | jetty.thread.pool.id                      | The current number of threads             |
| jetty.thread.limit          | UpDownCounter | jetty.thread.pool.id                      | The maximum number of threads in the pool |
| jetty.thread.busy.count     | UpDownCounter | jetty.thread.pool.id                      | The current number of busy threads        |
| jetty.thread.idle.count     | UpDownCounter | jetty.thread.pool.id                      | The current number of idle threads        |
| jetty.thread.queue.size     | UpDownCounter | jetty.thread.pool.id                      | The current job queue size                |
| jetty.io.select.count       | Counter       | jetty.selector.context, jetty.selector.id | The number of select calls                |
| jetty.session.created.count | Counter       | jetty.context, jetty.session.handler.id   | The total number of created sessions      |
| jetty.session.duration.sum  | Gauge         | jetty.context, jetty.session.handler.id   | The cumulated session duration            |
| jetty.session.duration.max  | Gauge         | jetty.context, jetty.session.handler.id   | The maximum session duration              |
| jetty.session.duration.mean | Gauge         | jetty.context, jetty.session.handler.id   | The mean session duration                 |

- `jetty.context` corresponds to the deployed application subfolder in `webapps` folder.
- `jetty.selector.context` is a technical string identifier, high cardinality with values like `HTTP_1_1@7674f035` but stable per Jetty process instance
- `jetty.selector.id` is a technical numeric identifier, usually with low cardinality between `0` and `9`.
- `jetty.session.cache.id` is a technical numeric identifier, usually single `0` value is used
- `jetty.thread.pool.id` is a technical numeric identifier, usually single `0` value is used
- `jetty.session.handler.id` is a technical numeric identifier, usually single `0` value is used
