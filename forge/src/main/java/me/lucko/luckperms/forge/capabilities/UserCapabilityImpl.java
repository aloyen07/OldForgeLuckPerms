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

import me.lucko.luckperms.common.cacheddata.type.PermissionCache;
import me.lucko.luckperms.common.context.manager.QueryOptionsCache;
import me.lucko.luckperms.common.locale.TranslationManager;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.verbose.event.CheckOrigin;
import me.lucko.luckperms.forge.context.ForgeContextManager;

import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import net.minecraft.entity.player.ServerPlayerEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

public class UserCapabilityImpl implements UserCapability {

    public static @Nonnull UserCapabilityImpl get(@Nonnull ServerPlayerEntity player) {
        return (UserCapabilityImpl) player.getCapability(CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("Capability missing for " + player.getUUID()));
    }

    public static @Nullable UserCapabilityImpl getNullable(@Nonnull ServerPlayerEntity player) {
        return (UserCapabilityImpl) player.getCapability(CAPABILITY).resolve().orElse(null);
    }

    private boolean initialised = false;

    private User user;
    private QueryOptionsCache<ServerPlayerEntity> queryOptionsCache;
    private String language;
    private Locale locale;

    public UserCapabilityImpl() {

    }

    public void initialise(UserCapabilityImpl previous) {
        this.user = previous.user;
        this.queryOptionsCache = previous.queryOptionsCache;
        this.language = previous.language;
        this.locale = previous.locale;
        this.initialised = true;
    }

    public void initialise(User user, ServerPlayerEntity player, ForgeContextManager contextManager) {
        this.user = user;
        this.queryOptionsCache = new QueryOptionsCache<>(player, contextManager);
        this.initialised = true;
    }

    private void assertInitialised() {
        if (!this.initialised) {
            throw new IllegalStateException("Capability has not been initialised");
        }
    }

    @Override
    public Tristate checkPermission(String permission) {
        assertInitialised();

        if (permission == null) {
            throw new NullPointerException("permission");
        }

        return checkPermission(permission, this.queryOptionsCache.getQueryOptions());
    }

    @Override
    public Tristate checkPermission(String permission, QueryOptions queryOptions) {
        assertInitialised();

        if (permission == null) {
            throw new NullPointerException("permission");
        }

        if (queryOptions == null) {
            throw new NullPointerException("queryOptions");
        }

        PermissionCache cache = this.user.getCachedData().getPermissionData(queryOptions);
        return cache.checkPermission(permission, CheckOrigin.PLATFORM_API_HAS_PERMISSION).result();
    }

    public User getUser() {
        assertInitialised();
        return this.user;
    }

    @Override
    public QueryOptions getQueryOptions() {
        return getQueryOptionsCache().getQueryOptions();
    }

    public QueryOptionsCache<ServerPlayerEntity> getQueryOptionsCache() {
        assertInitialised();
        return this.queryOptionsCache;
    }

    public Locale getLocale(ServerPlayerEntity player) {
        if (this.language == null || !this.language.equals(player.getLanguage())) {
            this.language = player.getLanguage();
            this.locale = TranslationManager.parseLocale(this.language);
        }

        return this.locale;
    }
}