package ever.green.managers;

import ever.green.EntityEditor;
import ever.green.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class LanguageManager {

    private final EntityEditor plugin;
    private FileConfiguration config;

    public LanguageManager(EntityEditor plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig(); // Saves config.yml if needed
        plugin.saveResource("messages.yml", false); // Saves our lang file
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        config = YamlConfiguration.loadConfiguration(file);
    }

    public Component getMessage(String path, String... replacements) {
        String text = config.getString(path, "<red>Missing: " + path);

        if (path.startsWith("messages.")) {
            text = config.getString("prefix", "") + text;
        }

        for (int i = 0; i < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return ColorUtil.parse(text);
    }

    public List<Component> getLore(String path, String... replacements) {
        return config.getStringList(path).stream().map(line -> {
            for (int i = 0; i < replacements.length; i += 2) {
                line = line.replace(replacements[i], replacements[i + 1]);
            }
            return ColorUtil.parse(line);
        }).collect(Collectors.toList());
    }
}