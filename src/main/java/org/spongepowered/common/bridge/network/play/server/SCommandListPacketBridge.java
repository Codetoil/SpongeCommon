package org.spongepowered.common.bridge.network.play.server;

import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.command.CommandSource;
import org.spongepowered.common.command.registrar.tree.RootCommandTreeBuilder;

public interface SCommandListPacketBridge {

    void bridge$addRootCommandTreeBuilder(RootCommandTreeBuilder rootCommandTreeBuilder);

    RootCommandNode<CommandSource> bridge$getRootCommandNode();

}
