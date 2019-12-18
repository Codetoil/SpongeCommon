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
package org.spongepowered.common.command.brigadier.tree;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.command.ICommandSource;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandExecutor;
import org.spongepowered.api.command.exception.ArgumentParseException;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.command.parameter.managed.ValueParser;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.common.command.brigadier.SpongeStringReader;
import org.spongepowered.common.command.brigadier.argument.SpongeParameterArgumentType;
import org.spongepowered.common.command.brigadier.context.SpongeCommandContextBuilder;
import org.spongepowered.common.command.manager.SpongeCommandCause;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

import javax.annotation.Nullable;

public class SpongeArgumentCommandNode<T> extends ArgumentCommandNode<SpongeCommandCause, T> {
    private static final String REQUIRED_ARGUMENT_OPEN = "<";
    private static final String REQUIRED_ARGUMENT_CLOSE = ">";

    private static final String OPTIONAL_ARGUMENT_OPEN = "[";
    private static final String OPTIONAL_ARGUMENT_CLOSE = "]";

    private final Parameter.Value<T> parameter;
    private final Parameter.Key<T> key;

    public static <S> SpongeArgumentCommandNode<S> create(Parameter.Value<S> parameter, @Nullable CommandExecutor executor) {
        SpongeParameterArgumentType<S> parameterArgumentType =
                new SpongeParameterArgumentType<S>(parameter.getKey(), parameter.getParsers(), parameter.getCompleter());
        return new SpongeArgumentCommandNode<S>(
                parameter,
                cause -> parameter.getRequirement().test(cause),
                parameterArgumentType,
                executor
        );
    }

    private SpongeArgumentCommandNode(
            Parameter.Value<T> parameterValue,
            Predicate<SpongeCommandCause> requirementPredicate,
            SpongeParameterArgumentType<T> parameter,
            @Nullable CommandExecutor executor) {
        super(parameterValue.getKey().key(),
                parameter,
                executor == null ? null : new SpongeCommandExecutorWrapper(executor),
                requirementPredicate,
                null,
                null,
                false,
                parameter);
        this.parameter = parameterValue;
        this.key = parameterValue.getKey();
    }

    protected boolean isValidInput(CommandDispatcher<SpongeCommandCause> dispatcher, SpongeCommandCause commandSource, String input) {
        for (ValueParser<? extends T> param : this.parameter.getParsers()) {
            try {
                SpongeCommandContextBuilder contextBuilder = new SpongeCommandContextBuilder(dispatcher, commandSource, this, 0);
                final SpongeStringReader reader = new SpongeStringReader(input, contextBuilder);
                param.getValue(this.key, reader, contextBuilder);
                if (!reader.canRead() || reader.peek() == ' ') {
                    return true;
                }
            } catch (final ArgumentParseException ignored) {
                // ignored
            }
        }

        return false;
    }

    @Override
    public boolean isValidInput(String input) {
        return isValidInput(new CommandDispatcher<>(),
                new SpongeCommandCause(Sponge.getCauseStackManager().getCurrentCause()),
                input);
    }

    // TODO: Cause aware?
    @Override
    public String getUsageText() {
        if (this.parameter.isOptional()) {
            return OPTIONAL_ARGUMENT_OPEN + this.parameter.getKey() + OPTIONAL_ARGUMENT_CLOSE;
        } else {
            return REQUIRED_ARGUMENT_OPEN + this.parameter.getKey() + REQUIRED_ARGUMENT_CLOSE;
        }
    }

    @Override
    public void parse(StringReader reader, CommandContextBuilder<SpongeCommandCause> contextBuilder) throws CommandSyntaxException {
        SpongeStringReader mutableReader;
        SpongeCommandContextBuilder builder;
        if (reader instanceof SpongeStringReader) {
            mutableReader = (SpongeStringReader) reader;
            builder = ((SpongeStringReader) reader).getCommandContextBuilder();
        } else {
            if (contextBuilder instanceof SpongeCommandContextBuilder) {
                builder = (SpongeCommandContextBuilder) contextBuilder;
            } else {
                builder = SpongeCommandContextBuilder.createFrom(contextBuilder);
            }

            mutableReader = new SpongeStringReader(reader, builder);
        }

        parseArg(mutableReader, builder);
    }

    @Override
    protected String getSortedKey() {
        return this.parameter.getKey().key();
    }

    @Override
    public Collection<String> getExamples() {
        return ImmutableList.of();
    }

    public void parseArg(ArgumentReader.Mutable argReader, SpongeCommandContextBuilder contextBuilder) throws CommandSyntaxException {
        final int start = argReader.getCursor();
        T result = null;
        Iterator<ValueParser<? extends T>> parserIterator = this.parameter.getParsers().iterator();
        while (result == null && parserIterator.hasNext()) {
            try {
                result = parserIterator.next()
                        .getValue(this.key, argReader, (org.spongepowered.api.command.parameter.CommandContext.Builder) contextBuilder)
                        .orElse(null);
            } catch (ArgumentParseException e) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                        .dispatcherParseException().createWithContext((ImmutableStringReader) argReader, e.getSuperText());
            }
        }

        if (result == null) {
            if (this.parameter.isOptional()) {
                // if optional, then we just return, else, throw error.
                return;
            } else {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException()
                        .createWithContext((ImmutableStringReader) argReader.getImmutable(), "todo");
            }
        }

        contextBuilder.putEntry(this.parameter.getKey(), result);
        contextBuilder.withNode(this, new StringRange(start, argReader.getCursor()));
    }

    private static Predicate<ICommandSource> wrapCausePredicate(Predicate<Cause> causePredicate) {
        return commandSource -> {
            if (commandSource instanceof SpongeCommandCause) {
                return causePredicate.test(((SpongeCommandCause) commandSource).getCause());
            }

            return false;
        };
    }

}
