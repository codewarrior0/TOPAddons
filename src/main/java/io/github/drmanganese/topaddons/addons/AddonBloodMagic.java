package io.github.drmanganese.topaddons.addons;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import io.github.drmanganese.topaddons.api.TOPAddon;
import io.github.drmanganese.topaddons.elements.bloodmagic.ElementAltarCrafting;
import io.github.drmanganese.topaddons.elements.bloodmagic.ElementNodeFilter;
import io.github.drmanganese.topaddons.reference.EnumChip;
import io.github.drmanganese.topaddons.reference.Names;

import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import WayofTime.bloodmagic.altar.BloodAltar;
import WayofTime.bloodmagic.api.altar.IBloodAltar;
import WayofTime.bloodmagic.api.iface.IBindable;
import WayofTime.bloodmagic.api.orb.BloodOrb;
import WayofTime.bloodmagic.api.orb.IBloodOrb;
import WayofTime.bloodmagic.api.registry.OrbRegistry;
import WayofTime.bloodmagic.api.saving.SoulNetwork;
import WayofTime.bloodmagic.api.util.helper.NetworkHelper;
import WayofTime.bloodmagic.item.armour.ItemLivingArmour;
import WayofTime.bloodmagic.item.armour.ItemSentientArmour;
import WayofTime.bloodmagic.item.sigil.ItemSigilBase;
import WayofTime.bloodmagic.item.sigil.ItemSigilHolding;
import WayofTime.bloodmagic.registry.ModBlocks;
import WayofTime.bloodmagic.registry.ModItems;
import WayofTime.bloodmagic.routing.IMasterRoutingNode;
import WayofTime.bloodmagic.tile.TileAltar;
import WayofTime.bloodmagic.tile.TileIncenseAltar;
import WayofTime.bloodmagic.tile.TileMimic;
import WayofTime.bloodmagic.tile.routing.TileFilteredRoutingNode;
import WayofTime.bloodmagic.util.helper.NumeralHelper;
import mcjty.theoneprobe.Tools;
import mcjty.theoneprobe.api.ElementAlignment;
import mcjty.theoneprobe.api.IBlockDisplayOverride;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;

import static mcjty.theoneprobe.api.TextStyleClass.MODNAME;

@TOPAddon(dependency = "BloodMagic")
public class AddonBloodMagic extends AddonBlank {

    private boolean requireSigil = true;
    private boolean seeMimickWithSigil = true;

    @Override
    public Map<Class<? extends ItemArmor>, EnumChip> getSpecialHelmets() {
        Map<Class<? extends ItemArmor>, EnumChip> map = new HashMap<>();
        map.put(ItemLivingArmour.class, EnumChip.STANDARD);
        map.put(ItemSentientArmour.class, EnumChip.STANDARD);
        return map;
    }

    @Override
    public void registerElements() {
        registerElement("filter_node", ElementNodeFilter::new);
        registerElement("altar_crafting", ElementAltarCrafting::new);
    }

    @Override
    public void addTankNames() {
        Names.tankNamesMap.put(TileAltar.class, new String[]{"Blood Altar"});
    }

    @Override
    public void updateConfigs(Configuration config) {
        requireSigil = config.get("bloodmagic", "requireSigil", true, "Is holding a divination sigil required to see certain information.").setLanguageKey("topaddons.config:bloodmagic_sigil").getBoolean();
        seeMimickWithSigil = config.get("bloodmagic", "seeMimickWithSigil", true, "Shows the player that they're looking at a mimick block when holding a seer sigil.").setLanguageKey("topaddons.config:bloodmagic_mimick_sigil").getBoolean();
    }

