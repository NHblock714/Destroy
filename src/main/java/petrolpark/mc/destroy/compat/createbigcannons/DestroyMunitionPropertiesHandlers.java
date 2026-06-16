package petrolpark.mc.destroy.compat.createbigcannons;

import petrolpark.mc.destroy.compat.createbigcannons.block.CustomExplosiveMixChargeProperties;

/**
 * Holds the single instance of the Custom-Mix-Charge propellant property handler. CBC's
 * {@code MunitionPropertiesHandler.registerBlockPropellantHandler} is called at block-registration
 * time to bind the block → handler.
*/
public class DestroyMunitionPropertiesHandlers {

    public static final CustomExplosiveMixChargeProperties.Handler CUSTOM_EXPLOSIVE_MIX_CHARGE = new CustomExplosiveMixChargeProperties.Handler();

    public static void init() {
        // Force class-load; static field construction ensures the handler instance exists.
    }
}
