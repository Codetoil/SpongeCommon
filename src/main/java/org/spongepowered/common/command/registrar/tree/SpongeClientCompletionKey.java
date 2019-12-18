package org.spongepowered.common.command.registrar.tree;

import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;

import java.util.function.Function;

public class SpongeClientCompletionKey<T extends CommandTreeBuilder<T>> implements ClientCompletionKey<T> {

    private final CatalogKey key;
    private final Function<ClientCompletionKey<T>, T> commandTreeBuilderConstructor;

    public SpongeClientCompletionKey(CatalogKey key, Function<ClientCompletionKey<T>, T> commandTreeBuilderConstructor) {
        this.key = key;
        this.commandTreeBuilderConstructor = commandTreeBuilderConstructor;
    }

    @Override
    public T createCommandTreeBuilder() {
        return this.commandTreeBuilderConstructor.apply(this);
    }

    @Override
    public String getNamespace() {
        return this.key.getNamespace();
    }

    @Override
    public String getValue() {
        return this.key.getValue();
    }

    @Override
    public int compareTo(CatalogKey o) {
        return this.key.compareTo(o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SpongeClientCompletionKey<?> that = (SpongeClientCompletionKey<?>) o;
        return this.key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }

}