    @Override
    public List<IBlockDisplayOverride> getBlockDisplayOverrides() {
        return Lists.newArrayList((IBlockDisplayOverride) (mode, probeInfo, player, world, blockState, data) -> {
            /*
             * Show the mimic block's "mimicked" block when it has an ItemBlock in its
             * internal inventory.
             */
            if (blockState.getBlock() == ModBlocks.MIMIC) {
                ItemStack mimicStack = ((TileMimic) world.getTileEntity(data.getPos())).getStackInSlot(0);

                if (mimicStack != null && mimicStack.getItem() instanceof ItemBlock) {
                    if (Tools.show(mode, mcjty.theoneprobe.config.Config.getRealConfig().getShowModName())) {
                        probeInfo.horizontal()
                                .item(mimicStack)
                                .vertical()
                                .itemLabel(mimicStack)
                                .text(MODNAME + Tools.getModName(((ItemBlock) mimicStack.getItem()).getBlock()));
                    } else {
                        probeInfo.horizontal(probeInfo.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER))
                                .item(mimicStack)
                                .itemLabel(mimicStack);
                    }

                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        boolean holdingSeer = !requireSigil || holdingSigil(player, (ItemSigilBase) ModItems.SIGIL_SEER);
        boolean holdingDivine = !requireSigil || holdingSigil(player, (ItemSigilBase) ModItems.SIGIL_DIVINATION) || holdingSeer;

        TileEntity tile = world.getTileEntity(data.getPos());
        if (tile instanceof IBloodAltar && holdingDivine) {
            IBloodAltar altar = (IBloodAltar) tile;
            textPrefixed(probeInfo, "{*topaddons.bloodmagic:tier*}", NumeralHelper.toRoman(altar.getTier().toInt()), TextFormatting.RED);

            if (altar instanceof TileAltar && holdingSeer) {
                ItemStack input = ((TileAltar) altar).getStackInSlot(0);
                if (input == null) return;
                BloodAltar bloodAltar = ReflectionHelper.getPrivateValue(TileAltar.class, (TileAltar) altar, "bloodAltar");

                if (input.getItem() instanceof IBloodOrb) {
                    String owner = ((IBindable) input.getItem()).getOwnerUUID(input);
                    if (!owner.isEmpty()) {
                        SoulNetwork network = NetworkHelper.getSoulNetwork(((IBindable) input.getItem()).getOwnerUUID(input));
                        BloodOrb orb = OrbRegistry.getOrb(network.getOrbTier() - 1);
                        addAltarCraftingElement(probeInfo, input, new ItemStack(WayofTime.bloodmagic.registry.ModItems.BLOOD_ORB, 1, network.getOrbTier() - 1), network.getCurrentEssence(), orb.getCapacity(), 0, player);
                    } else {
                        probeInfo.text(TextStyleClass.WARNING + "{*topaddons.bloodmagic:unbound_orb*}");
                    }
                } else if (altar.isActive()) {
                    ItemStack result = ReflectionHelper.getPrivateValue(BloodAltar.class, bloodAltar, "result");
                    if (result != null) {
                        addAltarCraftingElement(probeInfo, input, result, bloodAltar.getProgress(), bloodAltar.getLiquidRequired(), bloodAltar.getConsumptionRate(), player);
                    }
                }
            }
        }


        if (tile instanceof TileFilteredRoutingNode && !(tile instanceof IMasterRoutingNode)) {
            TileFilteredRoutingNode node = (TileFilteredRoutingNode) tile;
            ItemStack filterStack = node.getFilterStack(data.getSideHit());
            if (filterStack != null) {
                BlockPos sidePos = data.getPos().offset(data.getSideHit());
                if (world.getTileEntity(sidePos) != null) {
                    IBlockState sideState = world.getBlockState(sidePos);
                    ItemStack inventoryOnSide = sideState.getBlock().getPickBlock(sideState, new RayTraceResult(data.getHitVec(), data.getSideHit().getOpposite(), sidePos), world, sidePos, player);
                    addFilterElement(probeInfo, data.getSideHit().getName(), inventoryOnSide, filterStack, player);
                }
            }
        }

        if (tile instanceof TileIncenseAltar && holdingDivine) {
            TileIncenseAltar altar = (TileIncenseAltar) tile;
            textPrefixed(probeInfo, "{*topaddons.bloodmagic:tranquility*}", (int) ((100D * (int) (100 * altar.tranquility)) / 100D) + "");
            textPrefixed(probeInfo, "{*topaddons.bloodmagic:bonus*}", (int) (altar.incenseAddition * 100) + "%");
        }

        if (tile instanceof TileMimic && (!seeMimickWithSigil || holdingSeer)) {
            ItemStack mimicStack = ((TileMimic) world.getTileEntity(data.getPos())).getStackInSlot(0);
            if (mimicStack != null) {
                probeInfo.text(TextStyleClass.INFO + data.getPickBlock().getDisplayName());
            }
        }
    }

    private void addFilterElement(IProbeInfo probeInfo, String side, ItemStack inventoryOnSide, ItemStack filterStack, EntityPlayer player) {
        probeInfo.element(new ElementNodeFilter(getElementId(player, "filter_node"), side, inventoryOnSide, filterStack));
    }

    private void addAltarCraftingElement(IProbeInfo probeInfo, ItemStack input, ItemStack result, int progress, int required, float consumption, EntityPlayer player) {
        probeInfo.element(new ElementAltarCrafting(getElementId(player, "altar_crafting"), input, result, progress, required * input.stackSize, consumption));
    }

    private boolean holdingSigil(EntityPlayer player, ItemSigilBase sigil) {
        for (EnumHand hand : EnumHand.values()) {
            ItemStack heldStack = player.getHeldItem(hand);
            if (heldStack != null) {
                if (heldStack.getItem() == sigil) {
                    return true;
                } else if (heldStack.getItem() == ModItems.SIGIL_HOLDING) {
                    ItemStack currentHoldingStack = ItemSigilHolding.getItemStackInSlot(heldStack, ItemSigilHolding.getCurrentItemOrdinal(heldStack));
                    return currentHoldingStack != null && currentHoldingStack.getItem() == sigil;
                }
            }
        }

        return false;
    }
}
