/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.core.command.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import jdk.internal.org.objectweb.asm.Opcodes;
import net.minecraft.command.arguments.ArgumentTypes;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.command.arguments.IArgumentSerializer;
import net.minecraft.command.arguments.IRangeArgument;
import net.minecraft.command.arguments.serializers.StringArgumentSerializer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.common.bridge.command.argument.ArgumentTypes_EntryBridge;
import org.spongepowered.common.command.registrar.tree.EmptyCommandTreeBuilder;
import org.spongepowered.common.command.registrar.tree.EntityCommandTreeBuilder;
import org.spongepowered.common.command.registrar.tree.RangeCommandTreeBuilder;
import org.spongepowered.common.command.registrar.tree.StringCommandTreeBuilder;

import java.util.Map;
import java.util.function.Function;

@Mixin(ArgumentTypes.class)
public abstract class ArgumentTypesMixin {

    // Targeting the LAST put.
    @Redirect(method = "register",
            slice = @Slice(
                    from = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, target = "net/minecraft/command/arguments/ArgumentTypes.ID_TYPE_MAP:Ljava/util/Map;"),
                    to = @At("TAIL")),
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static <T extends ArgumentType<?>> Object impl$registerClientCompletionKey(
            Map<ResourceLocation, Object> map, @Coerce Object key, @Coerce Object value, String namespace, Class<?> argumentType,
            IArgumentSerializer<?> serializer) {
        if (serializer instanceof IRangeArgument.IntRange) {
            impl$addToValue(value,
                    (Function<ClientCompletionKey<CommandTreeBuilder.Range<Integer>>, CommandTreeBuilder.Range<Integer>>) RangeCommandTreeBuilder::new);
        } else if (serializer instanceof IRangeArgument.FloatRange) {
            impl$addToValue(value,
                    (Function<ClientCompletionKey<CommandTreeBuilder.Range<Float>>, CommandTreeBuilder.Range<Float>>) RangeCommandTreeBuilder::new);
        } else if (serializer instanceof StringArgumentSerializer) {
            impl$addToValue(value, StringCommandTreeBuilder::new);
        } else if (serializer instanceof EntityArgument) {
            impl$addToValue(value, EntityCommandTreeBuilder::new);
        } else {
            impl$addToValue(value, EmptyCommandTreeBuilder::new);
        }

        // Abusing generics here...
       return map.put((ResourceLocation) key, value);
    }

    private static <T extends CommandTreeBuilder<T>> void impl$addToValue(Object value, Function<ClientCompletionKey<T>, T> key) {
        ((ArgumentTypes_EntryBridge<T>) value).bridge$setCommandTreeBuilderProvider(key);
    }

}
