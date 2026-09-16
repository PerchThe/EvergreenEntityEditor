package ever.green.managers;

import ever.green.EntityEditor;
import ever.green.utils.EditorState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EditorSessionManager {

    private final EntityEditor plugin;
    private final HotbarManager hotbarManager;
    private final LanguageManager lang;

    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, Entity> activeSelections = new HashMap<>();
    private final Map<UUID, EditorState> currentStates = new HashMap<>();
    private final Map<UUID, BukkitTask> particleTasks = new HashMap<>();

    public enum Axis { X, Y, Z }
    private final Map<UUID, Axis> activeAxes = new HashMap<>();

    public EditorSessionManager(EntityEditor plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
        this.hotbarManager = new HotbarManager(plugin);
    }

    // FIX: Centralized Blacklist for Commands and Tools
    public boolean isBlacklisted(Entity target) {
        if (target == null) return false;
        return target instanceof Player || target instanceof EnderDragon || target instanceof Wither || target instanceof Giant;
    }

    public void enterEditorMode(Player player, Entity initialTarget) {
        // FIX: Force close inventory to ensure cursor items are returned to bags before saving snapshot
        player.closeInventory();

        UUID uuid = player.getUniqueId();

        if (!savedInventories.containsKey(uuid)) {
            savedInventories.put(uuid, player.getInventory().getContents());
            player.getInventory().clear();
        }

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        setSelection(player, initialTarget, true);
    }

    public void exitEditorMode(Player player) {
        UUID uuid = player.getUniqueId();

        if (!savedInventories.containsKey(uuid)) return;

        player.getInventory().clear();
        player.getInventory().setContents(savedInventories.get(uuid));

        savedInventories.remove(uuid);
        activeSelections.remove(uuid);
        currentStates.remove(uuid);
        activeAxes.remove(uuid); // FIX: Plugged memory leak!

        stopSparkles(uuid);

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
        player.sendMessage(lang.getMessage("messages.exited_editor"));
    }

    public void setSelection(Player player, Entity target, boolean isInitial) {
        UUID uuid = player.getUniqueId();
        activeSelections.put(uuid, target);

        stopSparkles(uuid);

        if (target != null) {
            player.sendActionBar(lang.getMessage("actionbar.selected", "%entity%", target.getType().name()));

            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                if (target.isValid() && target.getWorld().equals(player.getWorld())) {
                    double yOffset = target instanceof ArmorStand ? 1.8 : target.getHeight();
                    player.spawnParticle(Particle.HAPPY_VILLAGER,
                            target.getLocation().add(0, yOffset + 0.2, 0),
                            4, 0.3, 0.1, 0.3, 0.0);
                } else {
                    stopSparkles(uuid);
                }
            }, 0L, 10L);

            particleTasks.put(uuid, task);

            EditorState newState = (target instanceof ArmorStand) ? EditorState.ARMOR_STAND_PAGE_1 : EditorState.MOB_PAGE_1;
            changeState(player, newState);
        } else {
            if (isInitial) {
                player.sendMessage(lang.getMessage("messages.no_selection"));
            } else {
                player.sendActionBar(lang.getMessage("actionbar.selection_cleared"));
            }
            changeState(player, EditorState.EMPTY_PAGE);
        }
    }

    public void changeState(Player player, EditorState newState) {
        currentStates.put(player.getUniqueId(), newState);
        hotbarManager.renderHotbar(player, newState, getSelection(player), this);
    }

    public boolean hasSurvivalItem(Player player, Material material) {
        ItemStack[] inv = savedInventories.get(player.getUniqueId());
        if (inv == null) return false;
        for (ItemStack item : inv) {
            if (item != null && item.getType() == material) return true;
        }
        return false;
    }

    public void consumeSurvivalItem(Player player, Material material) {
        ItemStack[] inv = savedInventories.get(player.getUniqueId());
        if (inv == null) return;

        // FIX: Safely nullify to prevent invisible "ghost" items
        for (int i = 0; i < inv.length; i++) {
            ItemStack item = inv[i];
            if (item != null && item.getType() == material) {
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) {
                    inv[i] = null;
                }
                return;
            }
        }
    }

    public Axis getActiveAxis(Player player) {
        return activeAxes.getOrDefault(player.getUniqueId(), Axis.X);
    }

    public void cycleAxis(Player player) {
        Axis current = getActiveAxis(player);
        Axis next = current == Axis.X ? Axis.Y : (current == Axis.Y ? Axis.Z : Axis.X);
        activeAxes.put(player.getUniqueId(), next);
    }

    private void stopSparkles(UUID uuid) {
        if (particleTasks.containsKey(uuid)) {
            particleTasks.get(uuid).cancel();
            particleTasks.remove(uuid);
        }
    }

    public void shutdownRestore() {
        for (UUID uuid : new HashMap<>(savedInventories).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) exitEditorMode(player);
        }
    }

    public boolean isInEditorMode(Player player) {
        return savedInventories.containsKey(player.getUniqueId());
    }

    public Entity getSelection(Player player) {
        return activeSelections.get(player.getUniqueId());
    }

    public EditorState getState(Player player) {
        return currentStates.get(player.getUniqueId());
    }
}