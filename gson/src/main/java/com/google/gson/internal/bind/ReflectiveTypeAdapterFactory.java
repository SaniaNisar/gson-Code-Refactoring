package com.google.gson.internal.bind;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.ReflectionAccessFilter;
import com.google.gson.ReflectionAccessFilter.FilterResult;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.Excluder;
import com.google.gson.internal.GsonTypes;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.internal.Primitives;
import com.google.gson.internal.ReflectionAccessFilterHelper;
import com.google.gson.internal.TroubleshootingGuide;
import com.google.gson.internal.reflect.ReflectionHelper;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Type adapter that reflects over the fields and methods of a class. */
public final class ReflectiveTypeAdapterFactory implements TypeAdapterFactory {
  private final ConstructorConstructor constructorConstructor;
  private final FieldNamingStrategy fieldNamingPolicy;
  private final Excluder excluder;
  private final JsonAdapterAnnotationTypeAdapterFactory jsonAdapterFactory;
  private final List<ReflectionAccessFilter> reflectionFilters;

  public ReflectiveTypeAdapterFactory(
      ConstructorConstructor constructorConstructor,
      FieldNamingStrategy fieldNamingPolicy,
      Excluder excluder,
      JsonAdapterAnnotationTypeAdapterFactory jsonAdapterFactory,
      List<ReflectionAccessFilter> reflectionFilters) {
    this.constructorConstructor = constructorConstructor;
    this.fieldNamingPolicy = fieldNamingPolicy;
    this.excluder = excluder;
    this.jsonAdapterFactory = jsonAdapterFactory;
    this.reflectionFilters = reflectionFilters;
  }

  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    Class<? super T> raw = type.getRawType();

    if (!Object.class.isAssignableFrom(raw)) {
      return null; // it's a primitive!
    }

    if (ReflectionHelper.isAnonymousOrNonStaticLocal(raw)) {
      return createNullHandlingAdapter();
    }

    FilterResult filterResult =
        ReflectionAccessFilterHelper.getFilterResult(reflectionFilters, raw);
    if (filterResult == FilterResult.BLOCK_ALL) {
      throw new JsonIOException(
          "ReflectionAccessFilter does not permit using reflection for "
              + raw
              + ". Register a TypeAdapter for this type or adjust the access filter.");
    }
    boolean blockInaccessible = filterResult == FilterResult.BLOCK_INACCESSIBLE;

    // If the type is actually a Java Record, we need to use the RecordAdapter instead
    if (ReflectionHelper.isRecord(raw)) {
      return createRecordAdapter(gson, type, raw, blockInaccessible);
    }

