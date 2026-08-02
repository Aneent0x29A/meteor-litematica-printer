package com.kkllffaa.meteor_litematica_printer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Shredder extends Module {

	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	private final SettingGroup sgWorkMode = settings.createGroup("Work Mode");
	private final SettingGroup sgRender = settings.createGroup("Render");

	public enum FilterMode {
		NONE,
		WHITELIST,
		BLACKLIST
	}

	public enum SortMode {
		None,
		Closest,
		Furthest,
		TopDown
	}

	private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
			.name("range").description("Nuke range.").defaultValue(5.0)
			.min(1).sliderMin(1).max(20).sliderMax(6).build());

	private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
			.name("walls-range").description("Range through walls.").defaultValue(6.0)
			.min(0).sliderMin(0).max(6).build());

	private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
			.name("delay").description("Delay between breaks.").defaultValue(0)
			.min(0).sliderMin(0).max(100).sliderMax(20).build());

	private final Setting<Integer> bpt = sgGeneral.add(new IntSetting.Builder()
			.name("max-blocks-per-tick").description("Max blocks per tick.").defaultValue(100)
			.min(1).sliderMin(1).max(100).build());

	private final Setting<Boolean> breakWrongBlock = sgGeneral.add(new BoolSetting.Builder()
			.name("wrong-block").description("Break blocks with wrong type.").defaultValue(true).build());

	private final Setting<Boolean> breakWrongState = sgGeneral.add(new BoolSetting.Builder()
			.name("wrong-state").description("Break blocks with wrong state/properties.").defaultValue(true).build());

	private final Setting<Boolean> breakExtra = sgGeneral.add(new BoolSetting.Builder()
			.name("extra").description("Break blocks not in schematic.").defaultValue(true).build());

	private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>()
			.name("sort-mode").description("The blocks you want to mine first.")
			.defaultValue(SortMode.Closest).build());

	private final Setting<Boolean> packetMine = sgGeneral.add(new BoolSetting.Builder()
			.name("packet-mine").description("Attempt to instamine everything at once.")
			.defaultValue(false).build());

	private final Setting<Boolean> suitableTools = sgGeneral.add(new BoolSetting.Builder()
			.name("only-suitable-tools").description("Only mines when using an appropriate tool for the block.")
			.defaultValue(false).build());

	private final Setting<Boolean> interact = sgGeneral.add(new BoolSetting.Builder()
			.name("interact").description("Interacts with the block instead of mining.")
			.defaultValue(false).build());

	private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
			.name("rotate").description("Rotate to target block.").defaultValue(true).build());

	private final Setting<FilterMode> listMode = sgWorkMode.add(new EnumSetting.Builder<FilterMode>()
			.name("list-mode").description("Block list mode.").defaultValue(FilterMode.NONE).build());

	private final Setting<List<Block>> filterBlocks = sgWorkMode.add(new BlockListSetting.Builder()
			.name("filter-blocks").description("Blocks to whitelist or blacklist.")
			.visible(() -> listMode.get() != FilterMode.NONE).build());

	private final Setting<Keybind> selectBlockBind = sgWorkMode.add(new KeybindSetting.Builder()
			.name("select-block-bind").description("Adds targeted block to list.")
			.defaultValue(Keybind.none()).build());

	private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder()
			.name("swing").description("Swing hand.").defaultValue(true).build());

	private final Setting<Boolean> showBroken = sgRender.add(new BoolSetting.Builder()
			.name("broken-blocks").description("Show recently broken blocks.").defaultValue(true).build());

	private final Setting<ShapeMode> nukerBlockMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
			.name("nuker-block-mode").description("How broken blocks are rendered.")
			.defaultValue(ShapeMode.Both).visible(showBroken::get).build());

	private final Setting<SettingColor> nukerBlockSideColor = sgRender.add(new ColorSetting.Builder()
			.name("block-side-color").description("Broken block side color.")
			.defaultValue(new SettingColor(255, 0, 0, 80)).visible(showBroken::get).build());

	private final Setting<SettingColor> nukerBlockLineColor = sgRender.add(new ColorSetting.Builder()
			.name("block-line-color").description("Broken block line color.")
			.defaultValue(new SettingColor(255, 0, 0, 255)).visible(showBroken::get).build());

	private int timer;
	private int noBlockTimer;
	private boolean firstBlock;
	private final BlockPos.MutableBlockPos lastBlockPos = new BlockPos.MutableBlockPos();
	private final Set<BlockPos> interacted = new ObjectOpenHashSet<>();
	private final List<BlockPos> blocks = new ArrayList<>();

	public Shredder() {
		super(Addon.CATEGORY, "shredder", "Breaks blocks based on Litematica schematic.");
	}

	@Override
	public void onActivate() {
		firstBlock = true;
		timer = 0;
		noBlockTimer = 0;
		interacted.clear();
		blocks.clear();
	}

	@Override
	public void onDeactivate() {
		blocks.clear();
		interacted.clear();
	}

	@EventHandler
	private void onMouseClick(MouseClickEvent event) {
		if (event.action == KeyAction.Press)
			addTargetedBlockToList();
	}

	@EventHandler
	private void onKey(KeyInputEvent event) {
		if (event.action == KeyAction.Press)
			addTargetedBlockToList();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
		event.cooldown = 0;
	}

	@EventHandler
	private void onTickPre(TickEvent.Pre event) {
		if (mc.player == null || mc.level == null || mc.gameMode == null)
			return;

		WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
		if (ws == null) {
			toggle();
			return;
		}

		if (timer > 0) {
			timer--;
			return;
		}

		double pX = mc.player.getX(), pY = mc.player.getY(), pZ = mc.player.getZ();
		double rangeSq = Math.pow(range.get(), 2);

		equipSilkTool();

		blocks.clear();
		int r = (int) Math.ceil(range.get());

		BlockIterator.register(r + 1, r + 1, (blockPos, blockState) -> {
			double distSq = Utils.squaredDistance(pX, pY, pZ, blockPos.getX() + 0.5, blockPos.getY() + 0.5,
					blockPos.getZ() + 0.5);
			if (distSq > rangeSq)
				return;
			if (blockState.isAir())
				return;

			BlockState schemState = ws.getBlockState(blockPos);
			if (!schemState.isAir()) {
				/* inside schematic */ } else {
				if (!isInsideAnySchematic(blockPos))
					return;
			}

			if (!shouldBreak(schemState, blockState))
				return;

			if (mc.player.getBoundingBox().intersects(Vec3.atLowerCornerOf(blockPos),
					Vec3.atLowerCornerOf(blockPos).add(1, 1, 1)))
				return;

			FilterMode fm = listMode.get();
			if (fm != FilterMode.NONE) {
				boolean inList = filterBlocks.get().contains(blockState.getBlock());
				if (fm == FilterMode.BLACKLIST ? inList : !inList)
					return;
			}

			if (suitableTools.get() && !interact.get()
					&& !mc.player.getMainHandItem().isCorrectToolForDrops(blockState))
				return;

			if (!BlockUtils.canBreak(blockPos, blockState) && !interact.get())
				return;

			if (isOutOfRange(blockPos))
				return;

			if (interact.get() && interacted.contains(blockPos))
				return;

			blocks.add(blockPos.immutable());
		});

		BlockIterator.after(() -> {
			if (sortMode.get() == SortMode.TopDown)
				blocks.sort(Comparator.comparingDouble(value -> -value.getY()));
			else if (sortMode.get() != SortMode.None)
				blocks.sort(Comparator.comparingDouble(value -> Utils.squaredDistance(pX, pY, pZ, value.getX() + 0.5,
						value.getY() + 0.5, value.getZ() + 0.5) * (sortMode.get() == SortMode.Closest ? 1 : -1)));

			if (blocks.isEmpty()) {
				interacted.clear();
				if (noBlockTimer++ >= delay.get())
					firstBlock = true;
				return;
			} else {
				noBlockTimer = 0;
			}

			if (!firstBlock && !lastBlockPos.equals(blocks.getFirst())) {
				timer = delay.get();
				firstBlock = false;
				lastBlockPos.set(blocks.getFirst());
				if (timer > 0)
					return;
			}

			int count = 0;
			for (BlockPos block : blocks) {
				if (count >= bpt.get())
					break;

				boolean canInstaMine = BlockUtils.canInstaBreak(block);

				if (rotate.get())
					Rotations.rotate(Rotations.getYaw(block), Rotations.getPitch(block), () -> breakBlock(block));
				else
					breakBlock(block);

				if (showBroken.get())
					RenderUtils.renderTickingBlock(block, nukerBlockSideColor.get(), nukerBlockLineColor.get(),
							nukerBlockMode.get(), 0, 8, true, false);
				lastBlockPos.set(block);

				count++;
				if (!canInstaMine && !packetMine.get())
					break;
			}

			firstBlock = false;
			blocks.clear();
		});
	}

	private void breakBlock(BlockPos blockPos) {
		if (interact.get()) {
			BlockUtils.interact(
					new BlockHitResult(blockPos.getCenter(), BlockUtils.getDirection(blockPos), blockPos, true),
					InteractionHand.MAIN_HAND, swing.get());
			interacted.add(blockPos);
		} else if (packetMine.get()) {
			mc.getConnection().send(new ServerboundPlayerActionPacket(
					ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos,
					BlockUtils.getDirection(blockPos)));

			if (swing.get())
				mc.player.swing(InteractionHand.MAIN_HAND);
			else
				mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

			mc.getConnection().send(new ServerboundPlayerActionPacket(
					ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos,
					BlockUtils.getDirection(blockPos)));
		} else {
			BlockUtils.breakBlock(blockPos, swing.get());
		}
	}

	private boolean isOutOfRange(BlockPos blockPos) {
		Vec3 pos = blockPos.getCenter();
		ClipContext clipContext = new ClipContext(mc.player.getEyePosition(), pos, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, mc.player);
		BlockHitResult result = mc.level.clip(clipContext);
		if (result == null || !result.getBlockPos().equals(blockPos))
			return !PlayerUtils.isWithin(pos, wallsRange.get());
		return false;
	}

	private void addTargetedBlockToList() {
		if (!selectBlockBind.get().isPressed() || mc.screen != null)
			return;
		HitResult hitResult = mc.hitResult;
		if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK)
			return;
		BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
		Block targetBlock = mc.level.getBlockState(pos).getBlock();
		List<Block> list = filterBlocks.get();

		if (list.contains(targetBlock)) {
			list.remove(targetBlock);
			info("Removed " + Names.get(targetBlock) + " from filter blocks");
		} else {
			list.add(targetBlock);
			info("Added " + Names.get(targetBlock) + " to filter blocks");
		}
	}

	private void equipSilkTool() {
		if (Utils.getEnchantmentLevel(mc.player.getMainHandItem(), Enchantments.SILK_TOUCH) > 0)
			return;
		int bestSlot = -1;
		for (int i = 0; i < 9; i++) {
			if (Utils.getEnchantmentLevel(mc.player.getInventory().getItem(i), Enchantments.SILK_TOUCH) > 0) {
				bestSlot = i;
				break;
			}
		}
		if (bestSlot != -1)
			InvUtils.swap(bestSlot, false);
	}

	private boolean isInsideAnySchematic(BlockPos pos) {
		SchematicPlacementManager spm = DataManager.getSchematicPlacementManager();
		for (SchematicPlacement sp : spm.getAllSchematicsPlacements()) {
			BlockPos origin = sp.getOrigin();
			var schematic = sp.getSchematic();
			if (schematic == null)
				continue;
			var size = schematic.getTotalSize();
			if (pos.getX() >= origin.getX() && pos.getX() < origin.getX() + size.getX() &&
					pos.getY() >= origin.getY() && pos.getY() < origin.getY() + size.getY() &&
					pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + size.getZ()) {
				return true;
			}
		}
		return false;
	}

	private boolean shouldBreak(BlockState schem, BlockState world) {
		if (schem.isAir())
			return breakExtra.get();
		if (world.isAir())
			return false;
		if (world.getBlock() != schem.getBlock())
			return breakWrongBlock.get();
		if (breakWrongState.get()) {
			for (var prop : schem.getProperties()) {
				if (!world.hasProperty(prop))
					return true;
				if (!schem.getValue(prop).equals(world.getValue(prop)))
					return true;
			}
		}
		return false;
	}
}
