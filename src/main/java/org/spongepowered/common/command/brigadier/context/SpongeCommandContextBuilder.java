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
package org.spongepowered.common.command.brigadier.context;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.tree.CommandNode;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.common.bridge.brigadier.CommandContextBuilderBridge;
import org.spongepowered.common.command.manager.SpongeCommandCause;
import org.spongepowered.common.command.parameter.SpongeParameterKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class SpongeCommandContextBuilder extends CommandContextBuilder<SpongeCommandCause>
        implements org.spongepowered.api.command.parameter.CommandContext.Builder {

    public static SpongeCommandContextBuilder createFrom(CommandContextBuilder<SpongeCommandCause> original) {
        final SpongeCommandContextBuilder copy =
                new SpongeCommandContextBuilder(original.getDispatcher(),
                        original.getSource(),
                        original.getRootNode(),
                        original.getRange().getStart());
        CommandContextBuilderBridge<SpongeCommandCause> mixinCommandContextBuilder = (CommandContextBuilderBridge<SpongeCommandCause>) original;
        CommandContextBuilderBridge<SpongeCommandCause> copyMixinCommandContextBuilder = (CommandContextBuilderBridge<SpongeCommandCause>) copy;
        copy.withChild(original.getChild());
        copy.withCommand(original.getCommand());
        copyMixinCommandContextBuilder.bridge$putArguments(original.getArguments());
        copyMixinCommandContextBuilder.bridge$setRedirectModifier(mixinCommandContextBuilder.bridge$getRedirectModifier());
        copyMixinCommandContextBuilder.bridge$setFork(mixinCommandContextBuilder.bridge$isForks());
        copyMixinCommandContextBuilder.bridge$setStringRange(copy.getRange());
        return copy;
    }

    // The Sponge command system allows for multiple arguments to be stored under the same key.
    private final HashMap<Parameter.Key<?>, Collection<?>> arguments = new HashMap<>();

    public SpongeCommandContextBuilder(
            CommandDispatcher<SpongeCommandCause> dispatcher,
            SpongeCommandCause source,
            CommandNode<SpongeCommandCause> root,
            int start) {
        super(dispatcher, source, root, start);
    }

    @Override
    public SpongeCommandContextBuilder withArgument(String name, ParsedArgument<SpongeCommandCause, ?> argument) {
        // Generic wildcards begone!
        return withArgumentInternal(name, argument);
    }

    private <T> SpongeCommandContextBuilder withArgumentInternal(String name, ParsedArgument<SpongeCommandCause, T> argument) {
        Parameter.Key<T> objectKey = new SpongeParameterKey<T>(name, TypeToken.of((Class<T>) argument.getResult().getClass()));
        addToArgumentMap(objectKey, argument.getResult());
        super.withArgument(name, argument); // for getArguments and any mods that use this.
        return this;
    }

    @Override
    public SpongeCommandContextBuilder withSource(SpongeCommandCause source) {
        // Update the cause to include the command source into the context.
        super.withSource(source);
        return this;
    }

    public SpongeCommandContextBuilder copy() {
        final SpongeCommandContextBuilder copy = createFrom(this);
        copy.arguments.putAll(this.arguments);
        return copy;
    }

    @Override
    public Cause getCause() {
        return getSource().getCause();
    }

    @Override
    public boolean hasAny(Parameter.Key<?> key) {
        return this.arguments.containsKey(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOne(Parameter.Key<T> key) {
        SpongeParameterKey<T> spongeParameterKey = SpongeParameterKey.getSpongeKey(key);
        Collection<?> collection = getFrom(spongeParameterKey);
        if (collection.size() > 1) {
            throw new IllegalArgumentException("More than one entry was found for " + spongeParameterKey.toString());
        }

        return Optional.ofNullable((T) collection.iterator().next());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T requireOne(Parameter.Key<T> key) throws NoSuchElementException, IllegalArgumentException {
        SpongeParameterKey<T> spongeParameterKey = SpongeParameterKey.getSpongeKey(key);
        Collection<?> collection = getFrom(spongeParameterKey);
        if (collection.size() > 1) {
            throw new IllegalArgumentException("More than one entry was found for " + spongeParameterKey.toString());
        } else if (collection.isEmpty()) {
            throw new NoSuchElementException("No entry was found for " + spongeParameterKey.toString());
        }

        return (T) collection.iterator().next();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Collection<? extends T> getAll(Parameter.Key<T> key) {
        return (Collection<? extends T>) getFrom(SpongeParameterKey.getSpongeKey(key));
    }

    private Collection<?> getFrom(SpongeParameterKey<?> key) {
        Collection<?> collection = this.arguments.get(key);
        if (collection == null) {
            return ImmutableSet.of();
        }

        return collection;
    }

    @Override
    public <T> void putEntry(Parameter.Key<T> key, T object) {
        addToArgumentMap(SpongeParameterKey.getSpongeKey(key), object);
    }

    @Override
    public State getState() {
        return null;
    }

    @Override
    public void setState(State state) {

    }

    @Override
    public SpongeCommandContext build(final String input) {
        final CommandContextBuilder child = getChild();
        // TODO: this might not be needed for the derived class, come back when mixins are working
        final CommandContextBuilderBridge<SpongeCommandCause> mixinCommandContextBuilder = (CommandContextBuilderBridge<SpongeCommandCause>) this;
        return new SpongeCommandContext(
                getSource(),
                input,
                getArguments(),
                ImmutableMap.copyOf(this.arguments),
                getCommand(),
                getNodes(),
                getRange(),
                child == null ? null : child.build(input),
                mixinCommandContextBuilder.bridge$getRedirectModifier(),
                mixinCommandContextBuilder.bridge$isForks());
    }

    @Override
    public Builder reset() {
        return null;
    }

    private <T> void addToArgumentMap(Parameter.Key<T> key, T value) {
        ((List<T>) this.arguments.computeIfAbsent(key, k -> new ArrayList<>())).add(value);
    }

}