    ObjectConstructor<T> constructor = constructorConstructor.get(type, true);
    return new FieldReflectionAdapter<>(
        constructor, getBoundFields(gson, type, raw, blockInaccessible, false));
  }

  private <T> TypeAdapter<T> createNullHandlingAdapter() {
    return new TypeAdapter<T>() {
      @Override
      public T read(JsonReader in) throws IOException {
        in.skipValue();
        return null;
      }

      @Override
      public void write(JsonWriter out, T value) throws IOException {
        out.nullValue();
      }

      @Override
      public String toString() {
        return "AnonymousOrNonStaticLocalClassAdapter";
      }
    };
  }

  private <T> TypeAdapter<T> createRecordAdapter(
      Gson gson, TypeToken<T> type, Class<? super T> raw, boolean blockInaccessible) {
    @SuppressWarnings("unchecked")
    TypeAdapter<T> adapter =
        (TypeAdapter<T>)
            new RecordAdapter<>(
                raw, getBoundFields(gson, type, raw, blockInaccessible, true), blockInaccessible);
    return adapter;
  }

  private boolean includeField(Field f, boolean serialize) {
    return !excluder.excludeField(f, serialize);
  }

  /** First element holds the default name */
  @SuppressWarnings("MixedMutabilityReturnType")
  private List<String> getFieldNames(Field f) {
    SerializedName annotation = f.getAnnotation(SerializedName.class);
    if (annotation == null) {
      String fieldName = fieldNamingPolicy.translateName(f);
      List<String> alternates = fieldNamingPolicy.alternateNames(f);
      return createFieldNamesList(fieldName, alternates);
    } else {
      String fieldName = annotation.value();
      List<String> alternates = Arrays.asList(annotation.alternate());
      return createFieldNamesList(fieldName, alternates);
    }
  }

  private List<String> createFieldNamesList(String fieldName, List<String> alternates) {
    if (alternates.isEmpty()) {
      return Collections.singletonList(fieldName);
    }

    List<String> fieldNames = new ArrayList<>(alternates.size() + 1);
    fieldNames.add(fieldName);
    fieldNames.addAll(alternates);
    return fieldNames;
  }

  private static <M extends AccessibleObject & Member> void checkAccessible(
      Object object, M member) {
    if (!ReflectionAccessFilterHelper.canAccess(
        member, Modifier.isStatic(member.getModifiers()) ? null : object)) {
      String memberDescription = ReflectionHelper.getAccessibleObjectDescription(member, true);
      throw new JsonIOException(
          memberDescription
              + " is not accessible and ReflectionAccessFilter does not permit making it"
              + " accessible. Register a TypeAdapter for the declaring type, adjust the access"
              + " filter or increase the visibility of the element and its declaring type.");
    }
  }

  private BoundField createBoundField(
      Gson context,
      Field field,
      Method accessor,
      String serializedName,
      TypeToken<?> fieldType,
      boolean serialize,
      boolean blockInaccessible) {

    boolean isPrimitive = Primitives.isPrimitive(fieldType.getRawType());
    int modifiers = field.getModifiers();
    boolean isStaticFinalField = Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers);

    // Get type adapter for the field
    TypeAdapter<?> mapped = getTypeAdapterForField(context, field, fieldType);
    @SuppressWarnings("unchecked")
    TypeAdapter<Object> typeAdapter = (TypeAdapter<Object>) mapped;

    // Determine the write adapter needed for serialization
    TypeAdapter<Object> writeTypeAdapter =
        serialize
            ? determineWriteAdapter(context, typeAdapter, fieldType, mapped != null)
            : typeAdapter;

    return createBoundFieldInstance(
        serializedName,
        field,
        accessor,
        typeAdapter,
        writeTypeAdapter,
        isPrimitive,
        isStaticFinalField,
        blockInaccessible);
  }

  private TypeAdapter<?> getTypeAdapterForField(Gson context, Field field, TypeToken<?> fieldType) {
    JsonAdapter annotation = field.getAnnotation(JsonAdapter.class);
    if (annotation != null) {
      return jsonAdapterFactory.getTypeAdapter(
          constructorConstructor, context, fieldType, annotation, false);
    }
    return context.getAdapter(fieldType);
  }

  private TypeAdapter<Object> determineWriteAdapter(
      Gson context,
      TypeAdapter<Object> typeAdapter,
      TypeToken<?> fieldType,
      boolean jsonAdapterPresent) {
    if (jsonAdapterPresent) {
      return typeAdapter;
    }
    return new TypeAdapterRuntimeTypeWrapper<>(context, typeAdapter, fieldType.getType());
  }

  private BoundField createBoundFieldInstance(
      String serializedName,
      Field field,
      Method accessor,
      TypeAdapter<Object> typeAdapter,
      TypeAdapter<Object> writeTypeAdapter,
      boolean isPrimitive,
      boolean isStaticFinalField,
      boolean blockInaccessible) {

    return new BoundField(serializedName, field) {
      @Override
      void write(JsonWriter writer, Object source) throws IOException, IllegalAccessException {
        handleAccessibilityCheck(source, blockInaccessible, accessor);

        Object fieldValue = getFieldValue(source, accessor);
        if (fieldValue == source) {
          // avoid direct recursion
          return;
        }
        writer.name(serializedName);
        writeTypeAdapter.write(writer, fieldValue);
      }

      @Override
      void readIntoArray(JsonReader reader, int index, Object[] target)
          throws IOException, JsonParseException {
        Object fieldValue = typeAdapter.read(reader);
        if (fieldValue == null && isPrimitive) {
          throw new JsonParseException(
              "null is not allowed as value for record component '"
                  + fieldName
                  + "' of primitive type; at path "
                  + reader.getPath());
        }
        target[index] = fieldValue;
      }

      @Override
      void readIntoField(JsonReader reader, Object target)
          throws IOException, IllegalAccessException {
        Object fieldValue = typeAdapter.read(reader);
        if (fieldValue != null || !isPrimitive) {
          if (blockInaccessible) {
            checkAccessible(target, field);
          } else if (isStaticFinalField) {
            // Reflection does not permit setting value of `static final` field
            String fieldDescription = ReflectionHelper.getAccessibleObjectDescription(field, false);
            throw new JsonIOException("Cannot set value of 'static final' " + fieldDescription);
          }
          field.set(target, fieldValue);
        }
      }

      private Object getFieldValue(Object source, Method accessor)
          throws IllegalAccessException, IOException {
        try {
          if (accessor != null) {
            return invokeAccessor(accessor, source);
          } else {
            return field.get(source);
          }
        } catch (InvocationTargetException e) {
          String accessorDescription =
              ReflectionHelper.getAccessibleObjectDescription(accessor, false);
          throw new JsonIOException(
              "Accessor " + accessorDescription + " threw exception", e.getCause());
        }
      }

      private Object invokeAccessor(Method accessor, Object source)
          throws InvocationTargetException, IllegalAccessException {
        return accessor.invoke(source);
      }

      private void handleAccessibilityCheck(
          Object source, boolean blockInaccessible, Method accessor) {
        if (blockInaccessible) {
          if (accessor == null) {
            checkAccessible(source, field);
          } else {
            checkAccessible(source, accessor);
          }
        }
      }
    };
  }

  private static class FieldsData {
    public static final FieldsData EMPTY =
        new FieldsData(Collections.emptyMap(), Collections.emptyList());

    /** Maps from JSON member name to field */
    public final Map<String, BoundField> deserializedFields;

    public final List<BoundField> serializedFields;

    public FieldsData(
        Map<String, BoundField> deserializedFields, List<BoundField> serializedFields) {
      this.deserializedFields = deserializedFields;
      this.serializedFields = serializedFields;
    }
  }

  private static IllegalArgumentException createDuplicateFieldException(
      Class<?> declaringType, String duplicateName, Field field1, Field field2) {
    throw new IllegalArgumentException(
        "Class "
            + declaringType.getName()
            + " declares multiple JSON fields named '"
            + duplicateName
            + "'; conflict is caused by fields "
            + ReflectionHelper.fieldToString(field1)
            + " and "
            + ReflectionHelper.fieldToString(field2)
            + "\nSee "
            + TroubleshootingGuide.createUrl("duplicate-fields"));
  }

  private FieldsData getBoundFields(
      Gson context, TypeToken<?> type, Class<?> raw, boolean blockInaccessible, boolean isRecord) {
    if (raw.isInterface()) {
      return FieldsData.EMPTY;
    }

    Map<String, BoundField> deserializedFields = new LinkedHashMap<>();
    Map<String, BoundField> serializedFields = new LinkedHashMap<>();
    collectFieldsRecursively(
        context, type, raw, raw, blockInaccessible, isRecord, deserializedFields, serializedFields);

    return new FieldsData(deserializedFields, new ArrayList<>(serializedFields.values()));
  }

  private void collectFieldsRecursively(
      Gson context,
      TypeToken<?> type,
      Class<?> raw,
      Class<?> originalRaw,
      boolean blockInaccessible,
      boolean isRecord,
      Map<String, BoundField> deserializedFields,
      Map<String, BoundField> serializedFields) {

    if (raw == Object.class) {
      return;
    }

    // Process current class fields
    blockInaccessible =
        processCurrentClassFields(
            context,
            type,
            raw,
            originalRaw,
            blockInaccessible,
            isRecord,
            deserializedFields,
            serializedFields);

    // Process parent class fields recursively
    TypeToken<?> parentType =
        TypeToken.get(GsonTypes.resolve(type.getType(), raw, raw.getGenericSuperclass()));
    Class<?> parentRaw = parentType.getRawType();
    collectFieldsRecursively(
        context,
        parentType,
        parentRaw,
        originalRaw,
        blockInaccessible,
        isRecord,
        deserializedFields,
        serializedFields);
  }

  private boolean processCurrentClassFields(
      Gson context,
      TypeToken<?> type,
      Class<?> raw,
      Class<?> originalRaw,
      boolean blockInaccessible,
      boolean isRecord,
      Map<String, BoundField> deserializedFields,
      Map<String, BoundField> serializedFields) {

    Field[] fields = raw.getDeclaredFields();

    // For inherited fields, check if access to their declaring class is allowed
    if (raw != originalRaw && fields.length > 0) {
      FilterResult filterResult =
          ReflectionAccessFilterHelper.getFilterResult(reflectionFilters, raw);
      if (filterResult == FilterResult.BLOCK_ALL) {
        throw new JsonIOException(
            "ReflectionAccessFilter does not permit using reflection for "
                + raw
                + " (supertype of "
                + originalRaw
                + "). Register a TypeAdapter for this type or adjust the access filter.");
      }
      blockInaccessible = filterResult == FilterResult.BLOCK_INACCESSIBLE;
    }

    for (Field field : fields) {
      processField(
          context,
          type,
          raw,
          originalRaw,
          field,
          blockInaccessible,
          isRecord,
          deserializedFields,
          serializedFields);
    }

    return blockInaccessible;
  }

  private void processField(
      Gson context,
      TypeToken<?> type,
      Class<?> raw,
      Class<?> originalRaw,
      Field field,
      boolean blockInaccessible,
      boolean isRecord,
      Map<String, BoundField> deserializedFields,
      Map<String, BoundField> serializedFields) {

    boolean serialize = includeField(field, true);
    boolean deserialize = includeField(field, false);

    if (!serialize && !deserialize) {
      return;
    }

    Method accessor = null;
    if (isRecord) {
      if (Modifier.isStatic(field.getModifiers())) {
        deserialize = false;
      } else {
        accessor = ReflectionHelper.getAccessor(raw, field);
        if (!blockInaccessible) {
          ReflectionHelper.makeAccessible(accessor);
        }

        handleSerializedNameOnRecordAccessor(field, accessor);
      }
    }

    if (!blockInaccessible && accessor == null) {
      ReflectionHelper.makeAccessible(field);
    }

    Type fieldType = GsonTypes.resolve(type.getType(), raw, field.getGenericType());
    List<String> fieldNames = getFieldNames(field);
    String serializedName = fieldNames.get(0);

    BoundField boundField =
        createBoundField(
            context,
            field,
            accessor,
            serializedName,
            TypeToken.get(fieldType),
            serialize,
            blockInaccessible);

    addFieldToCollections(
        deserialize,
        serialize,
        fieldNames,
        serializedName,
        boundField,
        originalRaw,
        deserializedFields,
        serializedFields);
  }

  private void handleSerializedNameOnRecordAccessor(Field field, Method accessor) {
    if (accessor.getAnnotation(SerializedName.class) != null
        && field.getAnnotation(SerializedName.class) == null) {
      String methodDescription = ReflectionHelper.getAccessibleObjectDescription(accessor, false);
      throw new JsonIOException("@SerializedName on " + methodDescription + " is not supported");
    }
  }

  private void addFieldToCollections(
      boolean deserialize,
      boolean serialize,
      List<String> fieldNames,
      String serializedName,
      BoundField boundField,
      Class<?> originalRaw,
      Map<String, BoundField> deserializedFields,
      Map<String, BoundField> serializedFields) {

    if (deserialize) {
      for (String name : fieldNames) {
        BoundField replaced = deserializedFields.put(name, boundField);
        if (replaced != null) {
          throw createDuplicateFieldException(originalRaw, name, replaced.field, boundField.field);
        }
      }
    }

    if (serialize) {
      BoundField replaced = serializedFields.put(serializedName, boundField);
      if (replaced != null) {
        throw createDuplicateFieldException(
            originalRaw, serializedName, replaced.field, boundField.field);
      }
    }
  }

  abstract static class BoundField {
    /** Name used for serialization (but not for deserialization) */
    final String serializedName;

    final Field field;

    /** Name of the underlying field */
    final String fieldName;

    protected BoundField(String serializedName, Field field) {
      this.serializedName = serializedName;
      this.field = field;
      this.fieldName = field.getName();
    }

    /** Read this field value from the source, and append its JSON value to the writer */
    abstract void write(JsonWriter writer, Object source)
        throws IOException, IllegalAccessException;

    /** Read the value into the target array, used to provide constructor arguments for records */
    abstract void readIntoArray(JsonReader reader, int index, Object[] target)
        throws IOException, JsonParseException;

    /**
     * Read the value from the reader, and set it on the corresponding field on target via
     * reflection
     */
    abstract void readIntoField(JsonReader reader, Object target)
        throws IOException, IllegalAccessException;
  }

  /**
   * Base class for Adapters produced by this factory.
   *
   * <p>The {@link RecordAdapter} is a special case to handle records for JVMs that support it, for
   * all other types we use the {@link FieldReflectionAdapter}. This class encapsulates the common
   * logic for serialization and deserialization. During deserialization, we construct an
   * accumulator A, which we use to accumulate values from the source JSON. After the object has
   * been read in full, the {@link #finalize(Object)} method is used to convert the accumulator to
   * an instance of T.
   *
   * @param <T> type of objects that this Adapter creates.
   * @param <A> type of accumulator used to build the deserialization result.
   */
  // This class is public because external projects check for this class with `instanceof` (even
  // though it is internal)
  public abstract static class Adapter<T, A> extends TypeAdapter<T> {
    private final FieldsData fieldsData;

    Adapter(FieldsData fieldsData) {
      this.fieldsData = fieldsData;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
      if (value == null) {
        out.nullValue();
        return;
      }

      out.beginObject();
      try {
        for (BoundField boundField : fieldsData.serializedFields) {
          boundField.write(out, value);
        }
      } catch (IllegalAccessException e) {
        throw ReflectionHelper.createExceptionForUnexpectedIllegalAccess(e);
      }
      out.endObject();
    }

    @Override
    public T read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }

      A accumulator = createAccumulator();
      Map<String, BoundField> deserializedFields = fieldsData.deserializedFields;

      try {
        in.beginObject();
        while (in.hasNext()) {
          String name = in.nextName();
          BoundField field = deserializedFields.get(name);
          if (field == null) {
            in.skipValue();
          } else {
            readField(accumulator, in, field);
          }
        }
      } catch (IllegalStateException e) {
        throw new JsonSyntaxException(e);
      } catch (IllegalAccessException e) {
        throw ReflectionHelper.createExceptionForUnexpectedIllegalAccess(e);
      }
      in.endObject();
      return finalize(accumulator);
    }

    /** Create the Object that will be used to collect each field value */
    abstract A createAccumulator();

    /**
     * Read a single BoundField into the accumulator. The JsonReader will be pointed at the start of
     * the value for the BoundField to read from.
     */
    abstract void readField(A accumulator, JsonReader in, BoundField field)
        throws IllegalAccessException, IOException;

    /** Convert the accumulator to a final instance of T. */
    abstract T finalize(A accumulator);
  }

  private static final class FieldReflectionAdapter<T> extends Adapter<T, T> {
    private final ObjectConstructor<T> constructor;

    FieldReflectionAdapter(ObjectConstructor<T> constructor, FieldsData fieldsData) {
      super(fieldsData);
      this.constructor = constructor;
    }

    @Override
    T createAccumulator() {
      return constructor.construct();
    }

    @Override
    void readField(T accumulator, JsonReader in, BoundField field)
        throws IllegalAccessException, IOException {
      field.readIntoField(in, accumulator);
    }

    @Override
    T finalize(T accumulator) {
      return accumulator;
    }
  }

  private static final class RecordAdapter<T> extends Adapter<T, Object[]> {
    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = createPrimitiveDefaults();

    // The canonical constructor of the record
    private final Constructor<T> constructor;
    // Array of arguments to the constructor, initialized with default values for primitives
    private final Object[] constructorArgsDefaults;
    // Map from component names to index into the constructors arguments.
    private final Map<String, Integer> componentIndices = new HashMap<>();

    RecordAdapter(Class<T> raw, FieldsData fieldsData, boolean blockInaccessible) {
      super(fieldsData);
      constructor = ReflectionHelper.getCanonicalRecordConstructor(raw);

      if (blockInaccessible) {
        checkAccessible(null, constructor);
      } else {
        // Ensure the constructor is accessible
        ReflectionHelper.makeAccessible(constructor);
      }

      // Initialize default arguments for constructor parameters
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      Object[] defaults = new Object[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        defaults[i] = PRIMITIVE_DEFAULTS.get(parameterTypes[i]); // null for non-primitive types
      }
      constructorArgsDefaults = defaults;

      initializeComponentIndices(raw);
    }

    private void initializeComponentIndices(Class<T> raw) {
      String[] componentNames = ReflectionHelper.getRecordComponentNames(raw);
      for (int i = 0; i < componentNames.length; i++) {
        componentIndices.put(componentNames[i], i);
      }
    }

    private static Map<Class<?>, Object> createPrimitiveDefaults() {
      Map<Class<?>, Object> zeroes = new HashMap<>();
      zeroes.put(byte.class, (byte) 0);
      zeroes.put(short.class, (short) 0);
      zeroes.put(int.class, 0);
      zeroes.put(long.class, 0L);
      zeroes.put(float.class, 0F);
      zeroes.put(double.class, 0D);
      zeroes.put(char.class, '\0');
      zeroes.put(boolean.class, false);
      return zeroes;
    }

    @Override
    Object[] createAccumulator() {
      return constructorArgsDefaults.clone();
    }

    @Override
    void readField(Object[] accumulator, JsonReader in, BoundField field) throws IOException {
      // Obtain the component index from the name of the field backing it
      Integer componentIndex = componentIndices.get(field.fieldName);
      if (componentIndex == null) {
        throw createMissingComponentException(field);
      }
      field.readIntoArray(in, componentIndex, accumulator);
    }

    private IllegalStateException createMissingComponentException(BoundField field) {
      return new IllegalStateException(
          "Could not find the index in the constructor '"
              + ReflectionHelper.constructorToString(constructor)
              + "' for field with name '"
              + field.fieldName
              + "', unable to determine which argument in the constructor the field corresponds"
              + " to. This is unexpected behavior, as we expect the RecordComponents to have the"
              + " same names as the fields in the Java class, and that the order of the"
              + " RecordComponents is the same as the order of the canonical constructor"
              + " parameters.");
    }

    @Override
    T finalize(Object[] accumulator) {
      try {
        return constructor.newInstance(accumulator);
      } catch (IllegalAccessException e) {
        throw ReflectionHelper.createExceptionForUnexpectedIllegalAccess(e);
      } catch (InstantiationException | IllegalArgumentException e) {
        throw new RuntimeException(
            "Failed to invoke constructor '"
                + ReflectionHelper.constructorToString(constructor)
                + "' with args "
                + Arrays.toString(accumulator),
            e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(
            "Failed to invoke constructor '"
                + ReflectionHelper.constructorToString(constructor)
                + "' with args "
                + Arrays.toString(accumulator),
            e.getCause());
      }
    }
  }
}
