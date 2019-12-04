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

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.manager.CommandMapping;
import org.spongepowered.api.command.manager.CommandFailedRegistrationException;
import org.spongepowered.api.command.registrar.CommandRegistrar;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.common.bridge.brigadier.tree.RootCommandNodeBridge;
import org.spongepowered.common.command.manager.SpongeCommandManager;
import org.spongepowered.common.command.manager.SpongeCommandMapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public abstract class SpongeCommandRegistrar<T extends Command> implements CommandRegistrar {

    private final CommandDispatcher<CommandCause> dispatcher = new CommandDispatcher<>();
    private final Map<String, T> commandMap = new TreeMap<>();
    private final CatalogKey catalogKey;

    SpongeCommandRegistrar(CatalogKey catalogKey) {
        this.catalogKey = catalogKey;
    }

    /**
     * <strong>Do not call this directly.</strong> This must ONLY be called from
     * {@link SpongeCommandManager}, else the manager won't know to redirect
     * to here!
     *
     * @param container The {@link PluginContainer} of the owning plugin
     * @param command The command to register
     * @param primaryAlias The primary alias
     * @param secondaryAliases The secondary aliases, for the mapping
     * @return The mapping
     * @throws CommandFailedRegistrationException If no mapping could be created.
     */
    public CommandMapping register(PluginContainer container, T command, String primaryAlias, String[] secondaryAliases)
            throws CommandFailedRegistrationException {
        if (this.dispatcher.findNode(Collections.singletonList(primaryAlias.toLowerCase())) != null) {
            // we have a problem
            throw new CommandFailedRegistrationException("The primary alias " + primaryAlias + " has already been registered.");
        }

        this.dispatcher.register(createNode(primaryAlias.toLowerCase(), command));
        this.commandMap.put(primaryAlias, command);
        return new SpongeCommandMapping(
                primaryAlias,
                ImmutableSet.copyOf(secondaryAliases),
                container,
                this
        );
    }

    @Override
    public CommandResult process(CommandCause cause, String command, String arguments) throws CommandException {
        try {
            return CommandResult.builder().setResult(this.dispatcher.execute(createCommandString(command, arguments), cause)).build();
        } catch (CommandSyntaxException e) {
            // We'll unwrap later.
            throw new CommandException(Text.of(e.getMessage()), e);
        }
    }

    @Override
    public List<String> suggestions(CommandCause cause, String command, String arguments) {
        try {
            return this.dispatcher.getCompletionSuggestions(
                            this.dispatcher.parse(createCommandString(command, arguments), cause)
                    ).join().getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Text> help(CommandCause cause, String command) {
        T commandEntry = this.commandMap.get(command.toLowerCase());
        if (commandEntry == null) {
            throw new IllegalArgumentException(command + " is not a valid a valid command!");
        }

        return commandEntry.getHelp(cause);
    }

    @Override
    public void unregister(CommandMapping mapping) {
        if (Sponge.getCommandManager().isRegistered(mapping)) {
            this.commandMap.remove(mapping.getPrimaryAlias());
            ((RootCommandNodeBridge<CommandCause>) this.dispatcher).bridge$removeNode(
                    this.dispatcher.findNode(Collections.singletonList(mapping.getPrimaryAlias()))
            );
        }
    }

    @Override
    public CatalogKey getKey() {
        return this.catalogKey;
    }

    private String createCommandString(String command, String argument) {
        if (argument.isEmpty()) {
            return command;
        }

        return command + " " + argument;
    }

    abstract LiteralArgumentBuilder<CommandCause> createNode(String primaryAlias, T command);
}
