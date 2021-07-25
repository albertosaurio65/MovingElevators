package com.supermartijn642.movingelevators.base;

import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.model.MEBlockModelData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.model.data.IModelData;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Created 4/6/2020 by SuperMartijn642
 */
public abstract class METile extends TileEntity {

    public METile(TileEntityType<?> tileEntityTypeIn){
        super(tileEntityTypeIn);
    }

    private BlockState camoState = Blocks.AIR.defaultBlockState();

    private boolean dataChanged = false;

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket(){
        if(!this.dataChanged)
            return null;
        this.dataChanged = false;
        CompoundNBT compound = this.getChangedData();
        return compound == null || compound.isEmpty() ? null : new SUpdateTileEntityPacket(this.worldPosition, 0, compound);
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt){
        this.handleData(pkt.getTag());
    }

    @Override
    public CompoundNBT getUpdateTag(){
        CompoundNBT tag = super.getUpdateTag();
        tag.put("info", this.getAllData());
        return tag;
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag){
        super.handleUpdateTag(state, tag);
        this.handleData(tag.getCompound("info"));
    }

    @Override
    public CompoundNBT save(CompoundNBT compound){
        super.save(compound);
        compound.put("info", this.getAllData());
        return compound;
    }

    @Override
    public void load(BlockState state, CompoundNBT compound){
        super.load(state, compound);
        this.handleData(compound.getCompound("info"));
    }

    protected CompoundNBT getChangedData(){
        CompoundNBT data = new CompoundNBT();
        data.putInt("camoState", Block.getId(this.camoState));
        return data;
    }

    protected CompoundNBT getAllData(){
        CompoundNBT data = new CompoundNBT();
        data.putInt("camoState", Block.getId(this.camoState));
        return data;
    }

    protected void handleData(CompoundNBT data){
        if(data.contains("camoState"))
            this.camoState = Block.stateById(data.getInt("camoState"));
        else if(data.contains("hasCamo")){ // Do this for older versions
            if(data.getBoolean("hasCamo"))
                this.camoState = Block.stateById(data.getInt("camo"));
            else
                this.camoState = Blocks.AIR.defaultBlockState();
        }else if(data.contains("camo")){ // Do this for older versions
            ItemStack camoStack = ItemStack.of(data.getCompound("camo"));
            Item item = camoStack.getItem();
            if(item instanceof BlockItem){
                Block block = ((BlockItem)item).getBlock();
                this.camoState = block.defaultBlockState();
            }
        }
    }

    public boolean setCamoState(BlockState state){
        this.camoState = state == null ? Blocks.AIR.defaultBlockState() : state;
        this.dataChanged();
        return true;
    }

    public boolean canBeCamoStack(ItemStack stack){
        if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
            return false;
        Block block = ((BlockItem)stack.getItem()).getBlock();
        return !MovingElevators.CAMOUFLAGE_MOD_BLACKLIST.contains(block.getRegistryName().getNamespace()) && this.isFullCube(block.defaultBlockState());
    }

    private boolean isFullCube(BlockState state){
        List<AxisAlignedBB> shapes = state.getCollisionShape(this.level, this.worldPosition).toAabbs();
        return shapes.size() == 1 && shapes.get(0).equals(new AxisAlignedBB(0, 0, 0, 1, 1, 1));
    }

    public BlockState getCamoBlock(){
        if(this.camoState.getBlock() == Blocks.AIR)
            return this.getBlockState();
        return this.camoState;
    }

    public abstract Direction getFacing();

    protected void dataChanged(){
        this.dataChanged = true;
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        this.setChanged();
    }

    @Override
    public IModelData getModelData(){
        return new MEBlockModelData(this.camoState == null || this.camoState.isAir() ? null : this.camoState);
    }
}
