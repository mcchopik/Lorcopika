package lorcopika.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lorcopika.LoreState;
import lorcopika.config.LoreConfig;
import lorcopika.mechanic.LoreMechanics;
import lorcopika.profile.CharacterProfile;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class LoreCommand {
	private LoreCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("lore")
				.executes(context -> status(context.getSource()))
				.then(Commands.literal("status").executes(context -> status(context.getSource())))
				.then(Commands.literal("bind")
					.requires(LoreCommand::admin)
					.then(Commands.argument("player", StringArgumentType.word())
						.suggests(LoreCommand::onlineNames)
						.then(Commands.argument("profile", StringArgumentType.word())
							.suggests(LoreCommand::profileIds)
							.executes(LoreCommand::bind))))
				.then(Commands.literal("unbind")
					.requires(LoreCommand::admin)
					.then(Commands.argument("player", StringArgumentType.word())
						.suggests(LoreCommand::onlineNames)
						.executes(LoreCommand::unbind)))
				.then(Commands.literal("bindings")
					.requires(LoreCommand::admin)
					.executes(LoreCommand::bindings))
				.then(Commands.literal("profile")
					.requires(LoreCommand::admin)
					.then(Commands.literal("list").executes(LoreCommand::profileList))
					.then(Commands.literal("show")
						.then(Commands.argument("profile", StringArgumentType.word())
							.suggests(LoreCommand::profileIds)
							.executes(LoreCommand::profileShow)))
					.then(Commands.literal("reload").executes(LoreCommand::profileReload)))
				.then(Commands.literal("memory")
					.requires(LoreCommand::admin)
					.then(Commands.literal("add")
						.then(Commands.argument("profile", StringArgumentType.word())
							.suggests(LoreCommand::profileIds)
							.then(Commands.argument("text", StringArgumentType.greedyString())
								.executes(LoreCommand::memoryAdd))))
					.then(Commands.literal("remove")
						.then(Commands.argument("profile", StringArgumentType.word())
							.suggests(LoreCommand::profileIds)
							.then(Commands.argument("number", IntegerArgumentType.integer(1))
								.executes(LoreCommand::memoryRemove))))
					.then(Commands.literal("list")
						.then(Commands.argument("profile", StringArgumentType.word())
							.suggests(LoreCommand::profileIds)
							.executes(LoreCommand::memoryList)))
					.then(Commands.literal("clear")
						.then(Commands.argument("profile", StringArgumentType.word())
							.suggests(LoreCommand::profileIds)
							.executes(LoreCommand::memoryClear))))
				.then(Commands.literal("trigger")
					.requires(LoreCommand::admin)
					.then(Commands.literal("sound")
						.then(Commands.literal("add")
							.then(Commands.argument("profile", StringArgumentType.word())
								.suggests(LoreCommand::profileIds)
								.then(Commands.argument("sound", StringArgumentType.greedyString())
									.executes(LoreCommand::soundAdd))))
						.then(Commands.literal("remove")
							.then(Commands.argument("profile", StringArgumentType.word())
								.suggests(LoreCommand::profileIds)
								.then(Commands.argument("sound", StringArgumentType.greedyString())
									.executes(LoreCommand::soundRemove))))
						.then(Commands.literal("list")
							.then(Commands.argument("profile", StringArgumentType.word())
								.suggests(LoreCommand::profileIds)
								.executes(LoreCommand::soundList)))))
				.then(Commands.literal("feature")
					.requires(LoreCommand::admin)
					.then(Commands.argument("mechanic", StringArgumentType.word())
						.suggests(LoreCommand::featureNames)
						.then(Commands.literal("on")
							.executes(context -> setFeature(context, true, null))
							.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> setFeature(context, true, EntityArgument.getPlayer(context, "player")))))
						.then(Commands.literal("off")
							.executes(context -> setFeature(context, false, null))
							.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> setFeature(context, false, EntityArgument.getPlayer(context, "player")))))))
				.then(Commands.literal("set")
					.requires(LoreCommand::admin)
					.then(Commands.argument("param", StringArgumentType.string())
						.suggests(LoreCommand::paramNames)
						.then(Commands.argument("value", DoubleArgumentType.doubleArg())
							.executes(context -> setParam(context, null))
							.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> setParam(context, EntityArgument.getPlayer(context, "player")))))))
				.then(Commands.literal("show")
					.requires(LoreCommand::admin)
					.executes(context -> show(context.getSource(), null))
					.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> show(context.getSource(), EntityArgument.getPlayer(context, "player")))))
				.then(Commands.literal("reload")
					.requires(LoreCommand::admin)
					.executes(LoreCommand::reload))
				.then(Commands.literal("reset")
					.requires(LoreCommand::admin)
					.executes(context -> reset(context.getSource(), null))
					.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> reset(context.getSource(), EntityArgument.getPlayer(context, "player")))))
				.then(Commands.literal("debug")
					.requires(LoreCommand::admin)
					.then(Commands.literal("on").executes(context -> debug(context.getSource(), true)))
					.then(Commands.literal("off").executes(context -> debug(context.getSource(), false))))
		));
	}

	private static boolean admin(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static int status(CommandSourceStack source) {
		ServerPlayer player;
		try {
			player = source.getPlayerOrException();
		} catch (Exception exception) {
			source.sendFailure(Component.literal("Only a player can use this command"));
			return 0;
		}
		LoreState state = LoreState.get(source.getServer());
		CharacterProfile profile = state.profile(player);
		if (profile == null) {
			source.sendSuccess(() -> Component.literal("No character profile is bound to you"), false);
			return 1;
		}
		List<String> lines = new ArrayList<>();
		lines.add("Character: " + profile.name + " (" + profile.id + ")");
		lines.add("Features: " + String.join(", ", activeFeatures(state, player)));
		if (!profile.memories.isEmpty()) {
			lines.add("Memories:");
			for (int index = 0; index < profile.memories.size(); index++) {
				lines.add((index + 1) + ". " + profile.memories.get(index));
			}
		}
		lines.forEach(line -> player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	private static int bind(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String name = StringArgumentType.getString(context, "player");
		String profileId = StringArgumentType.getString(context, "profile");
		LoreState state = LoreState.get(source.getServer());
		if (state.profiles().get(profileId) == null) {
			source.sendFailure(Component.literal("Unknown profile: " + profileId));
			return 0;
		}
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
		if (target != null) {
			state.bindings().bind(target.getUUID(), target.getGameProfile().name(), profileId);
			LoreMechanics.applyHealth(target);
			source.sendSuccess(() -> Component.literal("Bound " + name + " to " + profileId), true);
			return 1;
		}
		UUID uuid = state.bindings().uuidForName(name);
		if (uuid == null) {
			source.sendFailure(Component.literal("Player is not online and has no known UUID: " + name));
			return 0;
		}
		state.bindings().bind(uuid, name, profileId);
		source.sendSuccess(() -> Component.literal("Bound " + name + " to " + profileId), true);
		return 1;
	}

	private static int unbind(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String name = StringArgumentType.getString(context, "player");
		LoreState state = LoreState.get(source.getServer());
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
		UUID uuid = target != null ? target.getUUID() : state.bindings().uuidForName(name);
		if (uuid == null || !state.bindings().unbind(uuid)) {
			source.sendFailure(Component.literal("No binding found for " + name));
			return 0;
		}
		if (target != null) {
			LoreMechanics.applyHealth(target);
		}
		source.sendSuccess(() -> Component.literal("Unbound " + name), true);
		return 1;
	}

	private static int bindings(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		LoreState state = LoreState.get(source.getServer());
		if (state.bindings().bindings.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No bindings"), false);
			return 1;
		}
		List<String> lines = new ArrayList<>();
		for (Map.Entry<String, String> entry : state.bindings().bindings.entrySet()) {
			String name = state.bindings().names.getOrDefault(entry.getKey(), entry.getKey());
			lines.add(name + " -> " + entry.getValue() + " (" + entry.getKey() + ")");
		}
		lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
		return 1;
	}

	private static int profileList(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		LoreState state = LoreState.get(source.getServer());
		if (state.profiles().all().isEmpty()) {
			source.sendSuccess(() -> Component.literal("No profiles"), false);
			return 1;
		}
		state.profiles().all().values().forEach(profile -> source.sendSuccess(() -> Component.literal(profile.id + " - " + profile.name), false));
		return 1;
	}

	private static int profileShow(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String id = StringArgumentType.getString(context, "profile");
		CharacterProfile profile = LoreState.get(source.getServer()).profiles().get(id);
		if (profile == null) {
			source.sendFailure(Component.literal("Unknown profile: " + id));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Profile: " + profile.id), false);
		source.sendSuccess(() -> Component.literal("Name: " + profile.name), false);
		source.sendSuccess(() -> Component.literal("Description: " + profile.description), false);
		source.sendSuccess(() -> Component.literal("Memories: " + profile.memories.size()), false);
		source.sendSuccess(() -> Component.literal("Familiar sounds: " + profile.familiarSounds.size()), false);
		return 1;
	}

	private static int profileReload(CommandContext<CommandSourceStack> context) {
		LoreState state = LoreState.get(context.getSource().getServer());
		state.profiles().reload();
		context.getSource().sendSuccess(() -> Component.literal("Profiles reloaded"), true);
		return 1;
	}

	private static int memoryAdd(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		CharacterProfile profile = profile(context);
		if (profile == null) {
			return 0;
		}
		profile.memories.add(StringArgumentType.getString(context, "text"));
		LoreState.get(source.getServer()).profiles().save(profile);
		source.sendSuccess(() -> Component.literal("Memory added to " + profile.id), true);
		return 1;
	}

	private static int memoryRemove(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		CharacterProfile profile = profile(context);
		if (profile == null) {
			return 0;
		}
		int number = IntegerArgumentType.getInteger(context, "number");
		if (number < 1 || number > profile.memories.size()) {
			source.sendFailure(Component.literal("Memory number out of range"));
			return 0;
		}
		profile.memories.remove(number - 1);
		LoreState.get(source.getServer()).profiles().save(profile);
		source.sendSuccess(() -> Component.literal("Memory removed from " + profile.id), true);
		return 1;
	}

	private static int memoryList(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		CharacterProfile profile = profile(context);
		if (profile == null) {
			return 0;
		}
		if (profile.memories.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No memories for " + profile.id), false);
			return 1;
		}
		for (int index = 0; index < profile.memories.size(); index++) {
			int number = index + 1;
			String memory = profile.memories.get(index);
			source.sendSuccess(() -> Component.literal(number + ". " + memory), false);
		}
		return 1;
	}

	private static int memoryClear(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		CharacterProfile profile = profile(context);
		if (profile == null) {
			return 0;
		}
		profile.memories.clear();
		LoreState.get(source.getServer()).profiles().save(profile);
		source.sendSuccess(() -> Component.literal("Memories cleared for " + profile.id), true);
		return 1;
	}

	private static int soundAdd(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		CharacterProfile profile = profile(context);
		if (profile == null) {
			return 0;
		}
		String value = StringArgumentType.getString(context, "sound");
		Identifier identifier = Identifier.tryParse(value);
		if (identifier == null) {
			source.sendFailure(Component.literal("Invalid sound id: " + value));
			return 0;
		}
		String sound = identifier.toString();
		if (!profile.familiarSounds.contains(sound)) {
			profile.familiarSounds.add(sound);
			LoreState.get(source.getServer()).profiles().save(profile);
		}
		source.sendSuccess(() -> Component.literal("Sound added to " + profile.id), true);
		return 1;
	}

	private static int soundRemove(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		CharacterProfile profile = profile(context);
		if (profile == null) {
			return 0;
		}
		String value = StringArgumentType.getString(context, "sound");
		Identifier identifier = Identifier.tryParse(value);
		String sound = identifier == null ? value : identifier.toString();
		boolean removed = profile.familiarSounds.remove(sound);
		if (removed) {
			LoreState.get(source.getServer()).profiles().save(profile);
		}
		source.sendSuccess(() -> Component.literal(removed ? "Sound removed" : "Sound not found"), true);
		return removed ? 1 : 0;
	}

	private static int soundList(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		CharacterProfile profile = profile(context);
		if (profile == null) {
			return 0;
		}
		if (profile.familiarSounds.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No familiar sounds for " + profile.id), false);
			return 1;
		}
		profile.familiarSounds.forEach(sound -> source.sendSuccess(() -> Component.literal(sound), false));
		return 1;
	}

	private static int setFeature(CommandContext<CommandSourceStack> context, boolean enabled, ServerPlayer player) {
		CommandSourceStack source = context.getSource();
		String mechanic = StringArgumentType.getString(context, "mechanic");
		if (!Arrays.asList(LoreConfig.FEATURES).contains(mechanic)) {
			source.sendFailure(Component.literal("Unknown mechanic: " + mechanic));
			return 0;
		}
		LoreState state = LoreState.get(source.getServer());
		if (player == null) {
			state.config().features.put(mechanic, enabled);
			LoreMechanics.refreshAll(source.getServer());
		} else {
			state.config().player(player.getUUID().toString()).features.put(mechanic, enabled);
			LoreMechanics.applyHealth(player);
		}
		state.saveConfig();
		String target = player == null ? "globally" : "for " + player.getGameProfile().name();
		source.sendSuccess(() -> Component.literal(mechanic + " " + (enabled ? "enabled" : "disabled") + " " + target), true);
		return 1;
	}

	private static int setParam(CommandContext<CommandSourceStack> context, ServerPlayer player) {
		CommandSourceStack source = context.getSource();
		String param = StringArgumentType.getString(context, "param");
		if (!Arrays.asList(LoreConfig.PARAMS).contains(param)) {
			source.sendFailure(Component.literal("Unknown parameter: " + param));
			return 0;
		}
		double value = DoubleArgumentType.getDouble(context, "value");
		if (!Double.isFinite(value)) {
			source.sendFailure(Component.literal("Value must be finite"));
			return 0;
		}
		LoreState state = LoreState.get(source.getServer());
		if (player == null) {
			state.config().params.put(param, value);
			LoreMechanics.refreshAll(source.getServer());
		} else {
			state.config().player(player.getUUID().toString()).params.put(param, value);
			LoreMechanics.applyHealth(player);
		}
		state.saveConfig();
		String target = player == null ? "globally" : "for " + player.getGameProfile().name();
		source.sendSuccess(() -> Component.literal(param + " set to " + value + " " + target), true);
		return 1;
	}

	private static int show(CommandSourceStack source, ServerPlayer target) {
		if (target == null) {
			try {
				target = source.getPlayerOrException();
			} catch (Exception exception) {
				source.sendFailure(Component.literal("Specify a player"));
				return 0;
			}
		}
		LoreState state = LoreState.get(source.getServer());
		CharacterProfile profile = state.profile(target);
		String uuid = target.getUUID().toString();
		List<String> lines = new ArrayList<>();
		lines.add("Settings for " + target.getGameProfile().name());
		lines.add("Profile: " + (profile == null ? "none" : profile.id));
		for (String feature : LoreConfig.FEATURES) {
			lines.add(feature + " = " + state.config().feature(uuid, feature));
		}
		for (String param : LoreConfig.PARAMS) {
			lines.add(param + " = " + state.config().param(uuid, param));
		}
		lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		LoreState.get(context.getSource().getServer()).reload();
		context.getSource().sendSuccess(() -> Component.literal("Lorcopika reloaded"), true);
		return 1;
	}

	private static int reset(CommandSourceStack source, ServerPlayer target) {
		LoreState state = LoreState.get(source.getServer());
		if (target == null) {
			state.config().reset(null);
			LoreMechanics.refreshAll(source.getServer());
		} else {
			state.config().reset(target.getUUID().toString());
			LoreMechanics.applyHealth(target);
		}
		state.saveConfig();
		source.sendSuccess(() -> Component.literal("Settings reset"), true);
		return 1;
	}

	private static int debug(CommandSourceStack source, boolean enabled) {
		LoreState state = LoreState.get(source.getServer());
		state.config().debug = enabled;
		state.saveConfig();
		source.sendSuccess(() -> Component.literal("Debug " + (enabled ? "enabled" : "disabled")), true);
		return 1;
	}

	private static CharacterProfile profile(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String id = StringArgumentType.getString(context, "profile");
		CharacterProfile profile = LoreState.get(source.getServer()).profiles().get(id);
		if (profile == null) {
			source.sendFailure(Component.literal("Unknown profile: " + id));
		}
		return profile;
	}

	private static List<String> activeFeatures(LoreState state, ServerPlayer player) {
		String uuid = player.getUUID().toString();
		List<String> active = new ArrayList<>();
		for (String feature : LoreConfig.FEATURES) {
			if (state.config().feature(uuid, feature)) {
				active.add(feature);
			}
		}
		return active;
	}

	private static CompletableFuture<Suggestions> onlineNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		List<String> names = context.getSource().getServer().getPlayerList().getPlayers().stream().map(player -> player.getGameProfile().name()).toList();
		return SharedSuggestionProvider.suggest(names, builder);
	}

	private static CompletableFuture<Suggestions> profileIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(LoreState.get(context.getSource().getServer()).profiles().ids(), builder);
	}

	private static CompletableFuture<Suggestions> featureNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(Arrays.asList(LoreConfig.FEATURES), builder);
	}

	private static CompletableFuture<Suggestions> paramNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(Arrays.asList(LoreConfig.PARAMS), builder);
	}
}
