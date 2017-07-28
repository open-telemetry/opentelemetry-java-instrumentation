package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDTags;
import io.opentracing.tag.Tags;

public class DBStatementAsResourceName extends AbstractDecorator {

  public DBStatementAsResourceName() {
    super();
    this.setMatchingTag(Tags.DB_STATEMENT.getKey());
    this.setSetTag(DDTags.RESOURCE_NAME);
  }

  //{ "insert" : "calls", "ordered" : true, "documents" : [{ "_id" : { "$oid" : "5979bbb0ed6fed5749cc9e7c" }, "name" : "MongoDB", "type" : "database", "identifier" : "10", "versions" : ["v3.2", "v3.0", "v2.6"], "info" : { "x" : 203, "y" : 102 } }] }
  private void normalizeFilter(final Object f) {}
}

  /*
  def normalize_filter(f=None):
      if f is None:
          return {}
      elif isinstance(f, list):
          # normalize lists of filters
          # e.g. {$or: [ { age: { $lt: 30 } }, { type: 1 } ]}
          return [normalize_filter(s) for s in f]
      elif isinstance(f, dict):
          # normalize dicts of filters
          #   {$or: [ { age: { $lt: 30 } }, { type: 1 } ]})
          out = {}
          for k, v in iteritems(f):
              if k == "$in" or k == "$nin":
                  # special case $in queries so we don't loop over lists.
                  out[k] = "?"
              elif isinstance(v, list) or isinstance(v, dict):
                  # RECURSION ALERT: needs to move to the agent
                  out[k] = normalize_filter(v)
              else:
                  # NOTE: this shouldn't happen, but let's have a safeguard.
                  out[k] = '?'
          return out
      else:
          # FIXME[matt] unexpected type. not sure this should ever happen, but at
          # least it won't crash.
          return {}*/
