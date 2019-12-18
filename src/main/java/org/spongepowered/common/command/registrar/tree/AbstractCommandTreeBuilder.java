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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractCommandTreeBuilder<T extends CommandTreeBuilder<T>> implements CommandTreeBuilder<T> {

    @Nullable private Map<String, Object> properties = null;
    @Nullable private Set<String> redirects = null;
    @Nullable private Map<String, AbstractCommandTreeBuilder<?>> children = null;
    private boolean executable = false;

    @Override
    public T child(String key, Consumer<Basic> childNode) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(childNode);

        return child(key, LiteralCommandTreeBuilder::new, childNode);
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
        this.children.put(key.toLowerCase(), (AbstractCommandTreeBuilder<?>) childTreeBuilder);
        return getThis();
    }

    @Override
    public T redirect(String to) {
        Objects.requireNonNull(to);

        if (this.redirects == null) {
            this.redirects = new HashSet<>();
        }
        this.redirects.add(to.toLowerCase());

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

    public JsonObject toJson(JsonObject object) {
        setType(object);
        if (this.executable) {
            object.addProperty("executable", true);
        }

        if (this.children != null) {
            // create children
            JsonObject childrenObject = new JsonObject();
            for (Map.Entry<String, AbstractCommandTreeBuilder<?>> element : this.children.entrySet()) {
                childrenObject.add(element.getKey(), element.getValue().toJson(new JsonObject()));
            }
            object.add("children", childrenObject);
        }

        if (this.redirects != null) {
            JsonArray redirectObject = new JsonArray();
            for (String redirect : this.redirects) {
                redirectObject.add(redirect);
            }
        }

        if (this.properties != null) {
            JsonObject propertiesObject = null;
            for (Map.Entry<String, Object> property : this.properties.entrySet()) {
                if (property.getValue() != null) {
                    if (propertiesObject == null) {
                        propertiesObject = new JsonObject();
                    }

                    if (property.getValue() instanceof Number) {
                        propertiesObject.addProperty(property.getKey(), (Number) property.getValue());
                    } else if (property.getValue() instanceof Boolean) {
                        propertiesObject.addProperty(property.getKey(), (boolean) property.getValue());
                    } else {
                        propertiesObject.addProperty(property.getKey(), String.valueOf(property.getValue()));
                    }
                }
            }

            if (propertiesObject != null) {
                object.add("properties", propertiesObject);
            }
        }

        return object;
    }


    abstract void setType(JsonObject object);

    private void checkKey(String key) {
        if (this.children != null && this.children.containsKey(key.toLowerCase())) {
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
