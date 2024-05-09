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

package me.lucko.luckperms.forge.network;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import me.lucko.luckperms.forge.events.PlayerNegotiationEvent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.ServerLoginNetHandler;
import net.minecraft.network.login.client.CCustomPayloadLoginPacket;
import net.minecraft.network.login.client.CEncryptionResponsePacket;
import net.minecraft.network.login.client.CLoginStartPacket;
import net.minecraft.network.login.server.SDisconnectLoginPacket;
import net.minecraft.network.login.server.SEnableCompressionPacket;
import net.minecraft.network.login.server.SEncryptionRequestPacket;
import net.minecraft.network.login.server.SLoginSuccessPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.CryptException;
import net.minecraft.util.CryptManager;
import net.minecraft.util.DefaultUncaughtExceptionHandler;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.fml.network.NetworkHooks;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class LuckPermsServerLoginNetHandler extends ServerLoginNetHandler {

    private static final AtomicInteger UNIQUE_THREAD_ID = new AtomicInteger(0);
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Random RANDOM = new Random();
    private final byte[] nonce = new byte[4];
    private final MinecraftServer server;
    public final NetworkManager connection;
    private State state;
    private int tick;
    private GameProfile gameProfile;
    private ServerPlayerEntity delayedAcceptPlayer;

    public LuckPermsServerLoginNetHandler(MinecraftServer server, NetworkManager networkManager) {
        super(server, networkManager);
        this.state = State.HELLO;
        this.server = server;
        this.connection = networkManager;
        RANDOM.nextBytes(this.nonce);
    }

    @Override
    public void tick() {
        if (this.state == State.NEGOTIATING) {
            boolean negotiationComplete = NetworkHooks.tickNegotiation(this, this.connection, this.delayedAcceptPlayer);
            if (negotiationComplete) {
                this.state = State.READY_TO_ACCEPT;
            }
        } else if (this.state == State.READY_TO_ACCEPT) {
            this.handleAcceptedLogin();
        } else if (this.state == State.DELAY_ACCEPT) {
            ServerPlayerEntity serverplayerentity = this.server.getPlayerList().getPlayer(this.gameProfile.getId());
            if (serverplayerentity == null) {
                this.state = State.READY_TO_ACCEPT;
                this.server.getPlayerList().placeNewPlayer(this.connection, this.delayedAcceptPlayer);
                this.delayedAcceptPlayer = null;
            }
        }

        if (this.tick++ == 600) {
            this.disconnect(new TranslationTextComponent("multiplayer.disconnect.slow_login"));
        }

    }

    @Override
    public void disconnect(@Nonnull ITextComponent reason) {
        try {
            LOGGER.info("Disconnecting {}: {}", this.getUserName(), reason.getString());
            this.connection.send(new SDisconnectLoginPacket(reason));
            this.connection.disconnect(reason);
        } catch (Exception ex) {
            LOGGER.error("Error whilst disconnecting player", ex);
        }

    }

    @Override
    public void handleAcceptedLogin() {
        if (!this.gameProfile.isComplete()) {
            this.gameProfile = this.createFakeProfile(this.gameProfile);
        }

        ITextComponent text = this.server.getPlayerList().canPlayerLogin(this.connection.getRemoteAddress(), this.gameProfile);
        if (text != null) {
            this.disconnect(text);
        } else {
            this.state = State.ACCEPTED;
            if (this.server.getCompressionThreshold() >= 0 && !this.connection.isMemoryConnection()) {
                this.connection.send(new SEnableCompressionPacket(this.server.getCompressionThreshold()),
                        (acceptedLogin) -> this.connection.setupCompression(this.server.getCompressionThreshold()));
            }

            this.connection.send(new SLoginSuccessPacket(this.gameProfile));
            ServerPlayerEntity player = this.server.getPlayerList().getPlayer(this.gameProfile.getId());
            if (player != null) {
                this.state = State.DELAY_ACCEPT;
                this.delayedAcceptPlayer = this.server.getPlayerList().getPlayerForLogin(this.gameProfile);
            } else {
                this.server.getPlayerList().placeNewPlayer(this.connection, this.server.getPlayerList().getPlayerForLogin(this.gameProfile));
            }
        }
    }

    @Override
    @Nonnull
    public String getUserName() {
        return this.gameProfile != null ? this.gameProfile + " (" + this.connection.getRemoteAddress() + ")" : String.valueOf(this.connection.getRemoteAddress());
    }


    @Override
    public void handleHello(CLoginStartPacket loginPacket) {
        Validate.validState(this.state == State.HELLO, "Unexpected hello packet");
        this.gameProfile = loginPacket.getGameProfile();
        if (this.server.usesAuthentication() && !this.connection.isMemoryConnection()) {
            this.state = State.KEY;
            this.connection.send(new SEncryptionRequestPacket("", this.server.getKeyPair().getPublic().getEncoded(), this.nonce));
        } else {
            this.state = State.NEGOTIATING;
            MinecraftForge.EVENT_BUS.post(new PlayerNegotiationEvent(this.connection, this.gameProfile));
        }
    }

    @Override
    public void handleKey(CEncryptionResponsePacket encryptionPacket) {
        Validate.validState(this.state == State.KEY, "Unexpected key packet");
        PrivateKey privatekey = this.server.getKeyPair().getPrivate();

        final String s;
        try {
            if (!Arrays.equals(this.nonce, encryptionPacket.getNonce(privatekey))) {
                throw new IllegalStateException("Protocol error");
            }

            SecretKey secretKey = encryptionPacket.getSecretKey(privatekey);
            Cipher cipherOne = CryptManager.getCipher(2, secretKey);
            Cipher cipherTwo = CryptManager.getCipher(1, secretKey);
            s = (new BigInteger(CryptManager.digestData("", this.server.getKeyPair().getPublic(), secretKey))).toString(16);
            this.state = State.AUTHENTICATING;
            this.connection.setEncryptionKey(cipherOne, cipherTwo);
        } catch (CryptException ex) {
            throw new IllegalStateException("Protocol error", ex);
        }

        Thread thread = new Thread(SidedThreadGroups.SERVER, "User Authenticator #" + UNIQUE_THREAD_ID.incrementAndGet()) {
            public void run() {
                LuckPermsServerLoginNetHandler handler = LuckPermsServerLoginNetHandler.this;
                GameProfile gameprofile = handler.gameProfile;

                try {
                    handler.gameProfile = handler.server.getSessionService().hasJoinedServer(new GameProfile(null, gameprofile.getName()), s, this.getAddress());
                    if (handler.gameProfile != null) {
                        LuckPermsServerLoginNetHandler.LOGGER.info("UUID of player {} is {}", handler.gameProfile.getName(), handler.gameProfile.getId());
                        handler.state = LuckPermsServerLoginNetHandler.State.NEGOTIATING;

                        MinecraftForge.EVENT_BUS.post(new PlayerNegotiationEvent(handler.connection, handler.gameProfile));
                    } else if (handler.server.isSingleplayer()) {
                        LuckPermsServerLoginNetHandler.LOGGER.warn("Failed to verify username but will let them in anyway!");
                        handler.gameProfile = handler.createFakeProfile(gameprofile);
                        handler.state = LuckPermsServerLoginNetHandler.State.NEGOTIATING;
                        MinecraftForge.EVENT_BUS.post(new PlayerNegotiationEvent(handler.connection, handler.gameProfile));
                    } else {
                        handler.disconnect(new TranslationTextComponent("multiplayer.disconnect.unverified_username"));
                        LuckPermsServerLoginNetHandler.LOGGER.error("Username '{}' tried to join with an invalid session", gameprofile.getName());
                    }
                } catch (AuthenticationUnavailableException ex) {
                    if (handler.server.isSingleplayer()) {
                        LuckPermsServerLoginNetHandler.LOGGER.warn("Authentication servers are down but will let them in anyway!");
                        handler.gameProfile = handler.createFakeProfile(gameprofile);
                        handler.state = LuckPermsServerLoginNetHandler.State.NEGOTIATING;
                        MinecraftForge.EVENT_BUS.post(new PlayerNegotiationEvent(handler.connection, handler.gameProfile));
                    } else {
                        handler.disconnect(new TranslationTextComponent("multiplayer.disconnect.authservers_down"));
                        LuckPermsServerLoginNetHandler.LOGGER.error("Couldn't verify username because servers are unavailable");
                    }
                }

            }

            @Nullable
            private InetAddress getAddress() {
                LuckPermsServerLoginNetHandler handler = LuckPermsServerLoginNetHandler.this;

                SocketAddress socketaddress = handler.connection.getRemoteAddress();
                return handler.server.getPreventProxyConnections() && socketaddress instanceof InetSocketAddress ? ((InetSocketAddress)socketaddress).getAddress() : null;
            }
        };
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        thread.start();
    }

    @Override
    @Nonnull
    protected GameProfile createFakeProfile(GameProfile p_152506_1_) {
        UUID uuid = PlayerEntity.createPlayerUUID(p_152506_1_.getName());
        return new GameProfile(uuid, p_152506_1_.getName());
    }

    @Override
    public void handleCustomQueryPacket(@Nonnull CCustomPayloadLoginPacket payloadLoginPacket) {
        if (!NetworkHooks.onCustomPayload(payloadLoginPacket, this.connection)) {
            this.disconnect(new TranslationTextComponent("multiplayer.disconnect.unexpected_query_response"));
        }
    }

    @Override
    public void onDisconnect(ITextComponent reason) {
        LOGGER.info("{} lost connection: {}", this.getUserName(), reason.getString());
    }

    @Override
    @Nonnull
    public NetworkManager getConnection() {
        return this.connection;
    }

    enum State {
        HELLO,
        KEY,
        AUTHENTICATING,
        NEGOTIATING,
        READY_TO_ACCEPT,
        DELAY_ACCEPT,
        ACCEPTED;

        State() {
        }
    }
}
