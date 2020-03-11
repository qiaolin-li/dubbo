// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: MapValue.proto

package org.apache.dubbo.common.serialize.protobuf.support.wrapper;

public final class MapValue {
  private MapValue() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  public interface MapOrBuilder extends
      // @@protoc_insertion_point(interface_extends:org.apache.dubbo.common.serialize.protobuf.support.wrapper.Map)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <code>map&lt;string, string&gt; attachments = 1;</code>
     */
    int getAttachmentsCount();
    /**
     * <code>map&lt;string, string&gt; attachments = 1;</code>
     */
    boolean containsAttachments(
        java.lang.String key);
    /**
     * Use {@link #getAttachmentsMap()} instead.
     */
    @java.lang.Deprecated
    java.util.Map<java.lang.String, java.lang.String>
    getAttachments();
    /**
     * <code>map&lt;string, string&gt; attachments = 1;</code>
     */
    java.util.Map<java.lang.String, java.lang.String>
    getAttachmentsMap();
    /**
     * <code>map&lt;string, string&gt; attachments = 1;</code>
     */

    java.lang.String getAttachmentsOrDefault(
        java.lang.String key,
        java.lang.String defaultValue);
    /**
     * <code>map&lt;string, string&gt; attachments = 1;</code>
     */

    java.lang.String getAttachmentsOrThrow(
        java.lang.String key);
  }
  /**
   * Protobuf type {@code org.apache.dubbo.common.serialize.protobuf.support.wrapper.Map}
   */
  public  static final class Map extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:org.apache.dubbo.common.serialize.protobuf.support.wrapper.Map)
      MapOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use Map.newBuilder() to construct.
    private Map(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private Map() {
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
      return new Map();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private Map(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10: {
              if (!((mutable_bitField0_ & 0x00000001) != 0)) {
                attachments_ = com.google.protobuf.MapField.newMapField(
                    AttachmentsDefaultEntryHolder.defaultEntry);
                mutable_bitField0_ |= 0x00000001;
              }
              com.google.protobuf.MapEntry<java.lang.String, java.lang.String>
              attachments__ = input.readMessage(
                  AttachmentsDefaultEntryHolder.defaultEntry.getParserForType(), extensionRegistry);
              attachments_.getMutableMap().put(
                  attachments__.getKey(), attachments__.getValue());
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_descriptor;
    }

    @SuppressWarnings({"rawtypes"})
    @java.lang.Override
    protected com.google.protobuf.MapField internalGetMapField(
        int number) {
      switch (number) {
        case 1:
          return internalGetAttachments();
        default:
          throw new RuntimeException(
              "Invalid map field number: " + number);
      }
    }
    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map.class, org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map.Builder.class);
    }

    public static final int ATTACHMENTS_FIELD_NUMBER = 1;
    private static final class AttachmentsDefaultEntryHolder {
      static final com.google.protobuf.MapEntry<
          java.lang.String, java.lang.String> defaultEntry =
              com.google.protobuf.MapEntry
              .<java.lang.String, java.lang.String>newDefaultInstance(
                  org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_AttachmentsEntry_descriptor, 
                  com.google.protobuf.WireFormat.FieldType.STRING,
                  "",
                  com.google.protobuf.WireFormat.FieldType.STRING,
                  "");
    }
    private com.google.protobuf.MapField<
        java.lang.String, java.lang.String> attachments_;
    private com.google.protobuf.MapField<java.lang.String, java.lang.String>
    internalGetAttachments() {
      if (attachments_ == null) {
        return com.google.protobuf.MapField.emptyMapField(
            AttachmentsDefaultEntryHolder.defaultEntry);
      }
      return attachments_;
    }

    public int getAttachmentsCount() {
      return internalGetAttachments().getMap().size();
    }
    /**
     * <code>map&lt;string, string&gt; attachments = 1;</code>
     */

    public boolean containsAttachments(
        java.lang.String key) {
      if (key == null) { throw new java.lang.NullPointerException(); }
      return internalGetAttachments().getMap().containsKey(key);
    }
    /**
     * Use {@link #getAttachmentsMap()} instead.
     */
    @java.lang.Deprecated
    public java.util.Map<java.lang.String, java.lang.String> getAttachments() {
      return getAttachmentsMap();
    }
    /**
     * <code>map&lt;string, string&gt; attachments = 1;</code>
     */

    public java.util.Map<java.lang.String, java.lang.String> getAttachmentsMap() {
      return internalGetAttachments().getMap();
    }
    /**
     * <code>map&lt;string, string&gt; attachments = 1;</code>
     */

    public java.lang.String getAttachmentsOrDefault(
        java.lang.String key,
        java.lang.String defaultValue) {
      if (key == null) { throw new java.lang.NullPointerException(); }
      java.util.Map<java.lang.String, java.lang.String> map =
          internalGetAttachments().getMap();
      return map.containsKey(key) ? map.get(key) : defaultValue;
    }
    /**
     * <code>map&lt;string, string&gt; attachments = 1;</code>
     */

    public java.lang.String getAttachmentsOrThrow(
        java.lang.String key) {
      if (key == null) { throw new java.lang.NullPointerException(); }
      java.util.Map<java.lang.String, java.lang.String> map =
          internalGetAttachments().getMap();
      if (!map.containsKey(key)) {
        throw new java.lang.IllegalArgumentException();
      }
      return map.get(key);
    }

    private byte memoizedIsInitialized = -1;
    @java.lang.Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      com.google.protobuf.GeneratedMessageV3
        .serializeStringMapTo(
          output,
          internalGetAttachments(),
          AttachmentsDefaultEntryHolder.defaultEntry,
          1);
      unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      for (java.util.Map.Entry<java.lang.String, java.lang.String> entry
           : internalGetAttachments().getMap().entrySet()) {
        com.google.protobuf.MapEntry<java.lang.String, java.lang.String>
        attachments__ = AttachmentsDefaultEntryHolder.defaultEntry.newBuilderForType()
            .setKey(entry.getKey())
            .setValue(entry.getValue())
            .build();
        size += com.google.protobuf.CodedOutputStream
            .computeMessageSize(1, attachments__);
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map)) {
        return super.equals(obj);
      }
      org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map other = (org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map) obj;

      if (!internalGetAttachments().equals(
          other.internalGetAttachments())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      if (!internalGetAttachments().getMap().isEmpty()) {
        hash = (37 * hash) + ATTACHMENTS_FIELD_NUMBER;
        hash = (53 * hash) + internalGetAttachments().hashCode();
      }
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @java.lang.Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code org.apache.dubbo.common.serialize.protobuf.support.wrapper.Map}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:org.apache.dubbo.common.serialize.protobuf.support.wrapper.Map)
        org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.MapOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_descriptor;
      }

      @SuppressWarnings({"rawtypes"})
      protected com.google.protobuf.MapField internalGetMapField(
          int number) {
        switch (number) {
          case 1:
            return internalGetAttachments();
          default:
            throw new RuntimeException(
                "Invalid map field number: " + number);
        }
      }
      @SuppressWarnings({"rawtypes"})
      protected com.google.protobuf.MapField internalGetMutableMapField(
          int number) {
        switch (number) {
          case 1:
            return internalGetMutableAttachments();
          default:
            throw new RuntimeException(
                "Invalid map field number: " + number);
        }
      }
      @java.lang.Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map.class, org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map.Builder.class);
      }

      // Construct using org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      @java.lang.Override
      public Builder clear() {
        super.clear();
        internalGetMutableAttachments().clear();
        return this;
      }

      @java.lang.Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_descriptor;
      }

      @java.lang.Override
      public org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map getDefaultInstanceForType() {
        return org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map.getDefaultInstance();
      }

      @java.lang.Override
      public org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map build() {
        org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @java.lang.Override
      public org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map buildPartial() {
        org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map result = new org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map(this);
        int from_bitField0_ = bitField0_;
        result.attachments_ = internalGetAttachments();
        result.attachments_.makeImmutable();
        onBuilt();
        return result;
      }

      @java.lang.Override
      public Builder clone() {
        return super.clone();
      }
      @java.lang.Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.setField(field, value);
      }
      @java.lang.Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @java.lang.Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @java.lang.Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, java.lang.Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @java.lang.Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          java.lang.Object value) {
        return super.addRepeatedField(field, value);
      }
      @java.lang.Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map) {
          return mergeFrom((org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map other) {
        if (other == org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map.getDefaultInstance()) return this;
        internalGetMutableAttachments().mergeFrom(
            other.internalGetAttachments());
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @java.lang.Override
      public final boolean isInitialized() {
        return true;
      }

      @java.lang.Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      private com.google.protobuf.MapField<
          java.lang.String, java.lang.String> attachments_;
      private com.google.protobuf.MapField<java.lang.String, java.lang.String>
      internalGetAttachments() {
        if (attachments_ == null) {
          return com.google.protobuf.MapField.emptyMapField(
              AttachmentsDefaultEntryHolder.defaultEntry);
        }
        return attachments_;
      }
      private com.google.protobuf.MapField<java.lang.String, java.lang.String>
      internalGetMutableAttachments() {
        onChanged();;
        if (attachments_ == null) {
          attachments_ = com.google.protobuf.MapField.newMapField(
              AttachmentsDefaultEntryHolder.defaultEntry);
        }
        if (!attachments_.isMutable()) {
          attachments_ = attachments_.copy();
        }
        return attachments_;
      }

      public int getAttachmentsCount() {
        return internalGetAttachments().getMap().size();
      }
      /**
       * <code>map&lt;string, string&gt; attachments = 1;</code>
       */

      public boolean containsAttachments(
          java.lang.String key) {
        if (key == null) { throw new java.lang.NullPointerException(); }
        return internalGetAttachments().getMap().containsKey(key);
      }
      /**
       * Use {@link #getAttachmentsMap()} instead.
       */
      @java.lang.Deprecated
      public java.util.Map<java.lang.String, java.lang.String> getAttachments() {
        return getAttachmentsMap();
      }
      /**
       * <code>map&lt;string, string&gt; attachments = 1;</code>
       */

      public java.util.Map<java.lang.String, java.lang.String> getAttachmentsMap() {
        return internalGetAttachments().getMap();
      }
      /**
       * <code>map&lt;string, string&gt; attachments = 1;</code>
       */

      public java.lang.String getAttachmentsOrDefault(
          java.lang.String key,
          java.lang.String defaultValue) {
        if (key == null) { throw new java.lang.NullPointerException(); }
        java.util.Map<java.lang.String, java.lang.String> map =
            internalGetAttachments().getMap();
        return map.containsKey(key) ? map.get(key) : defaultValue;
      }
      /**
       * <code>map&lt;string, string&gt; attachments = 1;</code>
       */

      public java.lang.String getAttachmentsOrThrow(
          java.lang.String key) {
        if (key == null) { throw new java.lang.NullPointerException(); }
        java.util.Map<java.lang.String, java.lang.String> map =
            internalGetAttachments().getMap();
        if (!map.containsKey(key)) {
          throw new java.lang.IllegalArgumentException();
        }
        return map.get(key);
      }

      public Builder clearAttachments() {
        internalGetMutableAttachments().getMutableMap()
            .clear();
        return this;
      }
      /**
       * <code>map&lt;string, string&gt; attachments = 1;</code>
       */

      public Builder removeAttachments(
          java.lang.String key) {
        if (key == null) { throw new java.lang.NullPointerException(); }
        internalGetMutableAttachments().getMutableMap()
            .remove(key);
        return this;
      }
      /**
       * Use alternate mutation accessors instead.
       */
      @java.lang.Deprecated
      public java.util.Map<java.lang.String, java.lang.String>
      getMutableAttachments() {
        return internalGetMutableAttachments().getMutableMap();
      }
      /**
       * <code>map&lt;string, string&gt; attachments = 1;</code>
       */
      public Builder putAttachments(
          java.lang.String key,
          java.lang.String value) {
        if (key == null) { throw new java.lang.NullPointerException(); }
        if (value == null) { throw new java.lang.NullPointerException(); }
        internalGetMutableAttachments().getMutableMap()
            .put(key, value);
        return this;
      }
      /**
       * <code>map&lt;string, string&gt; attachments = 1;</code>
       */

      public Builder putAllAttachments(
          java.util.Map<java.lang.String, java.lang.String> values) {
        internalGetMutableAttachments().getMutableMap()
            .putAll(values);
        return this;
      }
      @java.lang.Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @java.lang.Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:org.apache.dubbo.common.serialize.protobuf.support.wrapper.Map)
    }

    // @@protoc_insertion_point(class_scope:org.apache.dubbo.common.serialize.protobuf.support.wrapper.Map)
    private static final org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map();
    }

    public static org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<Map>
        PARSER = new com.google.protobuf.AbstractParser<Map>() {
      @java.lang.Override
      public Map parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new Map(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<Map> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<Map> getParserForType() {
      return PARSER;
    }

    @java.lang.Override
    public org.apache.dubbo.common.serialize.protobuf.support.wrapper.MapValue.Map getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_AttachmentsEntry_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_AttachmentsEntry_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\016MapValue.proto\022:org.apache.dubbo.commo" +
      "n.serialize.protobuf.support.wrapper\"\240\001\n" +
      "\003Map\022e\n\013attachments\030\001 \003(\0132P.org.apache.d" +
      "ubbo.common.serialize.protobuf.support.w" +
      "rapper.Map.AttachmentsEntry\0322\n\020Attachmen" +
      "tsEntry\022\013\n\003key\030\001 \001(\t\022\r\n\005value\030\002 \001(\t:\0028\001B" +
      ">\n:org.apache.dubbo.common.serialize.pro" +
      "tobuf.support.wrapperP\000b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_descriptor,
        new java.lang.String[] { "Attachments", });
    internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_AttachmentsEntry_descriptor =
      internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_descriptor.getNestedTypes().get(0);
    internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_AttachmentsEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_org_apache_dubbo_common_serialize_protobuf_support_wrapper_Map_AttachmentsEntry_descriptor,
        new java.lang.String[] { "Key", "Value", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
