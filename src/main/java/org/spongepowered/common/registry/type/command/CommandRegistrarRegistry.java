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
package org.spongepowered.common.registry.type.command;

import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.command.registrar.CommandRegistrar;
import org.spongepowered.common.command.registrar.SpongeManagedCommandRegistrar;
import org.spongepowered.common.command.registrar.SpongeRawCommandRegistrar;
import org.spongepowered.common.command.registrar.VanillaCommandRegistrar;
import org.spongepowered.common.registry.SpongeAdditionalCatalogRegistryModule;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CommandRegistrarRegistry implements SpongeAdditionalCatalogRegistryModule<CommandRegistrar> {

    private final Map<CatalogKey, CommandRegistrar> registrars = new HashMap<>();

    @Override
    public boolean allowsApiRegistration() {
        return true;
    }

    @Override
    public void registerAdditionalCatalog(CommandRegistrar extraCatalog) {
        if (!this.registrars.containsKey(extraCatalog.getKey())) {
            this.registrars.put(extraCatalog.getKey(), extraCatalog);
        } else {
            throw new IllegalArgumentException("Catalog with key ID " + extraCatalog.getKey().toString() + " has already been added.");
        }
    }

    @Override
    public Optional<CommandRegistrar> get(CatalogKey key) {
        return Optional.ofNullable(this.registrars.get(key));
    }

    @Override
    public Collection<CommandRegistrar> getAll() {
        return this.registrars.values();
    }

    @Override
    public void registerDefaults() {
        this.registrars.put(VanillaCommandRegistrar.CATALOG_KEY, VanillaCommandRegistrar.INSTANCE);
        this.registrars.put(SpongeManagedCommandRegistrar.CATALOG_KEY, SpongeManagedCommandRegistrar.INSTANCE);
        this.registrars.put(SpongeRawCommandRegistrar.CATALOG_KEY, SpongeRawCommandRegistrar.INSTANCE);
    }

}
