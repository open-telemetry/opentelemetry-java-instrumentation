Redis instrumentations duplicated anonymous database getter subclasses solely to omit `db.namespace` from span names. Use a shared internal `RedisSpanNameExtractor.create(dbAttributesGetter)` across Lettuce, Jedis, Vert.x Redis, Rediscala, and Redisson, while the original getters continue to supply database attributes.

Keep the namespace-masking getter private to the extractor, restore the concrete getters to `final`, and cover extracted names in old and stable database semconv modes.

Fixes #19717
