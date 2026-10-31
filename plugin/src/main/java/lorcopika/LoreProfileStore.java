package lorcopika;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LoreProfileStore {
	private final JavaPlugin plugin;
	private final File directory;
	private final Map<String, LoreProfile> profiles = new LinkedHashMap<>();

	public LoreProfileStore(JavaPlugin plugin) {
		this.plugin = plugin;
		this.directory = new File(plugin.getDataFolder(), "characters");
	}

	public void load() {
		if (!directory.exists() && !directory.mkdirs()) {
			plugin.getLogger().warning("Could not create " + directory);
		}
		File defaultProfile = new File(directory, "chopik.yml");
		if (!defaultProfile.exists()) {
			plugin.saveResource("characters/chopik.yml", false);
		}
		profiles.clear();
		File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
		if (files == null) {
			return;
		}
		for (File file : files) {
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
			String id = yaml.getString("id");
			if (id == null || id.isBlank()) {
				id = file.getName().replaceFirst("\\.ya?ml$", "");
			}
			String name = yaml.getString("name", id);
			String description = yaml.getString("description", "");
			List<String> memories = yaml.getStringList("memories");
			List<String> familiarSounds = yaml.getStringList("familiar-sounds");
			profiles.put(id, new LoreProfile(id, name, description, memories, familiarSounds));
		}
	}

	public LoreProfile get(String id) {
		return profiles.get(id);
	}

	public Set<String> ids() {
		return profiles.keySet();
	}

	public List<String> memories(String id) {
		LoreProfile profile = profiles.get(id);
		return profile == null ? List.of() : new ArrayList<>(profile.memories());
	}

	public void save(LoreProfile profile) {
		if (!directory.exists() && !directory.mkdirs()) {
			plugin.getLogger().warning("Could not create " + directory);
		}
		File file = new File(directory, profile.id() + ".yml");
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("id", profile.id());
		yaml.set("name", profile.name());
		yaml.set("description", profile.description());
		yaml.set("memories", profile.memories());
		yaml.set("familiar-sounds", profile.familiarSounds());
		try {
			yaml.save(file);
			profiles.put(profile.id(), profile);
		} catch (IOException exception) {
			plugin.getLogger().warning("Could not save profile " + profile.id() + ": " + exception.getMessage());
		}
	}

	public boolean delete(String id) {
		if ("chopik".equals(id)) {
			return false;
		}
		LoreProfile removed = profiles.remove(id);
		File file = new File(directory, id + ".yml");
		if (file.exists() && !file.delete()) {
			plugin.getLogger().warning("Could not delete " + file);
		}
		return removed != null;
	}
}
