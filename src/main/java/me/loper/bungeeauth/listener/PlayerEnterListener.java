package me.loper.bungeeauth.listener;

import me.loper.bungeeauth.server.ServerManager;
import me.loper.bungeeauth.server.ServerType;
import me.loper.bungeeauth.service.AuthManager;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import me.loper.bungeeauth.BungeeAuthPlayer;
import me.loper.bungeeauth.BungeeAuthPlugin;
import me.loper.bungeeauth.config.ConfigKeys;
import me.loper.bungeeauth.config.Message;
import me.loper.bungeeauth.config.MessageKeys;
import me.loper.bungeeauth.event.PlayerAuthenticatedEvent;
import me.loper.bungeeauth.exception.AuthenticationException;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class PlayerEnterListener extends BungeeAuthListener {

    private final ServerManager connector;
    private final BungeeAuthPlugin plugin;
    private final AuthManager authManager;

    public PlayerEnterListener(BungeeAuthPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        this.connector = plugin.getServerManager();
        this.authManager = this.plugin.getAuthManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(PreLoginEvent e) {
        if (e.isCancelled() || null == e.getConnection().getName()) {
            return;
        }

        String protocolRegex = this.plugin.getConfiguration()
                .get(ConfigKeys.PROTOCOL_REGEX);
        PendingConnection conn = e.getConnection();
        String version = String.valueOf(conn.getVersion());

        if (!Pattern.matches(protocolRegex, version)) {
            e.setCancelled(true);
            Message message = plugin.getMessageConfig()
                    .get(MessageKeys.VERSION_OUTDATED);
            e.setCancelReason(message.asComponent());
            return;
        }

        String userName = conn.getName();

        if (!Pattern.matches("^[A-Za-z0-9_]+$", userName)) {
            e.setCancelled(true);
            Message message = plugin.getMessageConfig()
                    .get(MessageKeys.BAD_NICKNAME);
            e.setCancelReason(message.asComponent());
        }

        InetSocketAddress address = (InetSocketAddress) conn.getSocketAddress();

        try {
            this.checkUserInWhitelist(userName, address.getHostString());
        } catch (RuntimeException ex) {
            this.plugin.getLogger().warning(String.format(
                "User with ip `%s` tried to login by username %s", address.getHostString(), userName));

            e.setCancelled(true);

            Message message = plugin.getMessageConfig()
                .get(MessageKeys.FORBIDDEN_ACCESS);
            e.setCancelReason(message.asComponent());
        }
    }

    private void checkUserInWhitelist(String userName, String userIp) {
        Map<String, List<String>> whiteList = this.plugin.getConfiguration()
            .get(ConfigKeys.WHITELIST_USERS);

        List<String> allowedIps = whiteList
            .get(userName.toLowerCase());

        if (null == allowedIps) {
            return;
        }

        if (allowedIps.contains(userIp)) {
            return;
        }

        throw new RuntimeException("This ip address is not allowed for this user.");
    }

    private void handleUnauthorizedAction(ServerConnectEvent e) {
        // current server player
        Server server = e.getPlayer().getServer();

        plugin.getLogger().info("Handle unauthorized player " + e.getPlayer().getName());

        if (null == server) {
            try {
                e.setTarget(this.connector.getServer(ServerType.LOGIN).getTarget());
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
                this.connector.disconnect(e.getPlayer());
            }
            return;
        }

        ServerType serverType = this.connector.getServerTypeByServerInfo(server.getInfo());

        if (serverType.equals(ServerType.LOGIN)) {
            e.setCancelled(true);
        }

        if (!this.connector.getServerTypeByServerInfo(e.getTarget()).equals(ServerType.LOGIN)) {
            try {
                e.setTarget(this.connector.getServer(ServerType.LOGIN).getTarget());
            } catch (IllegalStateException ex) {
                this.connector.disconnect(e.getPlayer());
                ex.printStackTrace();
            }
        }
    }

    private void handlePlayerSession(ServerConnectEvent e) {
        UUID uniqueId = e.getPlayer().getUniqueId();
        BungeeAuthPlayer player = this.plugin.getAuthPlayer(uniqueId);

        if (
            null == player || null == player.session
            || new Date().getTime() > player.session.lifeTime.endTime.getTime()
        ) {
            handleUnauthorizedAction(e);
            return;
        }

        ServerType target = connector.getServerTypeByServerInfo(e.getTarget());

        if (target.equals(ServerType.LOGIN)) {
            try {
                e.setTarget(this.connector.getServer(ServerType.GAME).getTarget());
            } catch (IllegalStateException ex) {
                connector.disconnect(e.getPlayer());
                ex.printStackTrace();
                return;
            }
        }

        Event event = new PlayerAuthenticatedEvent(uniqueId, true);
        this.plugin.getPluginManager().callEvent(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerConnect(LoginEvent e) {
        PendingConnection c = e.getConnection();

        plugin.getLogger().info(String.format("Player with uuid %s has joined to the network", c.getUniqueId()));

        e.registerIntent(this.plugin);

        // load session for player from database
        this.plugin.getScheduler().async().execute(() -> {
            try {
                this.authManager.authenticate(c);
            } catch (AuthenticationException ex) {
                this.plugin.getLogger().severe("Exception occurred whilst loading data for " + c.getUniqueId() + " - " + c.getName());
                ex.printStackTrace();
                e.setCancelled(true);
                Message message = plugin.getMessageConfig()
                    .get(MessageKeys.BAD_REQUEST);
                e.setCancelReason(message.asComponent());
            } finally {
                // finally, complete our intent to modify state, so the proxy can continue handling the connection.
                e.completeIntent(this.plugin);
            }
        });
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onConnect(ServerConnectEvent e) {
        UUID uniqueId = e.getPlayer().getUniqueId();

        if (!this.plugin.hasAuthPlayer(uniqueId)) {
            handleUnauthorizedAction(e);
            return;
        }

        BungeeAuthPlayer player = this.plugin.getAuthPlayer(uniqueId);

        if (null == player || !player.isAuthenticated()) {
            handlePlayerSession(e);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDisconnect(PlayerDisconnectEvent e) {
        ProxiedPlayer p = e.getPlayer();
        UUID uniqueId = p.getUniqueId();
        this.plugin.removeAuthPlayer(uniqueId);
    }
}


