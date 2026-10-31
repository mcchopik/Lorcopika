package lorcopika;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LorcopikaPlugin extends JavaPlugin {
	private LoreSettings settings;
	private LoreProfileStore profiles;
	private LoreBindingStore bindings;
	private LoreMechanics mechanics;

	@Override
	public void onEnable() {
		settings = new LoreSettings(this);
		settings.load();
		profiles = new LoreProfileStore(this);
		profiles.load();
		bindings = new LoreBindingStore(this);
		bindings.load();
		mechanics = new LoreMechanics(this, settings, profiles, bindings);
		mechanics.register();
		PluginCommand command = getCommand("lore");
		if (command != null) {
			LoreCommand executor = new LoreCommand(this, settings, profiles, bindings, mechanics);
			command.setExecutor(executor);
			command.setTabCompleter(executor);
		}
		getLogger().info("Lorcopika enabled");
	}

	@Override
	public void onDisable() {
		if (settings != null) {
			settings.save();
		}
		if (bindings != null) {
			bindings.save();
		}
	}
}
