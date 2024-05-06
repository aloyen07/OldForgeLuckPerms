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

package me.lucko.luckperms.forge.mixins;

import me.lucko.luckperms.forge.network.LuckPermsServerLoginNetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.ProtocolType;
import net.minecraft.network.handshake.ServerHandshakeNetHandler;
import net.minecraft.network.handshake.client.CHandshakePacket;
import net.minecraft.network.login.server.SDisconnectLoginPacket;
import net.minecraft.network.status.ServerStatusNetHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerHandshakeNetHandler.class)
public class ServerLoginNetHandlerMixin {


    @Shadow @Final private NetworkManager connection;

    @Shadow @Final private MinecraftServer server;

    @Shadow @Final private static ITextComponent IGNORE_STATUS_REASON;

    @Inject(method = "handleIntention", at = @At("HEAD"), cancellable = true)
    public void handleInjectionMixin(CHandshakePacket p_147383_1_, CallbackInfo ci) {

        if (ServerLifecycleHooks.handleServerLogin(p_147383_1_, this.connection)) {
            switch (p_147383_1_.getIntention()) {
                case LOGIN:
                    this.connection.setProtocol(ProtocolType.LOGIN);
                    if (p_147383_1_.getProtocolVersion() != SharedConstants.getCurrentVersion().getProtocolVersion()) {
                        TranslationTextComponent message = luckperms$getMessage(p_147383_1_);

                        this.connection.send(new SDisconnectLoginPacket(message));
                        this.connection.disconnect(message);
                    } else {
                        this.connection.setListener(new LuckPermsServerLoginNetHandler(this.server, this.connection));
                    }
                    break;
                case STATUS:
                    if (this.server.repliesToStatus()) {
                        this.connection.setProtocol(ProtocolType.STATUS);
                        this.connection.setListener(new ServerStatusNetHandler(this.server, this.connection));
                    } else {
                        this.connection.disconnect(IGNORE_STATUS_REASON);
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("Invalid intention " + p_147383_1_.getIntention());
            }

        }

        ci.cancel();
    }

    @Unique
    private static TranslationTextComponent luckperms$getMessage(CHandshakePacket p_147383_1_) {
        TranslationTextComponent message;
        if (p_147383_1_.getProtocolVersion() < 754) {
            message = new TranslationTextComponent("multiplayer.disconnect.outdated_client", SharedConstants.getCurrentVersion().getName());
        } else {
            message = new TranslationTextComponent("multiplayer.disconnect.incompatible", SharedConstants.getCurrentVersion().getName());
        }
        return message;
    }
}
