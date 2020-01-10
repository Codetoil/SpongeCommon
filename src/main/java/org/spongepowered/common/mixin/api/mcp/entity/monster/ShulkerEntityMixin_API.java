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
package org.spongepowered.common.mixin.api.mcp.entity.monster;

import net.minecraft.entity.monster.ShulkerEntity;
import org.spongepowered.api.data.value.Value;
import org.spongepowered.api.entity.living.golem.Shulker;
import org.spongepowered.api.entity.projectile.EntityTargetingProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.common.entity.projectile.ProjectileLauncher;
import org.spongepowered.common.mixin.api.mcp.entity.passive.GolemEntityMixin_API;

import java.util.Optional;
import java.util.Set;

@Mixin(ShulkerEntity.class)
public abstract class ShulkerEntityMixin_API extends GolemEntityMixin_API implements Shulker {

    @Override
    public <P extends EntityTargetingProjectile> Optional<P> launchWithTarget(final Class<P> projectileClass,
        final org.spongepowered.api.entity.Entity target) {
        return ProjectileLauncher.launchWithArgs(projectileClass, Shulker.class, this, null, target);
    }

    @Override
    protected Set<Value.Immutable<?>> api$getVanillaValues() {
        final Set<Value.Immutable<?>> values = super.api$getVanillaValues();

        values.add(this.color().asImmutable());
        values.add(this.direction().asImmutable());

        return values;
    }

}
