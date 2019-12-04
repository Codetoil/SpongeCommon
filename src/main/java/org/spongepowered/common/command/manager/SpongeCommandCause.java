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
package org.spongepowered.common.command.manager;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ICommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.scoreboard.Team;
import org.spongepowered.common.command.CommandHelper;
import org.spongepowered.common.text.SpongeTexts;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpongeCommandCause implements CommandCause, ICommandSource, ISuggestionProvider {

    private final Cause cause;
    private final ICommandSource wrappedSource;

    public SpongeCommandCause(Cause cause) {
        this.cause = cause;
        this.wrappedSource = CommandHelper.getCommandSource(cause);
    }

    @Override
    public Cause getCause() {
        return this.cause;
    }

    public ICommandSource getWrappedSource() {
        return this.wrappedSource;
    }

    @Override
    public void sendMessage(ITextComponent component) {
        CommandHelper.getTargetMessageChannel(this.cause).send(SpongeTexts.toText(component));
    }

    @Override
    public boolean shouldReceiveFeedback() {
        return this.wrappedSource.shouldReceiveFeedback();
    }

    @Override
    public boolean shouldReceiveErrors() {
        return this.wrappedSource.shouldReceiveFeedback();
    }

    @Override
    public boolean allowLogging() {
        return this.wrappedSource.allowLogging();
    }

    @Override
    public Collection<String> getPlayerNames() {
        return Sponge.getServer().getOnlinePlayers().stream().map(User::getName).collect(Collectors.toList());
    }

    @Override
    public Collection<String> getTeamNames() {
        return Sponge.getServer().getServerScoreboard().map(x -> x.getTeams().stream().map(Team::getName).collect(Collectors.toList()))
                .orElseGet(ImmutableList::of);
    }

    @Override
    public Collection<ResourceLocation> getSoundResourceLocations() {
        return Registry.SOUND_EVENT.keySet();
    }

    @Override
    public Stream<ResourceLocation> getRecipeResourceLocations() {
        return ((MinecraftServer) Sponge.getServer()).getRecipeManager().getKeys();
    }

    // This seems to be null in Mojang impl. Will revisit later... cv
    @Override
    @Nullable
    public CompletableFuture<Suggestions> getSuggestionsFromServer(CommandContext<ISuggestionProvider> context,
            SuggestionsBuilder suggestionsBuilder) {
        return null;
    }

    @Override
    public boolean hasPermissionLevel(int p_197034_1_) {
        // TODO: Permissions framework needs to intercept this.
        if (this.wrappedSource instanceof CommandSource) {
            return ((CommandSource) this.wrappedSource).hasPermissionLevel(p_197034_1_);
        }
        return p_197034_1_ <= 0;
    }
}
