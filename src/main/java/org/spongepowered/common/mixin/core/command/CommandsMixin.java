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
package org.spongepowered.common.mixin.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ICommandSource;
import net.minecraft.command.ISuggestionProvider;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.command.registrar.VanillaCommandRegistrar;

import java.util.Map;

@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Shadow
    protected abstract void commandSourceNodesToSuggestionNodes(CommandNode<CommandSource> rootCommandSource,
            CommandNode<ISuggestionProvider> rootSuggestion, CommandSource source,
            Map<CommandNode<CommandSource>, CommandNode<ISuggestionProvider>> commandNodeToSuggestionNode);

    /**
     * @author dualspiral, 30th November, 2019
     * @reason We augment the CommandDispatcher with our own methods
     *         using a wrapper, so we need to make sure it's replaced
     *         here.
     */
    @Redirect(method = "<init>", at = @At(
            value = "NEW",
            args = "class=com/mojang/brigadier/CommandDispatcher"
    ))
    private CommandDispatcher<ICommandSource> impl$useVanillaRegistrarAsDispatcher() {
        return VanillaCommandRegistrar.INSTANCE;
    }

    /**
     * @author dualspiral, 30th November, 2019
     * @reason We redirect to our own command manager, which might return to the dispatcher.
     */
    @Redirect(method = "handleCommand", at = @At(value = "INVOKE",
            target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/StringReader;Ljava/lang/Object;)I"))
    private int impl$redirectExecuteCall(CommandDispatcher commandDispatcher, StringReader input, Object source) {
        // We know that the object type will be ICommandSource
        ICommandSource commandSource = (ICommandSource) source;
        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(commandSource);
            // TODO: Handle command
            return 0;
        }
    }

    @Redirect(method = "send", at =
        @At(
                value = "INVOKE",
                target = "Lnet/minecraft/command/Commands;commandSourceNodesToSuggestionNodes"
                + "(Lcom/mojang/brigadier/tree/CommandNode;Lcom/mojang/brigadier/tree/CommandNode;Lnet/minecraft/command/CommandSource;Ljava/util/Map;)V"))
    private void impl$addSuggestionsToCommandList(Commands commands, CommandNode<CommandSource> rootCommandSource,
            CommandNode<ISuggestionProvider> rootSuggestion, CommandSource source,
            Map<CommandNode<CommandSource>, CommandNode<ISuggestionProvider>> commandNodeToSuggestionNode) {

        // We start by letting the Vanilla code do its thing...
        this.commandSourceNodesToSuggestionNodes(rootCommandSource, rootSuggestion, source, commandNodeToSuggestionNode);

        // TODO: Then, redirect nodes that are aliases in the CommandManager
        // TODO: Then we use our own objects to append to the tree by looping through all other registrars.
    }

}
