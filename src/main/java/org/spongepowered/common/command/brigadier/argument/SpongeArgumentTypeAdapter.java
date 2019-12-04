package org.spongepowered.common.command.brigadier.argument;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.exception.ArgumentParseException;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.command.parameter.CommandContext;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.command.parameter.managed.ValueParameter;
import org.spongepowered.api.text.Text;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Adapts underlying {@link ArgumentType}s as {@link ValueParameter}s
 *
 * <p>For use with standard {@link ArgumentType}s</p>
 *
 * @param <T> The type of parameter
 */
public class SpongeArgumentTypeAdapter<T> implements ArgumentType<T>, ValueParameter<T> {

    private final ArgumentType<T> type;

    public SpongeArgumentTypeAdapter(ArgumentType<T> type) {
        this.type = type;
    }

    public ArgumentType<T> getUnderlyingType() {
        return this.type;
    }

    @Override
    public T parse(StringReader reader) throws CommandSyntaxException {
        return this.type.parse(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(com.mojang.brigadier.context.CommandContext<S> context, SuggestionsBuilder builder) {
        return this.type.listSuggestions(context, builder);
    }

    @Override public Collection<String> getExamples() {
        return this.type.getExamples();
    }

    @Override public Text getUsage(CommandCause cause, Text key) {
        return key;
    }

    @Override
    public List<String> complete(CommandContext context) {
        CompletableFuture<Suggestions> c =
                listSuggestions((com.mojang.brigadier.context.CommandContext) context, new SuggestionsBuilder("", 0));
        try {
            return c.get().getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            return ImmutableList.of();
        }
    }

    @Override
    public Optional<? extends T> getValue(Parameter.Key<? super T> parameterKey, ArgumentReader.Mutable reader, CommandContext.Builder context)
            throws ArgumentParseException {
        return Optional.empty();
    }



}
