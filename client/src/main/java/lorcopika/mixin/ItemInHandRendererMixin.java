package lorcopika.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import lorcopika.client.ClientVisuals;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderPlayerArm", at = @At("HEAD"))
	private void lorcopika$beginArm(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int lightCoords,
		float inverseArmHeight,
		float attackValue,
		HumanoidArm arm,
		CallbackInfo callbackInfo
	) {
		ClientVisuals.setRenderingArm(arm);
	}

	@Inject(method = "renderPlayerArm", at = @At("RETURN"))
	private void lorcopika$endArm(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int lightCoords,
		float inverseArmHeight,
		float attackValue,
		HumanoidArm arm,
		CallbackInfo callbackInfo
	) {
		ClientVisuals.setRenderingArm(null);
	}

	@Redirect(
		method = "renderPlayerArm",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/core/ClientAsset$Texture;texturePath()Lnet/minecraft/resources/Identifier;")
	)
	private Identifier lorcopika$handTexture(ClientAsset.Texture texture) {
		return ClientVisuals.handTexture(texture);
	}
}
