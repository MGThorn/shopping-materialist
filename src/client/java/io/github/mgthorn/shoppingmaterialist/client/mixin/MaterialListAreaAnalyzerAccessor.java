package io.github.mgthorn.shoppingmaterialist.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import fi.dy.masa.litematica.materials.MaterialListAreaAnalyzer;
import fi.dy.masa.litematica.selection.AreaSelection;

@Mixin(MaterialListAreaAnalyzer.class)
public interface MaterialListAreaAnalyzerAccessor {
	@Accessor("selection")
	AreaSelection shoppingMaterialist$getSelection();
}
