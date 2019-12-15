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
package org.spongepowered.common.command.registrar.tree;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractCommandTreeBuilder<T extends CommandTreeBuilder<T>> implements CommandTreeBuilder<T> {

    @Nullable private final ClientCompletionKey<T> parameterType;

    @Nullable private Map<String, Object> properties = null;
    @Nullable private Map<String, String> redirects = null;
    @Nullable private Map<String, CommandTreeBuilder<?>> children = null;
    private boolean executable = false;

    public AbstractCommandTreeBuilder(@Nullable ClientCompletionKey<T> parameterType) {
        this.parameterType = parameterType;
    }

    @Override
    public T child(String key, Consumer<Empty> childNode) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(childNode);

        return child(key, EmptyCommandTreeBuilder::literalTree, childNode);
    }

    @Override
    public <S extends CommandTreeBuilder<S>> T child(String key, ClientCompletionKey<S> completionKey, Consumer<S> childNode) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(completionKey);
        Objects.requireNonNull(childNode);
        return child(key, completionKey::createCommandTreeBuilder, childNode);
    }

    private <S extends CommandTreeBuilder<S>> T child(
            String key,
            Supplier<S> builderSupplier,
            Consumer<S> childNode) {
        checkKey(key);
        if (this.children == null) {
            this.children = new HashMap<>();
        }

        S childTreeBuilder = builderSupplier.get();
        childNode.accept(childTreeBuilder);
        this.children.put(key.toLowerCase(), childTreeBuilder);
        return getThis();
    }

    @Override
    public T redirect(String key, String to) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(to);

        if (this.redirects == null) {
            this.redirects = new HashMap<>();
        }
        this.redirects.put(key.toLowerCase(), to.toLowerCase());

        return getThis();
    }

    @Override
    public T executable() {
        this.executable = true;
        return getThis();
    }

    @Override
    public T property(String key, String value) {
        return addProperty(key, value);
    }

    @Override
    public T property(String key, long value) {
        return addProperty(key, value);
    }

    @Override
    public T property(String key, double value) {
        return addProperty(key, value);
    }

    @Override
    public T property(String key, boolean value) {
        return addProperty(key, value);
    }

    @SuppressWarnings("unchecked")
    protected T getThis() {
        return (T) this;
    }

    private void checkKey(String key) {
        if (this.children != null && this.children.containsKey(key.toLowerCase()) ||
                (this.redirects != null && this.redirects.containsKey(key.toLowerCase()))) {
            throw new IllegalArgumentException("Key " + key + " is already set.");
        }
    }

    protected T addProperty(String key, Object value) {
        Objects.requireNonNull(key, "Property key must not be null");
        Objects.requireNonNull(value, "Property value must not be null");
        if (this.properties == null) {
            this.properties = new HashMap<>();
        }

        this.properties.put(key, value);
        return getThis();
    }

    protected T removeProperty(String key) {
        if (this.properties != null) {
            this.properties.remove(key.toLowerCase());
        }

        return getThis();
    }
}
