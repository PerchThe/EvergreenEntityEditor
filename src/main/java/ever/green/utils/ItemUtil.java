package ever.green.utils;

import ever.green.EntityEditor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ItemUtil {

    public static NamespacedKey EDITOR_KEY;

    public static void init(EntityEditor plugin) {
        EDITOR_KEY = new NamespacedKey(plugin, "editor_tool");
    }

    public static ItemStack createEditorItem(Material material, int amount, Component name, String toolId, List<Component> lore) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(127, amount)));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) meta.lore(lore);

            meta.getPersistentDataContainer().set(EDITOR_KEY, PersistentDataType.STRING, toolId);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack craftFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}