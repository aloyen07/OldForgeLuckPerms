/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.forge.capabilities;

import me.lucko.luckperms.forge.LPForgeBootstrap;
import me.lucko.luckperms.forge.LPForgePlugin;
import me.lucko.luckperms.forge.mixins.CapabilityProviderMixin;
import me.lucko.luckperms.forge.mixins.LivingEntityMixin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class UserCapabilityListener {

//    @SubscribeEvent
//    public void onRegisterCapabilities(AttachCapabilitiesEvent<Entity> event) {
//        event.register(UserCapabilityImpl.class);
//    }

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof ServerPlayerEntity)) {
            return;
        }

        event.addCapability(UserCapability.IDENTIFIER, new UserCapabilityProvider(new UserCapabilityImpl()));
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }

        PlayerEntity previousPlayer = event.getOriginal();
        PlayerEntity currentPlayer = event.getPlayer();


        // TODO
        ((CapabilityProviderMixin) previousPlayer).invokerReviveCaps();
        try {
            UserCapabilityImpl previous = UserCapabilityImpl.get((ServerPlayerEntity) previousPlayer);
            UserCapabilityImpl current = UserCapabilityImpl.get((ServerPlayerEntity) currentPlayer);

            current.initialise(previous);
            current.getQueryOptionsCache().invalidate();
        } finally {
            ((LivingEntityMixin) previousPlayer).invokerInvalidateCaps();
        }
    }

    private static final class UserCapabilityProvider implements ICapabilityProvider {
        private final UserCapabilityImpl userCapability;

        private UserCapabilityProvider(UserCapabilityImpl userCapability) {
            this.userCapability = userCapability;
        }

        @SuppressWarnings("unchecked")
        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            if (!cap.getName().equals(UserCapabilityImpl.CAPABILITY.getName())) {
                return LazyOptional.empty();
            }

            return LazyOptional.of(() -> (T) this.userCapability);
        }
    }

}
