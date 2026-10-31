package lorcopika.mixin;

import lorcopika.mechanic.LoreSounds;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSoundMixin {
	@Inject(
		method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
		at = @At("HEAD")
	)
	private void lorcopika$soundAt(
		@Nullable Entity except,
		double x,
		double y,
		double z,
		Holder<SoundEvent> sound,
		SoundSource source,
		float volume,
		float pitch,
		long seed,
		CallbackInfo callbackInfo
	) {
		LoreSounds.onSound((ServerLevel) (Object) this, x, y, z, sound.value().location());
	}

	@Inject(
		method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
		at = @At("HEAD")
	)
	private void lorcopika$soundEntity(
		@Nullable Entity except,
		Entity sourceEntity,
		Holder<SoundEvent> sound,
		SoundSource source,
		float volume,
		float pitch,
		long seed,
		CallbackInfo callbackInfo
	) {
		LoreSounds.onSound((ServerLevel) (Object) this, sourceEntity.getX(), sourceEntity.getY(), sourceEntity.getZ(), sound.value().location());
	}
}
