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

package me.lucko.luckperms.forge.service;

import com.mojang.authlib.GameProfile;
import me.lucko.luckperms.forge.LPForgePlugin;
import me.lucko.luckperms.forge.capabilities.UserCapability;

import me.lucko.luckperms.forge.capabilities.UserCapabilityImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.IPermissionHandler;
import net.minecraftforge.server.permission.context.IContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class ForgePermissionHandler implements IPermissionHandler {

    //public static final ResourceLocation IDENTIFIER = new ResourceLocation(LPForgeBootstrap.ID, "permission_handler");

    private final LPForgePlugin plugin;
    // Node - Description
    private final HashMap<String, String> permissionNodes;

    public ForgePermissionHandler(LPForgePlugin plugin, HashMap<String, String> permissionNodes) {
        this.plugin = plugin;
        this.permissionNodes = new HashMap<>(permissionNodes);

        for (String node : this.permissionNodes.keySet()) {
            this.plugin.getPermissionRegistry().insert(node);
        }
    }

    @Override
    public void registerNode(@Nonnull String node,
                             @Nonnull DefaultPermissionLevel defaultPermissionLevel,
                             @Nonnull String description) {
        permissionNodes.put(node, description);
        this.plugin.getPermissionRegistry().insert(node);
    }

    @Override
    @Nonnull
    public Collection<String> getRegisteredNodes() {
        return permissionNodes.keySet();
    }

    @Override
    public boolean hasPermission(@Nonnull GameProfile gameProfile, @Nonnull String s, @Nullable IContext iContext) {
        Optional<MinecraftServer> serverOptional = this.plugin.getBootstrap().getServer();

        if (serverOptional.isPresent()) {
            return Objects.requireNonNull(((DedicatedServer) serverOptional.get()).getPlayerList().getPlayer(gameProfile.getId()))
                    .getCapability(UserCapabilityImpl.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Player capability not found!"))
                    .hasPermission(s);
        } else {
            throw new IllegalStateException("MinecraftServer is not initialized!");
        }
    }

    @Override
    @Nonnull
    public String getNodeDescription(@Nonnull String s) {
        return String.valueOf(permissionNodes.get(s));
    }
}
