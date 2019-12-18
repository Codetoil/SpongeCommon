package org.spongepowered.common.command.registrar.tree;

import com.google.gson.JsonObject;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;

public class RootCommandTreeBuilder extends AbstractCommandTreeBuilder<CommandTreeBuilder.Basic> implements CommandTreeBuilder.Basic {

    @Override
    void setType(JsonObject object) {
        object.addProperty("type", "root");
    }

}
