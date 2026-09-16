package ever.green.commands;

import ever.green.EntityEditor;
import ever.green.managers.EditorSessionManager;
import ever.green.utils.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class EditorCommand implements CommandExecutor {

    private final EntityEditor plugin;

    public EditorCommand(EntityEditor plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }

        // FIX: Prevent players from entering editor mode while dead
        if (player.isDead()) {
            player.sendMessage(ColorUtil.parse("<red>You cannot use the editor while dead."));
            return true;
        }

        EditorSessionManager sessionManager = plugin.getSessionManager();

        if (sessionManager.isInEditorMode(player)) {
            sessionManager.exitEditorMode(player);
            return true;
        }

        Entity target = player.getTargetEntity(10);

        // FIX: PREVENT COMMAND BYPASS: Nullify target if it is blacklisted!
        if (sessionManager.isBlacklisted(target)) {
            player.sendActionBar(plugin.getLang().getMessage("actionbar.blacklisted"));
            target = null;
        }

        sessionManager.enterEditorMode(player, target);
        return true;
    }
}