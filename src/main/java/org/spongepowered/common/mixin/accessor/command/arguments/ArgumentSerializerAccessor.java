package org.spongepowered.common.mixin.accessor.command.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.command.arguments.ArgumentSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(ArgumentSerializer.class)
public interface ArgumentSerializerAccessor<T extends ArgumentType<?>> {

    @Accessor("factory")
    Supplier<T> accessor$getFactory();

}
