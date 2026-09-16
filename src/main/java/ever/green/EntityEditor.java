package ever.green;

import ever.green.commands.EditorCommand;
import ever.green.listeners.InteractionListener;
import ever.green.listeners.ProtectionListener;
import ever.green.managers.EditorSessionManager;
import ever.green.managers.GUIManager;
import ever.green.managers.LanguageManager;
import ever.green.utils.ItemUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class EntityEditor extends JavaPlugin {

    private EditorSessionManager sessionManager;
    private GUIManager guiManager;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        ItemUtil.init(this);

        // Load configurations
        this.languageManager = new LanguageManager(this);

        // Initialize managers
        this.sessionManager = new EditorSessionManager(this);
        this.guiManager = new GUIManager(this);

        if (getCommand("ee") != null) {
            getCommand("ee").setExecutor(new EditorCommand(this));
        }

        // Register all listeners!
        getServer().getPluginManager().registerEvents(new InteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        getLogger().info("EvergreenEntityEditor enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) sessionManager.shutdownRestore();
        getLogger().info("EvergreenEntityEditor disabled.");
    }

    public EditorSessionManager getSessionManager() { return sessionManager; }
    public GUIManager getGuiManager() { return guiManager; }
    public LanguageManager getLang() { return languageManager; }
}