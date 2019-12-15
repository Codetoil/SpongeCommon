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
package org.spongepowered.common.command.registrar;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.ICommandSource;
import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.manager.CommandMapping;
import org.spongepowered.api.command.registrar.CommandRegistrar;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.bridge.brigadier.tree.RootCommandNodeBridge;
import org.spongepowered.common.command.CommandHelper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class VanillaCommandRegistrar extends CommandDispatcher<ICommandSource> implements CommandRegistrar {

    public static final VanillaCommandRegistrar INSTANCE = new VanillaCommandRegistrar();
    public static final CatalogKey CATALOG_KEY = CatalogKey.builder().namespace(SpongeImpl.getMinecraftPlugin()).value("brigadier").build();

    private VanillaCommandRegistrar() {}

    // For mods and others that use this. We get the plugin container from the CauseStack
    // TODO: Make sure this is valid. For Forge, I suspect we'll have done this in a context of some sort.
    @Override
    public LiteralCommandNode<ICommandSource> register(LiteralArgumentBuilder<ICommandSource> command) {
        // Get the plugin container
        PluginContainer container = Sponge.getCauseStackManager().getCurrentCause().first(PluginContainer.class)
                .orElseThrow(() -> new IllegalStateException("Cannot register command without knowing its origin."));

        // Get the builder and the first literal.
        String requestedAlias = command.getLiteral();

        // This will throw an error if there is an issue.
        Sponge.getCommandManager()
                .registerAlias(
                        this,
                        container,
                        requestedAlias
                );

        // Let the registration happen.
        // TODO: Permission check
        return super.register(command);
    }

    @Override
    public CommandResult process(CommandCause cause, String command, String arguments) throws CommandException {
        ICommandSource source = CommandHelper.getCommandSource(cause.getCause());
        try {
            int result = execute(command + " " + arguments, source);
            return CommandResult.builder().setResult(result).build();
        } catch (CommandSyntaxException e) {
            throw new CommandException(Text.of(e.getMessage()), e);
        }
    }

    @Override
    public List<String> suggestions(CommandCause cause, String command, String arguments) {
        ICommandSource source = CommandHelper.getCommandSource(cause.getCause());
        CompletableFuture<Suggestions> suggestionsCompletableFuture = getCompletionSuggestions(parse(command + " " + arguments, source));
        // TODO: Fix so that we keep suggestions in the Mojang format
        return suggestionsCompletableFuture.join().getList().stream().map(Suggestion::getText).collect(Collectors.toList());
    }

    @Override
    public Optional<Text> help(CommandCause cause, String command) {
        CommandNode<ICommandSource> node = this.findNode(Collections.singletonList(command));
        if (node != null) {
            return Optional.of(Text.of(getSmartUsage(node, CommandHelper.getCommandSource(cause.getCause()))));
        }

        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void unregister(CommandMapping mapping) {
        if (!Sponge.getCommandManager().isRegistered(mapping)) {
            ((RootCommandNodeBridge<ICommandSource>) getRoot()).bridge$removeNode(getRoot().getChild(mapping.getPrimaryAlias()));
        }
    }

    @Override
    public void completeCommandTree(CommandTreeBuilder builder) {

    }

    @Override
    public CatalogKey getKey() {
        return CATALOG_KEY;
    }

}
