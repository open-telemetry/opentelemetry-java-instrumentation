//package io.opentelemetry.instrumentation.nifi.v1_24_0.model;
//
//public enum State {
//
//  INIT_PROCESSOR {
//
//    @Override
//    public State nextState() {
//      return TRIGGER_PROCESSOR;
//    }
//  },
//  TRIGGER_PROCESSOR {
//
//    @Override
//    public State nextState() {
//      return CREATE_SESSION;
//    }
//  },
//  CREATE_SESSION {
//
//    @Override
//    public State nextState() {
//      return CREATE_FLOWFILE;
//    }
//  },
//  CREATE_FLOWFILE {
//
//    @Override
//    public State nextState() {
//      return TRANSFER_FLOWFILE;
//    }
//  },
//  GET_FLOWFILE {
//
//    @Override
//    public State nextState() {
//      return TRANSFER_FLOWFILE;
//    }
//  },
//  CLONE_FLOWFILE {
//
//    @Override
//    public State nextState() {
//      return TRANSFER_FLOWFILE;
//    }
//  },
//  TRANSFER_FLOWFILE {
//
//    @Override
//    public State nextState() {
//      return COMMIT_SESSION;
//    }
//  },
//  COMMIT_SESSION {
//
//    @Override
//    public State nextState() {
//      return COMMIT_SESSION;
//    }
//  },
//  ATTACH_LOG {
//
//    @Override
//    public State nextState() {
//      return COMMIT_SESSION;
//    }
//  },
//  EXTRACT_HEADERS {
//
//    @Override
//    public State nextState() {
//      return NULL;
//    }
//  },
//  INJECT_HEADERS {
//
//    @Override
//    public State nextState() {
//      return NULL;
//    }
//  },
//  AGENT_STARTUP {
//
//    @Override
//    public State nextState() {
//      return NULL;
//    }
//  },
//  AGENT_OPERATION {
//
//    @Override
//    public State nextState() {
//      return NULL;
//    }
//  },
//  ON_TRIGGERED {
//
//    @Override
//    public State nextState() {
//      return CREATE_SESSION;
//    }
//  },
//  ON_SCHEDULED {
//
//    @Override
//    public State nextState() {
//      return ON_TRIGGERED;
//    }
//  },
//  NULL {
//
//    @Override
//    public State nextState() {
//      return NULL;
//    }
//  };
//
//  public abstract State nextState();
//}