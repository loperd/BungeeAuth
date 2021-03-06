package me.loper.bungeeauth.listener;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import me.loper.bungeeauth.BungeeAuthPlugin;
import me.loper.bungeeauth.config.Message;
import me.loper.bungeeauth.config.MessageKeys;
import me.loper.bungeeauth.event.ChangedPasswordEvent;

import java.util.UUID;

public class ChangePasswordListener extends BungeeAuthListener {

    private final BungeeAuthPlugin plugin;

    public ChangePasswordListener(BungeeAuthPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChangePassword(ChangedPasswordEvent e) {
        String playerName = e.getPlayerName();
        CommandSender sender = e.getSender();
        UUID uniqueId = e.getPlayerId();

        Message selfNotice = plugin.getMessageConfig().get(
            MessageKeys.CHANGEPASSWORD_SELF_SUCCESS);

        this.plugin.getPlayer(uniqueId).ifPresent(p ->
            p.sendMessage(selfNotice.asComponent()));

        if (!e.isProxiedPlayer()) {
            Message otherNotice = plugin.getMessageConfig().get(
                MessageKeys.CHANGEPASSWORD_OTHER_SUCCESS);
            sender.sendMessage(otherNotice.asComponent(playerName));
        }

        this.plugin.getAuthManager().clearSessions(uniqueId);
    }
}
