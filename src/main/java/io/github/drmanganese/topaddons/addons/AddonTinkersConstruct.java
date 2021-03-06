package io.github.drmanganese.topaddons.addons;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import io.github.drmanganese.topaddons.TOPAddons;
import io.github.drmanganese.topaddons.api.TOPAddon;
import io.github.drmanganese.topaddons.elements.tconstruct.ElementSmelteryTank;

import java.util.List;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import slimeknights.tconstruct.gadgets.tileentity.TileDryingRack;
import slimeknights.tconstruct.library.smeltery.SmelteryTank;
import slimeknights.tconstruct.smeltery.tileentity.TileSmeltery;

@TOPAddon(dependency = "tconstruct")
public class AddonTinkersConstruct extends AddonBlank {


    @Override
    public void registerElements() {
        registerElement("smeltery", ElementSmelteryTank::new);
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        TileEntity tile = world.getTileEntity(data.getPos());
        if (tile instanceof TileSmeltery) {
            SmelteryTank tank = ((TileSmeltery) world.getTileEntity(data.getPos())).getTank();
            final boolean inIngots = player.getCapability(TOPAddons.OPTS_CAP, null).getBoolean("smelteryInIngots");
            addSmelteryTankElement(probeInfo, tank.getFluids(), Math.max(tank.getFluidAmount(), tank.getCapacity()), inIngots, mode, getElementId(player, "smeltery"));
        }

        if (tile instanceof TileDryingRack) {
            TileDryingRack tileDrying = (TileDryingRack) tile;
            textPrefixed(probeInfo, "{*topaddons.tico:progress*}", (Math.round(tileDrying.getProgress()  * 100)) + "%");
        }
    }

    private void addSmelteryTankElement(IProbeInfo probeInfo, List<FluidStack> fluids, int capacity, boolean inIngots, ProbeMode mode, int id) {
        probeInfo.element(new ElementSmelteryTank(id, fluids, capacity, inIngots, mode == ProbeMode.EXTENDED));
    }
}
