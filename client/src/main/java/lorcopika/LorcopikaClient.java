package lorcopika;

import lorcopika.client.ClientConfig;
import lorcopika.client.EyeDarknessHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public final class LorcopikaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LorcopikaCommon.init();
		ClientConfig.get();
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.MISC_OVERLAYS,
			Identifier.fromNamespaceAndPath("lorcopika", "eye_darkness"),
			new EyeDarknessHud()
		);
	}
}
