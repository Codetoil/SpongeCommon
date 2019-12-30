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
package org.spongepowered.common.command.registrar;

import com.mojang.brigadier.tree.CommandNode;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.registrar.CommandRegistrar;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;

/**
 * This is an API in Common! For those plugins that decide that they wish to
 * use Brigadier, they should implement this interface. By doing so, we will
 * bypass the creation of our {@link CommandTreeBuilder} objects and pull
 * suggestions directly from the provided tree. This implementation uses this
 * for our own command work.
 *
 * <p>We obviously discourage the use of this "API" as it is implementation
 * bound. We do not guarantee this interface will not break from build to
 * build, it is provided as a courtesy.</p>
 *
 * <p><strong>USE AT YOUR OWN RISK.</strong></p>
 */
public interface BrigadierBackedCommandRegistrar extends CommandRegistrar {

    /**
     * Gets the backing Brigadier {@link CommandNode} for this registrar. You
     * <strong>do not</strong> need to filter out commands that users should
     * not be able to acess.
     *
     * @return The {@link CommandNode}.
     */
    CommandNode<CommandCause> getCommandNode();

}
