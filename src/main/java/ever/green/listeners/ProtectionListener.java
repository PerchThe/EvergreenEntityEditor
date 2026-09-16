package ever.green.listeners;

import ever.green.EntityEditor;
import ever.green.managers.EditorSessionManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class ProtectionListener implements Listener {

    private final EditorSessionManager sessionManager;

    public ProtectionListener(EntityEditor plugin) {
        this.sessionManager = plugin.getSessionManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (sessionManager.isInEditorMode(player)) {
            // FIX: Clear entirely to prevent glass panes from filling the AngelChest
            event.getDrops().clear();

            sessionManager.exitEditorMode(player);

            if (!event.getKeepInventory()) {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        event.getDrops().add(item.clone());
                    }
                }
                player.getInventory().clear();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (sessionManager.isInEditorMode(player)) {
            sessionManager.exitEditorMode(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (sessionManager.isInEditorMode(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // FIX: The "Hoover" Exploit
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && sessionManager.isInEditorMode(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOffHandSwap(PlayerSwapHandItemsEvent event) {
        if (sessionManager.isInEditorMode(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && sessionManager.isInEditorMode(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && sessionManager.isInEditorMode(player)) {
            event.setCancelled(true);
        }
    }
}