package petrolpark.mc.destroy.core.chemistry.vat;

import petrolpark.mc.library.core.data.recipe.ingredient.BlockIngredient;

/**
 * Material configuration for a Vat — defines pressure rating, thermal conductivity, transparency,
 * and the set of blocks that count as this material.
*/
public record VatMaterial(double maxPressure, double thermalConductivity, boolean transparent,
                          BlockIngredient<?> blocks) {}
