package io.github.nicodoou.mobai.adapter.debug;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.OptionalLong;

/** Gson has no built-in support for Optional: empty is written as null and read back as empty. */
final class OptionalTypeAdapterFactory implements TypeAdapterFactory {
  @Override
  @SuppressWarnings("unchecked") // the raw type was checked just above each cast
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    if (type.getRawType() == OptionalLong.class) {
      return (TypeAdapter<T>) new OptionalLongAdapter();
    }
    if (type.getRawType() != Optional.class) {
      // Returning null is the TypeAdapterFactory contract for "not my type".
      return null;
    }
    Type valueType = ((ParameterizedType) type.getType()).getActualTypeArguments()[0];
    return (TypeAdapter<T>) new OptionalAdapter<>(gson.getAdapter(TypeToken.get(valueType)));
  }

  private static final class OptionalAdapter<V> extends TypeAdapter<Optional<V>> {
    private final TypeAdapter<V> valueAdapter;

    OptionalAdapter(TypeAdapter<V> valueAdapter) {
      this.valueAdapter = valueAdapter;
    }

    @Override
    public void write(JsonWriter out, Optional<V> value) throws IOException {
      if (value.isEmpty()) {
        out.nullValue();
        return;
      }
      valueAdapter.write(out, value.get());
    }

    @Override
    public Optional<V> read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return Optional.empty();
      }
      return Optional.of(valueAdapter.read(in));
    }
  }

  private static final class OptionalLongAdapter extends TypeAdapter<OptionalLong> {
    @Override
    public void write(JsonWriter out, OptionalLong value) throws IOException {
      if (value.isEmpty()) {
        out.nullValue();
        return;
      }
      out.value(value.getAsLong());
    }

    @Override
    public OptionalLong read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return OptionalLong.empty();
      }
      return OptionalLong.of(in.nextLong());
    }
  }
}
