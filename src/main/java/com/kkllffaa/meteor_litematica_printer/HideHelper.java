package com.kkllffaa.meteor_litematica_printer;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class HideHelper {

	public static boolean active;
	public static int opacity = 25;

	public static int getAlpha(BlockState state, BlockPos pos) {
		if (!active)
			return -1;
		if (state.isAir())
			return -1;

		WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
		if (ws == null)
			return -1;

		BlockState schem = ws.getBlockState(pos);
		if (schem.isAir())
			return -1;
		if (schem.getBlock() != state.getBlock())
			return -1;

		for (var prop : schem.getProperties()) {
			if (!state.hasProperty(prop))
				return -1;
			if (!schem.getValue(prop).equals(state.getValue(prop)))
				return -1;
		}

		return opacity;
	}
}
