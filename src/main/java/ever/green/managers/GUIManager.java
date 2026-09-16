package ever.green.managers;

import ever.green.EntityEditor;
import ever.green.utils.ColorUtil;
import ever.green.utils.EditorState;
import ever.green.utils.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GUIManager implements Listener {

    private final EntityEditor plugin;
    private final LanguageManager lang;

    private final Map<UUID, ArmorStand> openEquipmentMenus = new HashMap<>();

    private final int SLOT_HELMET = 28;
    private final int SLOT_CHEST = 29;
    private final int SLOT_LEGS = 30;
    private final int SLOT_BOOTS = 31;
    private final int SLOT_MAINHAND = 33;
    private final int SLOT_OFFHAND = 34;

    private final int[] EQUIPMENT_SLOTS = {SLOT_HELMET, SLOT_CHEST, SLOT_LEGS, SLOT_BOOTS, SLOT_MAINHAND, SLOT_OFFHAND};

    public GUIManager(EntityEditor plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    public void openEquipmentGUI(Player player, ArmorStand stand) {
        plugin.getSessionManager().exitEditorMode(player);

        openEquipmentMenus.put(player.getUniqueId(), stand);

        Inventory inv = Bukkit.createInventory(null, 45, lang.getMessage("gui.equipment.title"));

        ItemStack filler = ItemUtil.craftFiller();
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(SLOT_HELMET - 18, buildIndicator(Material.LEATHER_HELMET, "helmet"));
        inv.setItem(SLOT_CHEST - 18, buildIndicator(Material.LEATHER_CHESTPLATE, "chestplate"));
        inv.setItem(SLOT_LEGS - 18, buildIndicator(Material.LEATHER_LEGGINGS, "leggings"));
        inv.setItem(SLOT_BOOTS - 18, buildIndicator(Material.LEATHER_BOOTS, "boots"));
        inv.setItem(SLOT_MAINHAND - 18, buildIndicator(Material.WOODEN_SWORD, "main_hand"));
        inv.setItem(SLOT_OFFHAND - 18, buildIndicator(Material.SHIELD, "off_hand"));

        EntityEquipment eq = stand.getEquipment();
        if (eq != null) {
            inv.setItem(SLOT_HELMET, eq.getHelmet());
            inv.setItem(SLOT_CHEST, eq.getChestplate());
            inv.setItem(SLOT_LEGS, eq.getLeggings());
            inv.setItem(SLOT_BOOTS, eq.getBoots());
            inv.setItem(SLOT_MAINHAND, eq.getItemInMainHand());
            inv.setItem(SLOT_OFFHAND, eq.getItemInOffHand());
        }

        player.openInventory(inv);
    }

    private ItemStack buildIndicator(Material mat, String key) {
        return ItemUtil.createEditorItem(mat, 1, lang.getMessage("gui.equipment." + key), "indicator", null);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openEquipmentMenus.containsKey(player.getUniqueId())) return;

        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            int slot = event.getSlot();
            boolean isEquipmentSlot = false;
            for (int eqSlot : EQUIPMENT_SLOTS) {
                if (slot == eqSlot) {
                    isEquipmentSlot = true;
                    break;
                }
            }
            if (!isEquipmentSlot) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openEquipmentMenus.containsKey(player.getUniqueId())) return;

        for (int slot : event.getRawSlots()) {
            if (slot < 45) {
                boolean isEquipmentSlot = false;
                for (int eqSlot : EQUIPMENT_SLOTS) {
                    if (slot == eqSlot) {
                        isEquipmentSlot = true;
                        break;
                    }
                }
                if (!isEquipmentSlot) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // FIX: If they aren't even using the Equipment GUI, ignore the event entirely!
        if (!openEquipmentMenus.containsKey(player.getUniqueId())) return;

        ArmorStand stand = openEquipmentMenus.remove(player.getUniqueId());
        Inventory inv = event.getInventory();

        if (stand == null || !stand.isValid()) {
            for (int eqSlot : EQUIPMENT_SLOTS) {
                ItemStack item = inv.getItem(eqSlot);
                if (item != null && item.getType() != Material.AIR) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            if (stand != null) player.sendMessage(ColorUtil.parse("<red>The Armor Stand was destroyed! Your items were dropped."));
            return;
        }

        EntityEquipment eq = stand.getEquipment();

        if (eq != null) {
            eq.setHelmet(inv.getItem(SLOT_HELMET));
            eq.setChestplate(inv.getItem(SLOT_CHEST));
            eq.setLeggings(inv.getItem(SLOT_LEGS));
            eq.setBoots(inv.getItem(SLOT_BOOTS));
            eq.setItemInMainHand(inv.getItem(SLOT_MAINHAND));
            eq.setItemInOffHand(inv.getItem(SLOT_OFFHAND));
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_GENERIC, 1f, 1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !player.isDead() && stand.isValid()) {
                plugin.getSessionManager().enterEditorMode(player, stand);
                plugin.getSessionManager().changeState(player, EditorState.ARMOR_STAND_PAGE_3);
            }
        }, 1L);
    }
}