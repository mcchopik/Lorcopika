package lorcopika;

import java.util.ArrayList;
import java.util.List;

public final class LoreProfile {
	private final String id;
	private String name;
	private String description;
	private final List<String> memories;
	private final List<String> familiarSounds;

	public LoreProfile(String id, String name, String description, List<String> memories, List<String> familiarSounds) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.memories = new ArrayList<>(memories);
		this.familiarSounds = new ArrayList<>(familiarSounds);
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String description() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<String> memories() {
		return memories;
	}

	public List<String> familiarSounds() {
		return familiarSounds;
	}

	public boolean familiar(String sound) {
		for (String entry : familiarSounds) {
			if (sound.equals(entry) || (entry.endsWith(".") && sound.startsWith(entry))) {
				return true;
			}
		}
		return false;
	}
}
