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

package me.lucko.luckperms.forge;

import me.lucko.luckperms.common.cacheddata.result.TristateResult;
import me.lucko.luckperms.common.locale.TranslationManager;
import me.lucko.luckperms.common.query.QueryOptionsImpl;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.sender.SenderFactory;
import me.lucko.luckperms.common.verbose.VerboseCheckTarget;
import me.lucko.luckperms.common.verbose.event.CheckOrigin;
import me.lucko.luckperms.forge.capabilities.UserCapability;
import me.lucko.luckperms.forge.capabilities.UserCapabilityImpl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.luckperms.api.util.Tristate;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;

import java.util.Locale;
import java.util.UUID;

public class ForgeSenderFactory extends SenderFactory<LPForgePlugin, CommandSource> {
    public ForgeSenderFactory(LPForgePlugin plugin) {
        super(plugin);
    }

    @Override
    protected UUID getUniqueId(CommandSource commandSource) {
        if (commandSource.getEntity() instanceof PlayerEntity) {
            return commandSource.getEntity().getUUID();
        }
        return Sender.CONSOLE_UUID;
    }

    @Override
    protected String getName(CommandSource commandSource) {
        if (commandSource.getEntity() instanceof PlayerEntity) {
            return commandSource.getTextName();
        }
        return Sender.CONSOLE_NAME;
    }

    @Override
    protected void sendMessage(CommandSource sender, Component message) {
        Locale locale;
        if (sender.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) sender.getEntity();
            UserCapabilityImpl user = UserCapabilityImpl.get(player);
            locale = user.getLocale(player);
        } else {
            locale = null;
        }

        sender.sendSuccess(toNativeText(TranslationManager.render(message, locale)), false);
    }

    @Override
    protected Tristate getPermissionValue(CommandSource commandSource, String node) {
        if (commandSource.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) commandSource.getEntity();
            UserCapability user = UserCapabilityImpl.get(player);
            return user.checkPermission(node);
        }

        VerboseCheckTarget target = VerboseCheckTarget.internal(commandSource.getTextName());
        getPlugin().getVerboseHandler().offerPermissionCheckEvent(CheckOrigin.PLATFORM_API_HAS_PERMISSION, target, QueryOptionsImpl.DEFAULT_CONTEXTUAL, node, TristateResult.UNDEFINED);
        getPlugin().getPermissionRegistry().offer(node);
        return Tristate.UNDEFINED;
    }

    @Override
    protected boolean hasPermission(CommandSource commandSource, String node) {
        return getPermissionValue(commandSource, node).asBoolean();
    }

    @Override
    protected void performCommand(CommandSource sender, String command) {
        sender.getServer().getCommands().performCommand(sender, command);
    }

    @Override
    protected boolean isConsole(CommandSource sender) {
        return !(sender.getEntity() instanceof ServerPlayerEntity);
    }

    public static ITextComponent toNativeText(Component component) {
        return ITextComponent.Serializer.fromJson(GsonComponentSerializer.gson().serialize(component));
    }

}
