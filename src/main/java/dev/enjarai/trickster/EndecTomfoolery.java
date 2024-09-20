package dev.enjarai.trickster;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Function3;
import com.mojang.serialization.Codec;
import com.mojang.util.UndashedUuid;
import io.wispforest.endec.*;
import io.wispforest.owo.serialization.CodecUtils;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class EndecTomfoolery {
    public static final Endec<BlockPos> ALWAYS_READABLE_BLOCK_POS =
            vectorEndec(Endec.INT, BlockPos::new, BlockPos::getX, BlockPos::getY, BlockPos::getZ);
    public static final Endec<UUID> UUID = Endec.STRING.xmap(UndashedUuid::fromStringLenient, java.util.UUID::toString);

    public static final SerializationAttribute.Marker UBER_COMPACT_ATTRIBUTE = SerializationAttribute.marker("uber_compact");
    public static final SerializationAttribute.Marker CODEC_SAFE = SerializationAttribute.marker("codec_safe");

    public static <C, V> Endec<V> vectorEndec(Endec<C> componentEndec, Function3<C, C, C, V> constructor, Function<V, C> xGetter, Function<V, C> yGetter, Function<V, C> zGetter) {
        return componentEndec.listOf().validate(ints -> {
            if (ints.size() != 3) {
                throw new IllegalStateException("vector array must have three elements");
            }
        }).xmap(
                components -> constructor.apply(components.get(0), components.get(1), components.get(2)),
                vector -> List.of(xGetter.apply(vector), yGetter.apply(vector), zGetter.apply(vector))
        );
    }

    public static <T> Endec<Optional<T>> safeOptionalOf(Endec<T> endec) {
        return Endec.ifAttr(CODEC_SAFE, Endec.<Optional<T>>of(
                (ctx, serializer, value) -> {
                    try (var struct = serializer.struct()) {
                        struct.field("present", ctx, Endec.BOOLEAN, value.isPresent());
                        value.ifPresent(t -> struct.field("value", ctx, endec, t));
                    }
                },
                (ctx, deserializer) -> {
                    var struct = deserializer.struct();
                    //noinspection DataFlowIssue
                    if (struct.field("present", ctx, Endec.BOOLEAN)) {
                        //noinspection DataFlowIssue
                        return Optional.of(struct.field("value", ctx, endec));
                    } else {
                        return Optional.empty();
                    }
                }
        )).orElse(endec.optionalOf());
    }

    public static <T> Codec<T> toCodec(Endec<T> endec) {
        return CodecUtils.toCodec(endec, SerializationContext.attributes(CODEC_SAFE));
    }

    public static <T> StructEndec<T> recursive(Function<StructEndec<T>, StructEndec<T>> wrapped) {
        return new RecursiveStructEndec<>(wrapped);
    }

    public static <T> StructEndec<T> lazy(Supplier<StructEndec<T>> supplier) {
        return new RecursiveStructEndec<>(e -> supplier.get());
    }

    public static <T> StructEndec<T> unit(T value) {
        return new StructEndec<>() {
            @Override
            public void encodeStruct(SerializationContext ctx, Serializer<?> serializer, Serializer.Struct struct, T value) {
                // no-op
            }

            @Override
            public T decodeStruct(SerializationContext ctx, Deserializer<?> deserializer, Deserializer.Struct struct) {
                return value;
            }
        };
    }

    public static <T> StructEndec<T> unit(Supplier<T> value) {
        return new StructEndec<>() {
            @Override
            public void encodeStruct(SerializationContext ctx, Serializer<?> serializer, Serializer.Struct struct, T value) {
                // no-op
            }

            @Override
            public T decodeStruct(SerializationContext ctx, Deserializer<?> deserializer, Deserializer.Struct struct) {
                return value.get();
            }
        };
    }

    private static class RecursiveStructEndec<T> implements StructEndec<T> {
        private final Supplier<StructEndec<T>> wrapped;

        RecursiveStructEndec(Function<StructEndec<T>, StructEndec<T>> wrapped) {
            this.wrapped = Suppliers.memoize(() -> wrapped.apply(this));
        }

        @Override
        public void encodeStruct(SerializationContext ctx, Serializer<?> serializer, Serializer.Struct struct, T value) {
            wrapped.get().encodeStruct(ctx, serializer, struct, value);
        }

        @Override
        public T decodeStruct(SerializationContext ctx, Deserializer<?> deserializer, Deserializer.Struct struct) {
            return wrapped.get().decodeStruct(ctx, deserializer, struct);
        }
    }
}
