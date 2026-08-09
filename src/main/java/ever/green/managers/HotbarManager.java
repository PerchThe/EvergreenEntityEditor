package ever.green.managers;

import ever.green.EntityEditor;
import ever.green.utils.EditorState;
import ever.green.utils.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class HotbarManager {

    private final LanguageManager lang;

    public HotbarManager(EntityEditor plugin) {
        this.lang = plugin.getLang();
    }

    public void renderHotbar(Player player, EditorState state, Entity target, EditorSessionManager session) {
        Inventory inv = player.getInventory();
        inv.clear();

        int page = 1;
        if (state == EditorState.ARMOR_STAND_PAGE_2 || state == EditorState.MOB_PAGE_2) page = 2;
        else if (state == EditorState.ARMOR_STAND_PAGE_3) page = 3;
        else if (state == EditorState.ARMOR_STAND_PAGE_4) page = 4;

        inv.setItem(7, buildItem(Material.PAPER, page, "page", "next_page", "%page%", String.valueOf(page)));
        inv.setItem(8, buildItem(Material.RED_STAINED_GLASS_PANE, 1, "exit", "exit"));

        ItemStack selector = buildItem(Material.STRUCTURE_VOID, 1, "selector", "selector");

        switch (state) {
            case EMPTY_PAGE -> {
                for (int i = 0; i < 7; i++) inv.setItem(i, selector);
                if (session.hasSurvivalItem(player, Material.ARMOR_STAND)) {
                    inv.setItem(4, buildItem(Material.ARMOR_STAND, 1, "place_stand", "place_stand"));
                }
            }
            case ARMOR_STAND_PAGE_1 -> {
                inv.setItem(0, selector);
                inv.setItem(1, buildItem(Material.LAPIS_LAZULI, 1, "move_x", "move_x"));
                inv.setItem(2, buildItem(Material.REDSTONE, 1, "move_y", "move_y"));
                inv.setItem(3, buildItem(Material.EMERALD, 1, "move_z", "move_z"));
                inv.setItem(4, buildItem(Material.COMPARATOR, 1, "rotate_global", "rotate_global"));
                inv.setItem(5, buildItem(Material.GRAY_DYE, 1, "scale", "scale"));

                if (session.hasSurvivalItem(player, Material.ARMOR_STAND)) {
                    inv.setItem(6, buildItem(Material.ARMOR_STAND, 1, "place_stand", "place_stand"));
                }
            }
            case ARMOR_STAND_PAGE_2 -> {
                String axisName = session.getActiveAxis(player).name();
                inv.setItem(0, buildItem(getAxisMat(axisName), 1, "axis_selector", "axis_selector", "%axis%", axisName));
                inv.setItem(1, buildItem(Material.IRON_HELMET, 1, "pose_head", "pose_head"));
                inv.setItem(2, buildItem(Material.IRON_CHESTPLATE, 1, "pose_body", "pose_body"));
                inv.setItem(3, buildItem(Material.BLAZE_ROD, 1, "pose_l_arm", "pose_l_arm"));
                inv.setItem(4, buildItem(Material.BONE, 1, "pose_r_arm", "pose_r_arm"));
                inv.setItem(5, buildItem(Material.LEATHER_BOOTS, 1, "pose_l_leg", "pose_l_leg"));
                inv.setItem(6, buildItem(Material.IRON_BOOTS, 1, "pose_r_leg", "pose_r_leg"));
            }
            case ARMOR_STAND_PAGE_3 -> {
                inv.setItem(0, buildItem(Material.CHEST, 1, "equipment_gui", "equipment_gui"));
                inv.setItem(1, buildItem(Material.IRON_DOOR, 1, "toggle_lock", "toggle_lock"));
                inv.setItem(2, buildItem(Material.SHIELD, 1, "toggle_invuln", "toggle_invuln"));
                inv.setItem(3, buildItem(Material.ENCHANTED_BOOK, 1, "toggle_glow", "toggle_glow"));
                inv.setItem(4, buildItem(Material.ARMOR_STAND, 1, "toggle_arms", "toggle_arms"));
                inv.setItem(5, buildItem(Material.SMOOTH_STONE_SLAB, 1, "toggle_baseplate", "toggle_baseplate"));
                inv.setItem(6, buildItem(Material.NAME_TAG, 1, "edit_name", "edit_name"));
            }
            case ARMOR_STAND_PAGE_4 -> {
                inv.setItem(0, buildItem(Material.GLASS, 1, "toggle_visibility", "toggle_visibility"));
                inv.setItem(1, buildItem(Material.WATER_BUCKET, 1, "clipboard", "copy_paste_1", "%slot%", "1"));
                inv.setItem(2, buildItem(Material.WATER_BUCKET, 2, "clipboard", "copy_paste_2", "%slot%", "2"));
                inv.setItem(3, buildItem(Material.WATER_BUCKET, 3, "clipboard", "copy_paste_3", "%slot%", "3"));
                inv.setItem(4, buildItem(Material.ANVIL, 1, "reset_pose", "reset_pose"));
            }
            case MOB_PAGE_1 -> {
                inv.setItem(0, selector);
                inv.setItem(1, buildItem(Material.LAPIS_LAZULI, 1, "move_x", "move_x"));
                inv.setItem(2, buildItem(Material.REDSTONE, 1, "move_y", "move_y"));
                inv.setItem(3, buildItem(Material.EMERALD, 1, "move_z", "move_z"));
                inv.setItem(4, buildItem(Material.COMPARATOR, 1, "rotate_yaw", "rotate_yaw"));
                inv.setItem(5, buildItem(Material.REPEATER, 1, "rotate_pitch", "rotate_pitch"));

                if (target instanceof LivingEntity) {
                    inv.setItem(6, buildItem(Material.ICE, 1, "toggle_ai", "toggle_ai"));
                }
            }
            case MOB_PAGE_2 -> {
                inv.setItem(0, selector);
                // Swapped the useless invuln button for the Name Tag!
                inv.setItem(1, buildItem(Material.NAME_TAG, 1, "edit_name", "edit_name"));
                inv.setItem(2, buildItem(Material.BELL, 1, "toggle_silence", "toggle_silence"));
                inv.setItem(3, buildItem(Material.ENCHANTED_BOOK, 1, "toggle_glow", "toggle_glow"));

                // Dynamically stack the remaining items so there are no awkward empty gaps
                int nextSlot = 4;

                if (canChangeAge(target)) {
                    inv.setItem(nextSlot++, buildItem(Material.CLOCK, 1, "toggle_age", "toggle_age"));
                }

                // Here is the new Age Lock item using a Chain!
                if (target instanceof Ageable) {
                    inv.setItem(nextSlot++, buildItem(Material.GOLDEN_DANDELION, 1, "toggle_age_lock", "toggle_age_lock"));
                }

                if (target instanceof Tameable) {
                    inv.setItem(nextSlot++, buildItem(Material.BONE, 1, "untame", "untame"));
                }
            }
        }

        for (int i = 0; i <= 8; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, ItemUtil.craftFiller());
        }
    }

    // Helper to ensure the clock shows up for Slimes, Zombies, and Piglins too!
    private boolean canChangeAge(Entity target) {
        return target instanceof Ageable ||
                target instanceof org.bukkit.entity.Zombie ||
                target instanceof org.bukkit.entity.PiglinAbstract ||
                target instanceof org.bukkit.entity.Slime;
    }

    private ItemStack buildItem(Material mat, int amt, String configKey, String toolId, String... replacements) {
        return ItemUtil.createEditorItem(
                mat, amt,
                lang.getMessage("items." + configKey + ".name", replacements),
                toolId,
                lang.getLore("items." + configKey + ".lore", replacements)
        );
    }

    private Material getAxisMat(String axis) {
        return axis.equals("X") ? Material.RED_DYE : (axis.equals("Y") ? Material.GREEN_DYE : Material.BLUE_DYE);
    }
}