package com.annonasoftware.games.woodworking.objects.block;

/*
 *  Copyright (c) 2020 madflavius under the terms of GPL v3 
 *  Most of the tree/wood logic taken from net.dries007.tfc.objects.blocks.wood.BlockLogTFC.java.
 *   Thank you to the TFC TNG team!
 */

import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.annonasoftware.games.woodworking.client.WoodworkingSounds;
import com.annonasoftware.games.woodworking.objects.item.ItemSplitLog;
import com.annonasoftware.games.woodworking.objects.te.TEChoppingBlock;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import mcp.MethodsReturnNonnullByDefault;
import net.dries007.tfc.Constants;
import net.dries007.tfc.api.capability.size.IItemSize;
import net.dries007.tfc.api.capability.size.Size;
import net.dries007.tfc.api.capability.size.Weight;
import net.dries007.tfc.api.types.Tree;
import net.dries007.tfc.objects.blocks.wood.BlockLogTFC;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.OreDictionaryHelper;

import static net.minecraft.block.material.Material.WOOD;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class BlockChoppingBlock extends Block implements IItemSize
{
    public static final AxisAlignedBB SMALL_AABB = new AxisAlignedBB(0.125, 0, 0.125, 0.875, 0.5, 0.875);
    public static final PropertyBool LOG_PLACED = PropertyBool.create("log_placed");

    private static final Map<Tree, BlockChoppingBlock> MAP = new HashMap<>();

    public static BlockChoppingBlock get(Tree wood)
    {
        return MAP.get(wood);
    }

    public Tree wood;

    public BlockChoppingBlock(Tree wood)
    {
        super(WOOD, MapColor.AIR);
        if (MAP.put(wood, this) != null) throw new IllegalStateException("There can only be one.");
        this.wood = wood;
        setDefaultState(blockState.getBaseState().withProperty(LOG_PLACED, false));
        setHarvestLevel("axe", 0);
        setHardness(15.0F);
        setResistance(5.0F);
        //OreDictionaryHelper.register(this, "log", "wood");
        Blocks.FIRE.setFireInfo(this, 5, 5);
        setTickRandomly(true);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        final ItemStack stack = playerIn.getHeldItemMainhand();

        //right click to place a log on the chopping block
        if(OreDictionaryHelper.doesStackMatchOre(stack, "logWood") && !worldIn.getBlockState(pos).getValue(LOG_PLACED))
        {
            //only works if there's room on the block and room to swing
            if(worldIn.getBlockState(pos.up(2)).getBlock().equals(Blocks.AIR) && worldIn.getBlockState(pos.up()).getBlock().equals(Blocks.AIR))
            {
                //just to double check there's not some weird log item
                if(stack.getItem() instanceof ItemBlock);
                {
                    worldIn.setBlockState(pos, this.getDefaultState().withProperty(LOG_PLACED, true), worldIn.isRemote ? 11 : 3);
                    //stores log in TE
                    TEChoppingBlock te = Helpers.getTE(worldIn, pos, TEChoppingBlock.class);
                    if(te != null)
                    {
                        //all this jazz so we only grab one log
                        ItemBlock ib = (ItemBlock)stack.getItem();
                        BlockLogTFC logWood = (BlockLogTFC)ib.getBlock();
                        ItemStack newStack = new ItemStack(logWood, 1);
                        te.setLog(newStack);
                    }
                    stack.splitStack(1);
                    return true;
                }
            }        
        }

        //if there's already a log in place...
        if(worldIn.getBlockState(pos).getValue(LOG_PLACED))
        {
            //first grab what's in the inventory, used in both situations
            TEChoppingBlock te = Helpers.getTE(worldIn, pos, TEChoppingBlock.class);

            if (te != null)
            {
                IItemHandler cap = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (cap != null)
                {
                    ItemStack contents = cap.extractItem(0, 64, false);

                    if(OreDictionaryHelper.doesStackMatchOre(contents, "logWood"))
                    {
                        ItemBlock ib = (ItemBlock)(contents.getItem());
                        BlockLogTFC log = (BlockLogTFC)ib.getBlock();
                        Tree logWood = log.getWood();

                        //...then either split it on axe hit
                        final Set<String> toolClasses = stack.getItem().getToolClasses(stack);
                        if (toolClasses.contains("axe") && !toolClasses.contains("saw"))
                        {        
                            worldIn.playSound(playerIn, pos, WoodworkingSounds.WOOD_LOG_SPLIT, SoundCategory.BLOCKS, 1.0F, 1.0F);
                            
                            if(!worldIn.isRemote)
                            {
                                for(int i = 0; i < 6; i++)
                                {
                                    worldIn.spawnParticle(EnumParticleTypes.BLOCK_DUST,
                                                            (double)pos.getX()+Constants.RNG.nextDouble(),
                                                            (double)pos.getY()+1f,
                                                            (double)pos.getZ()+Constants.RNG.nextDouble(),
                                                            0.2D, 0.4D, 0.2D, Block.getStateId(state));
                                }
                                Helpers.spawnItemStack(worldIn, pos.add(0.5d, 0.5d, 0.5d), new ItemStack(ItemSplitLog.get(logWood), 4));
                            }
                        }

                        //or remove it back to player's inventory
                        else
                        {
                            ItemHandlerHelper.giveItemToPlayer(playerIn, contents);
                        }
                    }
                    //TODO: if there's a log in place and player is using froe, split into shingles

                }
            }
            
            return worldIn.setBlockState(pos, this.getDefaultState(), worldIn.isRemote ? 11 : 3);
        }

        return false;
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state)
    {
        TEChoppingBlock te = Helpers.getTE(worldIn, pos, TEChoppingBlock.class);
        if (te != null)
        {
            te.onBreakBlock(worldIn, pos, state);
        }
        super.breakBlock(worldIn, pos, state);
    }
    
    @Override
    @Nonnull
    public IBlockState getStateFromMeta(int meta)
    {
        return getDefaultState().withProperty(LOG_PLACED, (meta & 0b01) == 0b01);
    }

    @Override
    public int getMetaFromState(IBlockState state)
    {
        return state.getValue(LOG_PLACED) ? 0b01 : 0;
    }

    @Override
    protected BlockStateContainer createBlockState()
    {
        return new BlockStateContainer(this, LOG_PLACED);
    }

    @Override
    public boolean isFullBlock(IBlockState state)
    {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state)
    {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state)
    {
        return false;
    }

    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos)
    {
        return state.getValue(LOG_PLACED) ? FULL_BLOCK_AABB : SMALL_AABB;
    }

    @Override
    public Size getSize(ItemStack stack)
    {
        return Size.LARGE;
    }

    @Override
    public Weight getWeight(ItemStack stack)
    {
        return Weight.HEAVY;
    }

    @Override
    @Nonnull
    public EnumBlockRenderType getRenderType(IBlockState state)
    {
        return EnumBlockRenderType.MODEL;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @Nonnull
    public BlockRenderLayer getRenderLayer()
    {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean hasTileEntity(IBlockState state)
    {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state)
    {
        return new TEChoppingBlock();
    }
}