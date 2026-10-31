package lorcopika.mechanic;

import java.util.Set;
import lorcopika.LoreState;
import lorcopika.profile.CharacterProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class LoreSounds {
	private static final Set<String> LOUD = Set.of(
		"minecraft:entity.generic.explode",
		"minecraft:entity.dragon_fireball.explode",
		"minecraft:entity.lightning_bolt.thunder",
		"minecraft:entity.lightning_bolt.impact",
		"minecraft:entity.warden.sonic_boom",
		"minecraft:entity.warden.roar",
		"minecraft:block.sculk_shrieker.shriek"
	);

	private LoreSounds() {
	}

	public static void onSound(ServerLevel level, double x, double y, double z, Identifier id) {
		LoreState state = LoreState.peek(level.getServer());
		if (state == null) {
			return;
		}
		String sound = id.toString();
		boolean loud = isLoud(sound);
		long now = level.getServer().getTickCount();
		for (ServerPlayer player : level.players()) {
			if (!state.isBound(player)) {
				continue;
			}
			double distance = player.distanceToSqr(x, y, z);
			if (loud && state.feature(player, "loud_sounds")) {
				double radius = Math.max(1.0, state.param(player, "loud_sounds.radius"));
				LoreState.Runtime runtime = state.runtime(player);
				if (distance <= radius * radius && now >= runtime.nextLoud) {
					float damage = (float) Math.max(0.0, state.param(player, "loud_sounds.damage"));
					if (damage > 0.0F) {
						player.hurtServer(level, level.damageSources().magic(), damage);
					}
					long interval = Math.max(1L, (long) state.param(player, "loud_sounds.interval"));
					runtime.nextLoud = now + interval;
					state.debug(player, "loud_sounds: " + sound);
				}
			}
			CharacterProfile profile = state.profile(player);
			if (profile != null && state.feature(player, "familiar_sound") && profile.familiarSounds.contains(sound) && distance <= 576.0) {
				LoreState.Runtime runtime = state.runtime(player);
				if (now < runtime.nextFamiliar) {
					continue;
				}
				player.sendOverlayMessage(Component.literal("..."));
				int duration = (int) Math.max(1.0, state.param(player, "familiar_sound.slow_duration"));
				int amplifier = (int) Math.max(0.0, state.param(player, "familiar_sound.slow_amplifier"));
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, amplifier, false, false));
				runtime.nextFamiliar = now + 200L;
				state.debug(player, "familiar_sound: " + sound);
			}
		}
	}

	private static boolean isLoud(String sound) {
		return LOUD.contains(sound) || sound.startsWith("minecraft:item.goat_horn.sound.");
	}
}
