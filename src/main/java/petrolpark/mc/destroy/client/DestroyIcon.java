package petrolpark.mc.destroy.client;

import petrolpark.mc.library.compat.create.registry.PetrolparkIcon;

import net.minecraft.resources.ResourceLocation;
import petrolpark.mc.destroy.Destroy;

public class DestroyIcon extends PetrolparkIcon {

    public static final ResourceLocation ICON_ATLAS = Destroy.asResource("textures/gui/icons.png");

	private static int x = 0, y = -1;

    public static final DestroyIcon
	
        RADIOACTIVITY = newRow(),
        ACID_RAIN = next(),
        OZONE_DEPLETION = next(),
        GREENHOUSE = next(),
        SMOG = next();

		// QUESTION_MARK = new DestroyIcon(0, 1),

		// VAT_SOLUTION = new DestroyIcon(0, 3),
		// VAT_GAS = new DestroyIcon(1, 3),
		// VAT_ALL = new DestroyIcon(2, 3);

	private static DestroyIcon next() {
		return new DestroyIcon(++x, y);
	};

	private static DestroyIcon newRow() {
		return new DestroyIcon(x = 0, ++y);
	};

    public DestroyIcon(int x, int y) {
        super(x, y);
    };

    @Override
	protected ResourceLocation getTextureLocation() {
		return ICON_ATLAS;
	};
    
};
