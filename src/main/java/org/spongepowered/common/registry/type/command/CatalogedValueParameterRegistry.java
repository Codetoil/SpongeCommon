package org.spongepowered.common.registry.type.command;

import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.command.parameter.managed.standard.CatalogedValueParameter;
import org.spongepowered.api.registry.AdditionalCatalogRegistryModule;

import java.util.Collection;
import java.util.Optional;

public class CatalogedValueParameterRegistry implements AdditionalCatalogRegistryModule<CatalogedValueParameter<?>> {



    @Override
    public void registerAdditionalCatalog(CatalogedValueParameter<?> extraCatalog) {

    }

    @Override
    public Optional<CatalogedValueParameter<?>> get(CatalogKey key) {
        return Optional.empty();
    }

    @Override
    public Collection<CatalogedValueParameter<?>> getAll() {
        return null;
    }
}
