Keep Apache and Tomcat DBCP pool metric names stable after metrics are first registered. Late MBean registration no longer replaces a derived pool name with the JMX name, preventing one pool's history from being split across metric series.

Document this lifecycle and cover both legacy and stable database semantic conventions.

Fixes #20232
