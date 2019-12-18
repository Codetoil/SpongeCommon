package org.spongepowered.common.command.registrar.tree;

import com.google.gson.JsonObject;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;

public class ArgumentCommandTreeBuilder<T extends CommandTreeBuilder<T>> extends AbstractCommandTreeBuilder<T> {

    private final ClientCompletionKey<T> parameterType;

    public ArgumentCommandTreeBuilder(ClientCompletionKey<T> parameterType) {
        this.parameterType = parameterType;
    }

    @Override
    void setType(JsonObject object) {
        object.addProperty("type", "argument");
        object.addProperty("parser", this.parameterType.getNamespace() + ":" + this.parameterType.getValue());
    }

}
