/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.thrift;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "all"})
public class Account
    implements org.apache.thrift.TBase<Account, Account._Fields>, java.io.Serializable, Cloneable {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
      new org.apache.thrift.protocol.TStruct("Account");

  private static final org.apache.thrift.protocol.TField ZONE_FIELD_DESC =
      new org.apache.thrift.protocol.TField(
          "zone", org.apache.thrift.protocol.TType.STRING, (short) 1);
  private static final org.apache.thrift.protocol.TField CARD_ID_FIELD_DESC =
      new org.apache.thrift.protocol.TField(
          "cardId", org.apache.thrift.protocol.TType.STRING, (short) 2);

  public String zone; // required
  public String cardId; // required

  /**
   * The set of fields this struct contains, along with convenience methods for finding and
   * manipulating them.
   */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    ZONE((short) 1, "zone"),
    CARD_ID((short) 2, "cardId");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /** Find the _Fields constant that matches fieldId, or null if its not found. */
    public static _Fields findByThriftId(int fieldId) {
      switch (fieldId) {
        case 1: // ZONE
          return ZONE;
        case 2: // CARD_ID
          return CARD_ID;
        default:
          return null;
      }
    }

    /** Find the _Fields constant that matches fieldId, throwing an exception if it is not found. */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null)
        throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /** Find the _Fields constant that matches name, or null if its not found. */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments

  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
        new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(
        _Fields.ZONE,
        new org.apache.thrift.meta_data.FieldMetaData(
            "zone",
            org.apache.thrift.TFieldRequirementType.REQUIRED,
            new org.apache.thrift.meta_data.FieldValueMetaData(
                org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(
        _Fields.CARD_ID,
        new org.apache.thrift.meta_data.FieldMetaData(
            "cardId",
            org.apache.thrift.TFieldRequirementType.REQUIRED,
            new org.apache.thrift.meta_data.FieldValueMetaData(
                org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(Account.class, metaDataMap);
  }

  public Account() {}

  public Account(String zone, String cardId) {
    this();
    this.zone = zone;
    this.cardId = cardId;
  }

  /** Performs a deep copy on <i>other</i>. */
  public Account(Account other) {
    if (other.isSetZone()) {
      this.zone = other.zone;
    }
    if (other.isSetCardId()) {
      this.cardId = other.cardId;
    }
  }

  public Account deepCopy() {
    return new Account(this);
  }

  @Override
  public void clear() {
    this.zone = null;
    this.cardId = null;
  }

  public String getZone() {
    return this.zone;
  }

  public Account setZone(String zone) {
    this.zone = zone;
    return this;
  }

  public void unsetZone() {
    this.zone = null;
  }

  /** Returns true if field zone is set (has been assigned a value) and false otherwise */
  public boolean isSetZone() {
    return this.zone != null;
  }

  public void setZoneIsSet(boolean value) {
    if (!value) {
      this.zone = null;
    }
  }

  public String getCardId() {
    return this.cardId;
  }

  public Account setCardId(String cardId) {
    this.cardId = cardId;
    return this;
  }

  public void unsetCardId() {
    this.cardId = null;
  }

  /** Returns true if field cardId is set (has been assigned a value) and false otherwise */
  public boolean isSetCardId() {
    return this.cardId != null;
  }

  public void setCardIdIsSet(boolean value) {
    if (!value) {
      this.cardId = null;
    }
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
      case ZONE:
        if (value == null) {
          unsetZone();
        } else {
          setZone((String) value);
        }
        break;

      case CARD_ID:
        if (value == null) {
          unsetCardId();
        } else {
          setCardId((String) value);
        }
        break;
    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
      case ZONE:
        return getZone();

      case CARD_ID:
        return getCardId();
    }
    throw new IllegalStateException();
  }

  /**
   * Returns true if field corresponding to fieldID is set (has been assigned a value) and false
   * otherwise
   */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
      case ZONE:
        return isSetZone();
      case CARD_ID:
        return isSetCardId();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null) return false;
    if (that instanceof Account) return this.equals((Account) that);
    return false;
  }

  public boolean equals(Account that) {
    if (that == null) return false;

    boolean this_present_zone = true && this.isSetZone();
    boolean that_present_zone = true && that.isSetZone();
    if (this_present_zone || that_present_zone) {
      if (!(this_present_zone && that_present_zone)) return false;
      if (!this.zone.equals(that.zone)) return false;
    }

    boolean this_present_cardId = true && this.isSetCardId();
    boolean that_present_cardId = true && that.isSetCardId();
    if (this_present_cardId || that_present_cardId) {
      if (!(this_present_cardId && that_present_cardId)) return false;
      if (!this.cardId.equals(that.cardId)) return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  public int compareTo(Account other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;
    Account typedOther = (Account) other;

    lastComparison = Boolean.valueOf(isSetZone()).compareTo(typedOther.isSetZone());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetZone()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.zone, typedOther.zone);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetCardId()).compareTo(typedOther.isSetCardId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetCardId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.cardId, typedOther.cardId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    org.apache.thrift.protocol.TField field;
    iprot.readStructBegin();
    while (true) {
      field = iprot.readFieldBegin();
      if (field.type == org.apache.thrift.protocol.TType.STOP) {
        break;
      }
      switch (field.id) {
        case 1: // ZONE
          if (field.type == org.apache.thrift.protocol.TType.STRING) {
            this.zone = iprot.readString();
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
          }
          break;
        case 2: // CARD_ID
          if (field.type == org.apache.thrift.protocol.TType.STRING) {
            this.cardId = iprot.readString();
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
          }
          break;
        default:
          org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
      }
      iprot.readFieldEnd();
    }
    iprot.readStructEnd();

    // check for required fields of primitive type, which can't be checked in the validate method
    validate();
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot)
      throws org.apache.thrift.TException {
    validate();

    oprot.writeStructBegin(STRUCT_DESC);
    if (this.zone != null) {
      oprot.writeFieldBegin(ZONE_FIELD_DESC);
      oprot.writeString(this.zone);
      oprot.writeFieldEnd();
    }
    if (this.cardId != null) {
      oprot.writeFieldBegin(CARD_ID_FIELD_DESC);
      oprot.writeString(this.cardId);
      oprot.writeFieldEnd();
    }
    oprot.writeFieldStop();
    oprot.writeStructEnd();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("Account(");
    boolean first = true;

    sb.append("zone:");
    if (this.zone == null) {
      sb.append("null");
    } else {
      sb.append(this.zone);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("cardId:");
    if (this.cardId == null) {
      sb.append("null");
    } else {
      sb.append(this.cardId);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    if (zone == null) {
      throw new org.apache.thrift.protocol.TProtocolException(
          "Required field 'zone' was not present! Struct: " + toString());
    }
    if (cardId == null) {
      throw new org.apache.thrift.protocol.TProtocolException(
          "Required field 'cardId' was not present! Struct: " + toString());
    }
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(
          new org.apache.thrift.protocol.TCompactProtocol(
              new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in)
      throws java.io.IOException, ClassNotFoundException {
    try {
      read(
          new org.apache.thrift.protocol.TCompactProtocol(
              new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }
}
