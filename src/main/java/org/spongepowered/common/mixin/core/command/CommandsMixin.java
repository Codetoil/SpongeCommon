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
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ICommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.SuggestionProviders;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.registrar.CommandRegistrar;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.command.manager.SpongeCommandCause;
import org.spongepowered.common.command.manager.SpongeCommandManager;
import org.spongepowered.common.command.registrar.BrigadierBackedCommandRegistrar;
import org.spongepowered.common.command.registrar.VanillaCommandRegistrar;
import org.spongepowered.common.command.registrar.tree.RootCommandTreeBuilder;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Shadow
    protected abstract void commandSourceNodesToSuggestionNodes(CommandNode<CommandSource> rootCommandSource,
            CommandNode<ISuggestionProvider> rootSuggestion, CommandSource source,
            Map<CommandNode<CommandSource>, CommandNode<ISuggestionProvider>> commandNodeToSuggestionNode);

    // We augment the CommandDispatcher with our own methods using a wrapper, so we need to make sure it's replaced here.
    @Redirect(method = "<init>", at = @At(
            value = "NEW",
            args = "class=com/mojang/brigadier/CommandDispatcher"
    ))
    private CommandDispatcher<ICommandSource> impl$useVanillaRegistrarAsDispatcher() {
        return VanillaCommandRegistrar.INSTANCE;
    }

    // We redirect to our own command manager, which might return to the dispatcher.
    @Redirect(method = "handleCommand", at = @At(value = "INVOKE",
            target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/StringReader;Ljava/lang/Object;)I"))
    private int impl$redirectExecuteCall(CommandDispatcher<?> commandDispatcher, StringReader input, Object source) {
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
    private void impl$addSuggestionsToCommandList(
            Commands commands,
            CommandNode<CommandSource> rootCommandSource,
            CommandNode<ISuggestionProvider> rootSuggestion,
            CommandSource source,
            Map<CommandNode<CommandSource>, CommandNode<ISuggestionProvider>> commandNodeToSuggestionNode) {

        // We start by letting the Vanilla code do its thing...
        this.commandSourceNodesToSuggestionNodes(rootCommandSource, rootSuggestion, source, commandNodeToSuggestionNode);

        CommandCause cause = CommandCause.of(Sponge.getCauseStackManager().getCurrentCause());

        // Now we take our command manager. Anything that is a Brigadier backed manager is easy...
        SpongeImpl.getRegistry().getCatalogRegistry().getAllOf(CommandRegistrar.class)
                .filter(x -> !(x instanceof VanillaCommandRegistrar))
                .forEach(x -> {
                    if (x instanceof BrigadierBackedCommandRegistrar) {
                        // use the node to throw it something similar to the Vanilla spec...
                        Map<CommandNode<CommandCause>, CommandNode<ISuggestionProvider>> map = new HashMap<>();
                        this.impl$createSuggestionNodes(
                                ((BrigadierBackedCommandRegistrar) x).getCommandNode(),
                                rootSuggestion,
                                cause,
                                map);
                    } else {
                        // get the command trees
                        RootCommandTreeBuilder treeBuilder = new RootCommandTreeBuilder();
                        x.completeCommandTree(cause, treeBuilder);

                        // TODO: create a Brig tree and merge
                    }
                });

        // Finally, add our aliases to the tree using redirects.
        SpongeCommandManager commandManager = SpongeImpl.getCommandManager();
        commandManager.getMappings().forEach((primary, mapping) -> {
            CommandNode<ISuggestionProvider> targetRedirect = rootSuggestion.getChild(primary);
            mapping.getAllAliases().forEach(alias -> {
                if (!alias.equals(primary)) {
                    rootSuggestion.addChild(
                            LiteralArgumentBuilder.<ISuggestionProvider>literal(alias).redirect(targetRedirect).build()
                    );
                }
            });
        });
    }

    // This method is almost a direct copy of commandSourceNodesToSuggestionNodes,
    // changing in CommandCause for CommandSource
    // TODO: Try to meld CommandSource into CommandCause?
    private void impl$createSuggestionNodes(
            CommandNode<CommandCause> rootCommandSource,
            CommandNode<ISuggestionProvider> rootSuggestion,
            CommandCause source,
            Map<CommandNode<CommandCause>, CommandNode<ISuggestionProvider>> commandNodeToSuggestionNode) {
        for(CommandNode<CommandCause> commandnode : rootCommandSource.getChildren()) {
            if (commandnode.canUse(source)) {
                ArgumentBuilder<ISuggestionProvider, ?> argumentbuilder = (ArgumentBuilder)commandnode.createBuilder();
                argumentbuilder.requires((p_197060_0_) -> true);
                if (argumentbuilder.getCommand() != null) {
                    argumentbuilder.executes((p_197053_0_) -> 0);
                }

                if (argumentbuilder instanceof RequiredArgumentBuilder) {
                    RequiredArgumentBuilder<ISuggestionProvider, ?> requiredargumentbuilder = (RequiredArgumentBuilder)argumentbuilder;
                    if (requiredargumentbuilder.getSuggestionsProvider() != null) {
                        requiredargumentbuilder.suggests(SuggestionProviders.ensureKnown(requiredargumentbuilder.getSuggestionsProvider()));
                    }
                }

                if (argumentbuilder.getRedirect() != null) {
                    argumentbuilder.redirect(commandNodeToSuggestionNode.get(argumentbuilder.getRedirect()));
                }

                CommandNode<ISuggestionProvider> commandnode1 = argumentbuilder.build();
                commandNodeToSuggestionNode.put(commandnode, commandnode1);
                rootSuggestion.addChild(commandnode1);
                if (!commandnode.getChildren().isEmpty()) {
                    this.impl$createSuggestionNodes(commandnode, commandnode1, source, commandNodeToSuggestionNode);
                }
            }
        }
    }
}
