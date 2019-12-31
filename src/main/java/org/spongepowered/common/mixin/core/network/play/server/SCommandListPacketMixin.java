package org.spongepowered.common.mixin.core.network.play.server;

import net.minecraft.command.arguments.SuggestionProviders;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SCommandListPacket;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.common.bridge.command.argument.ArgumentTypes_EntryBridge;
import org.spongepowered.common.bridge.network.play.server.SCommandListPacketBridge;
import org.spongepowered.common.command.registrar.tree.AbstractCommandTreeBuilder;
import org.spongepowered.common.command.registrar.tree.ArgumentCommandTreeBuilder;
import org.spongepowered.common.command.registrar.tree.RootCommandTreeBuilder;

import java.util.Map;

@Mixin(SCommandListPacket.class)
public abstract class SCommandListPacketMixin implements SCommandListPacketBridge {

    @Nullable private RootCommandTreeBuilder impl$commandTreeBuilder;

    @Override
    public void bridge$addRootCommandTreeBuilder(RootCommandTreeBuilder rootCommandTreeBuilder) {
        if (this.impl$commandTreeBuilder == null) {
            this.impl$commandTreeBuilder = new RootCommandTreeBuilder();
        }

        this.impl$commandTreeBuilder.addChildren(rootCommandTreeBuilder.getChildren());
    }

    // This adds our own commands in that don't make use of Brigadier.
    private void impl$writeCommandNode(
            PacketBuffer buf,
            @Nullable String key,
            AbstractCommandTreeBuilder<?> node,
            Map<AbstractCommandTreeBuilder<?>, Integer> nodeIds) {
        buf.writeByte(node.getFlags());
        buf.writeVarInt(node.getChildren().size());

        for(AbstractCommandTreeBuilder<?> treeNode : node.getChildren().values()) {
            buf.writeVarInt(nodeIds.get(treeNode));
        }

        // Redirects are going to be a nightmare.
        // TODO: Node counting
        if (node.getRedirect() != null) {
            buf.writeVarInt(nodeIds.get(node.getRedirect()));
        }

        if (key != null) {
            buf.writeString(key);
            if (node instanceof ArgumentCommandTreeBuilder) {
                ArgumentCommandTreeBuilder<?> treeBuilder = (ArgumentCommandTreeBuilder<?>) node;
                ArgumentTypes_EntryBridge<?, ?> entry = (ArgumentTypes_EntryBridge<?, ?>) treeBuilder.getParser();
                buf.writeResourceLocation(entry.accessor$getResourceLocation());
                ((ArgumentCommandTreeBuilder<?>) node).applyProperties(buf);
                if (treeBuilder.isCustomSuggestions()) {
                    buf.writeResourceLocation(SuggestionProviders.getId(SuggestionProviders.ASK_SERVER));
                }
            }
        }

    }

}
