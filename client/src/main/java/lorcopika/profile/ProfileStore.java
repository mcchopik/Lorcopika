package lorcopika.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class ProfileStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path directory;
	private final Logger logger;
	private final Map<String, CharacterProfile> profiles = new LinkedHashMap<>();

	public ProfileStore(Path directory, Logger logger) {
		this.directory = directory;
		this.logger = logger;
	}

	public void load() {
		profiles.clear();
		try {
			Files.createDirectories(directory);
			try (Stream<Path> files = Files.list(directory)) {
				files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(this::loadFile);
			}
		} catch (IOException exception) {
			logger.warning("Could not read profiles: " + exception.getMessage());
		}
	}

	private void loadFile(Path file) {
		try (Reader reader = Files.newBufferedReader(file)) {
			CharacterProfile profile = GSON.fromJson(reader, CharacterProfile.class);
			if (profile == null || profile.id == null || profile.id.isBlank()) {
				throw new IOException("missing profile id");
			}
			if (profile.memories == null) {
				profile.memories = new ArrayList<>();
			}
			if (profile.familiarSounds == null) {
				profile.familiarSounds = new ArrayList<>();
			}
			profiles.put(profile.id, profile);
		} catch (Exception exception) {
			logger.warning("Invalid profile " + file + ": " + exception.getMessage());
		}
	}

	public void ensureDefault() {
		if (!Files.exists(directory.resolve("chopik.json"))) {
			save(defaultChopik());
		}
		if (!profiles.containsKey("chopik")) {
			profiles.put("chopik", defaultChopik());
		}
	}

	public void reload() {
		load();
		ensureDefault();
	}

	public CharacterProfile get(String id) {
		return id == null ? null : profiles.get(id);
	}

	public List<String> ids() {
		return new ArrayList<>(profiles.keySet());
	}

	public Map<String, CharacterProfile> all() {
		return profiles;
	}

	public void save(CharacterProfile profile) {
		profiles.put(profile.id, profile);
		try {
			Files.createDirectories(directory);
			try (Writer writer = Files.newBufferedWriter(directory.resolve(profile.id + ".json"))) {
				GSON.toJson(profile, writer);
			}
		} catch (IOException exception) {
			logger.warning("Could not save profile " + profile.id + ": " + exception.getMessage());
		}
	}

	public static CharacterProfile defaultChopik() {
		CharacterProfile profile = new CharacterProfile();
		profile.id = "chopik";
		profile.name = "Чопик";
		profile.description = "Частично заражён скалком. Правый глаз не видит. Варден больше не замечает его.";
		profile.memories.add("Ты помнишь, как лежишь на земле, а Варден уходит дальше.");
		profile.memories.add("Ты помнишь долгое падение в неизвестность.");
		profile.memories.add("Ты помнишь боль, но не помнишь, откуда она.");
		profile.memories.add("Ты помнишь, как деревня исчезала в темноте.");
		profile.memories.add("Ты помнишь, как очнулся и понял, что правый глаз не видит.");
		profile.memories.add("Ты помнишь чужой голос, который будто стёр твою память.");
		profile.memories.add("Ты помнишь, как скалк впервые коснулся твоей руки.");
		profile.familiarSounds.add("minecraft:entity.warden.ambient");
		profile.familiarSounds.add("minecraft:entity.warden.heartbeat");
		profile.familiarSounds.add("minecraft:entity.warden.listening");
		profile.familiarSounds.add("minecraft:block.sculk_sensor.clicking");
		profile.familiarSounds.add("minecraft:block.sculk_sensor.clicking_active");
		profile.familiarSounds.add("minecraft:block.sculk_catalyst.bloom");
		profile.familiarSounds.add("minecraft:block.sculk_shrieker.shriek");
		profile.familiarSounds.add("minecraft:entity.player.hurt");
		return profile;
	}
}
