package thut.tech.common.entity;

import static thut.api.ThutBlocks.*;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import thut.api.ThutBlocks;
import thut.api.entity.IMultiBox;
import thut.api.maths.Matrix3;
import thut.api.maths.Vector3;
import thut.tech.common.blocks.tileentity.TileEntityLiftAccess;
import thut.tech.common.handlers.ConfigHandler;
import thut.tech.common.items.ItemLinker;
import thut.tech.common.network.PacketPipeline;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;

public class EntityLift extends EntityLivingBase implements IEntityAdditionalSpawnData, IMultiBox
{
	public double size = 1;
	public double speedUp = ConfigHandler.LiftSpeedUp;
	public double speedDown = -ConfigHandler.LiftSpeedDown;
	public static int ACCELERATIONTICKS = 20;
	public double acceleration = 0.05;
	public boolean up = true;
	public boolean toMoveY = false;
	public boolean moved = false;
	public boolean axis = true;
	public boolean hasPassenger = false;
	public static boolean AUGMENTG = true;
	int n = 0;
	int passengertime = 10;
	boolean first = true;
	Random r = new Random();
	public UUID id = UUID.randomUUID();
	private static HashMap<UUID, EntityLift> lifts = new HashMap();
	
	public double prevFloorY = 0;
	public double prevFloor = 0;
	
	public boolean called = false;
	TileEntityLiftAccess current;
	
	Matrix3 mainBox = new Matrix3();
	
	public ConcurrentHashMap<String, Matrix3> boxes = new ConcurrentHashMap<String, Matrix3>();
	public ConcurrentHashMap<String, Vector3> offsets = new ConcurrentHashMap<String, Vector3>();
	
	public int[] floors = new int[64];

	Matrix3 base = new Matrix3();
	Matrix3 top = new Matrix3();
	Matrix3 wall1 = new Matrix3();
	
	public EntityLift(World par1World) 
	{
		super(par1World);
		this.ignoreFrustumCheck = true;
		this.hurtResistantTime =0;
		this.isImmuneToFire = true;
		for(int i = 0; i<64; i++)
		{
			floors[i] = -1;
		}
	}
	
	public boolean canRenderOnFire()
	{
		return false;
	}
	
    /**
     * Checks if the entity's current position is a valid location to spawn this entity.
     */
    public boolean getCanSpawnHere()
    {
    	return false;
    }
	
    public boolean isPotionApplicable(PotionEffect par1PotionEffect)
    {
    	return false;
    }
    
	public EntityLift(World world, double x, double y, double z, double size)
	{
		this(world);
		this.setPosition(x, y, z);
		r.setSeed(100);
		this.size = Math.max(size, 1);
		this.setSize((float)this.size, 1f);
		lifts.put(id, this);
	}
	
	@Override
	public void onUpdate()
	{
		this.prevPosY = posY;
		if((int)size!=(int)this.width)
		{
			this.setSize((float)size, 1f);
		}
		
		if(first)
		{
			checkRails(0);
			first = false;
		}
		clearLiquids();
		
		if(!checkBlocks(0))
			toMoveY=false;
		
		toMoveY = called = getDestY()>0;
		up = getDestY() > posY;
		
		accelerate();
		if(toMoveY)
		{
			doMotion();
		}
		
		checkCollision();
		passengertime = hasPassenger?20:passengertime-1;
		n++;
	}
	
	public void passengerCheck()
	{
		List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox);
		if(list.size()>0)
		{
			hasPassenger = true;
		}
		else
		{
			hasPassenger = false;
		}
	}

	public void call(int floor)
	{
		if(floor == 0||floor>64)
		{
			return;
		}
		if(floors[floor-1]>0)
		{
			callYValue(floors[floor-1]);
			setDestinationFloor(floor);
		}
	}
	
	public void callYValue(int yValue)
	{
		setDestY(yValue);
	}
	
	public void accelerate()
	{
		motionX = 0;
		motionZ = 0;
		if(!toMoveY)
			motionY *= 0.5;
		else
		{
			if(up)
				motionY = Math.min(speedUp, motionY + acceleration*speedUp);
			else
				motionY = Math.max(speedDown, motionY + acceleration*speedDown);
		}
	}
	
	public void doMotion()
	{
		if(up)
		{
			if(checkBlocks(motionY*(ACCELERATIONTICKS+1)))
			{
				setPosition(posX, posY+motionY, posZ);
				moved = true;
				return;
			}
			else
			{
				while(motionY>=0&&!checkBlocks((motionY - acceleration*speedUp/10)*(ACCELERATIONTICKS+1)))
				{
					motionY = motionY - acceleration*speedUp/10;
				}
				
				if(checkBlocks(motionY))
				{
					setPosition(posX, posY+motionY, posZ);
					moved = true;
					return;
				}
				else
				{
					setPosition(posX, Math.abs(posY-getDestY())<0.5?getDestY():Math.floor(posY), posZ);
					called = false;
					prevFloor = getDestinationFloor();
					prevFloorY = getDestY();
					setDestY(-1);
					setDestinationFloor(0);
					if(current!=null)
					{
						current.setCalled(false);
						worldObj.scheduleBlockUpdate(current.xCoord, current.yCoord, current.zCoord, current.getBlockType(), 5);
						current = null;
					}
					motionY = 0;
					toMoveY = false;
					moved = false;
				}
			}
		}
		else
		{
			if(checkBlocks(motionY*(ACCELERATIONTICKS+1)))
			{
				setPosition(posX, posY+motionY, posZ);
				moved = true;
				return;
			}
			else
			{
				while(motionY<=0&&!checkBlocks((motionY - acceleration*speedDown/10)*(ACCELERATIONTICKS+1)))
				{
					motionY = motionY - acceleration*speedDown/10;
				}
				
				if(checkBlocks(motionY))
				{
					setPosition(posX, posY+motionY, posZ);
					moved = true;
					return;
				}
				else
				{
					setPosition(posX, Math.abs(posY-getDestY())<0.5?getDestY():Math.floor(posY), posZ);
					called = false;
					prevFloor = getDestinationFloor();
					prevFloorY = getDestY();
					setDestY(-1);
					setDestinationFloor(0);
					if(current!=null)
					{
						current.setCalled(false);
						worldObj.scheduleBlockUpdate(current.xCoord, current.yCoord, current.zCoord, current.getBlockType(), 5);
						current = null;
					}
					motionY = 0;
					toMoveY = false;
					moved = false;
				}
			}
		}
		toMoveY = false;
		moved = false;
	}
	
	public boolean checkBlocks(double dir)
	{
		boolean ret = true;
		Vector3 thisloc = new Vector3(this);
		thisloc = thisloc.add(new Vector3(0,dir,0));
		
		if(called)
		{
			if(dir > 0 && thisloc.y > getDestY())
			{
				return false;
			}
			if(dir < 0 && thisloc.y < getDestY())
			{
				return false;
			}
		}

		int rad = (int)(Math.floor(size/2));
		
		for(int i = -rad; i<=rad;i++)
			for(int j = -rad;j<=rad;j++)
			{
				Vector3 checkTop = (thisloc.add(new Vector3(i,4,j)));
				Vector3 checkBottom = (thisloc.add(new Vector3(i,1,j)));
				ret = ret && (thisloc.add(new Vector3(i,0,j))).clearOfBlocks(worldObj);
				ret = ret && (thisloc.add(new Vector3(i,5,j))).clearOfBlocks(worldObj);
				if(checkTop.isFluid(worldObj))
				{
					checkTop.setAir(worldObj);
				}
				if(checkBottom.isFluid(worldObj))
				{
					checkBottom.setAir(worldObj);
				}
			}

		ret = ret && checkRails(dir);
		return ret;
	}
	
	public void clearLiquids()
	{
		int rad = (int)(Math.floor(size/2));

		Vector3 thisloc = new Vector3(this);
		for(int i = -rad; i<=rad;i++)
			for(int j = -rad;j<=rad;j++)
			{
				Vector3 check = (thisloc.add(new Vector3(i,5,j)));
				if(check.isFluid(worldObj))
				{
					check.setBlock(worldObj, air,0);
				}
				check = (thisloc.add(new Vector3(i,0,j)));
				if(check.isFluid(worldObj))
				{
					check.setBlock(worldObj, air,0);
				}
			}
	}
	
	public boolean checkRails(double dir)
	{
		int rad = (int)(1+Math.floor(size/2));
		
		int[][] sides = {{rad,0},{-rad,0},{0,rad},{0,-rad}};
		
		boolean ret = true;
		
		for(int i = 0; i<5; i++)
		{
			Vector3 a = new Vector3((int)Math.floor(posX)+sides[axis?2:0][0],(int)Math.floor(posY+dir+i),(int)Math.floor(posZ)+sides[axis?2:0][1]);
			Vector3 b = new Vector3((int)Math.floor(posX)+sides[axis?3:1][0],(int)Math.floor(posY+dir+i),(int)Math.floor(posZ)+sides[axis?3:1][1]);
			
			ret = ret&&a.getBlock(worldObj)==liftRail;
			ret = ret&&b.getBlock(worldObj)==liftRail;
			
			if(ret)
			{
				TileEntityLiftAccess teA = (TileEntityLiftAccess) a.getTileEntity(worldObj);
				TileEntityLiftAccess teB = (TileEntityLiftAccess) b.getTileEntity(worldObj);
				if(teA.lift==null)
					teA.setLift(this);
				if(teB.lift==null)
					teB.setLift(this);
			}
		}
		
		if((!ret&&dir==0))
		{
			axis = !axis;
			for(int i = 0; i<5; i++)
			{
				ret = ret&&worldObj.getBlock((int)Math.floor(posX)+sides[axis?2:0][0],(int)Math.floor(posY+dir+i),(int)Math.floor(posZ)+sides[axis?2:0][1])==liftRail;
				ret = ret&&worldObj.getBlock((int)Math.floor(posX)+sides[axis?3:1][0],(int)Math.floor(posY+dir+i),(int)Math.floor(posZ)+sides[axis?3:1][1])==liftRail;
			}
		}
		
		return ret;
	}
	
	private boolean consumePower()
	{
		boolean power = false;
		int sizeFactor = size == 1?4:size==3?23:55;
		double energyCost = 0;//(destinationY - posY)*ENERGYCOST*sizeFactor;
		if(energyCost<=0)
			return true;
		if(!power)
			toMoveY = false;
		return power;
		
	}
    public void checkCollision()
    {
        List list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, AxisAlignedBB.getBoundingBox(posX - (size+1), posY, posZ - (size+1), posX+(size+1), posY + 6, posZ + (size+1)));
        
        setOffsets();
        setBoxes();
        
        if (list != null && !list.isEmpty())
        {
        	if(list.size() == 1 && this.riddenByEntity!=null)
        	{
        		return;
        	}
        	
            for (int i = 0; i < list.size(); ++i)
            {
                Entity entity = (Entity)list.get(i);
//                if(entity!=this.riddenByEntity&&!(entity instanceof CopyOfEntityLift))
                {
                	applyEntityCollision(entity);
                }
            }
        }
    }
	
    /**
     * Applies a velocity to each of the entities pushing them away from each other. Args: entity
     */
    public void applyEntityCollision(Entity entity)
    {
    	boolean collided = false;
    	for(String key: boxes.keySet())
    	{
    		Matrix3 box = boxes.get(key);
    		Vector3 offset = new Vector3();
    		if(offsets.containsKey(key))
    		{
    			offset = offsets.get(key);
    		}
    		if(box!=null)
    		{
    			boolean push = box.pushOutOfBox((Entity)this, entity, offset);
    			collided = push || collided;
    			if(key.contains("top")||key.contains("base"))
    			{
                    if(AUGMENTG&&push&&toMoveY&&!up)
                    {
                    	entity.motionY+=motionY;
                    }
    			}
    			if(push)
    			{
    				
    			}
    		}
    	}
    	
    	if(!collided)
    	{
	    	Vector3 rotation = mainBox.boxRotation();	
	    	Vector3 r = ((new Vector3(entity)).subtract(new Vector3(this)));
	    	if(!(rotation.y==0&&rotation.z==0))
	    	{
	    		r = r.rotateAboutAngles(rotation.y, rotation.z);
	    	}
	    	if(r.inMatBox(mainBox))
	    	{
	    		entity.setPosition(entity.posX + motionX, entity.posY, entity.posZ+motionZ);
	    	}
    	}
    	
    }
	
    /**
     * First layer of player interaction
     */
    public boolean interactFirst(EntityPlayer player)
    {
    	ItemStack item = player.getHeldItem();
    	//System.out.println(FMLCommonHandler.instance().getEffectiveSide()+" "+getCurrentFloor());
    	if(player.isSneaking()&&item!=null&&item.getItem() instanceof ItemLinker)
    	{
           	if(item.stackTagCompound == null)
        	{
        		item.setTagCompound(new NBTTagCompound() );
        	}
           	item.stackTagCompound.setString("lift", id.toString());
           	if(worldObj.isRemote)
           	player.addChatMessage(new ChatComponentText("lift set"));
           	return true;
    	}
    	if(player.isSneaking()&&item!=null&&(
    			player.getHeldItem().getItem().getUnlocalizedName().toLowerCase().contains("wrench")
				 ||player.getHeldItem().getItem().getUnlocalizedName().toLowerCase().contains("screwdriver")
				 ||player.getHeldItem().getItem().getUnlocalizedName().equals(Items.stick.getUnlocalizedName())
    			))
		 {
    		if(worldObj.isRemote)
    		{
    			player.addChatMessage(new ChatComponentText("killed lift"));
    		}
    		setDead();
    		return true;
    	}
    	if(item!=null&&(
				 player.getHeldItem().getItem().getUnlocalizedName().toLowerCase().contains("wrench")
				 ||player.getHeldItem().getItem().getUnlocalizedName().toLowerCase().contains("screwdriver")))
		 {

    		axis = !axis;
    		return true;
		 }
    	
    	return false;
    }
	
    /**
     * Will get destroyed next tick.
     */
    public void setDead()
    {
    	if(!worldObj.isRemote&&!this.isDead)
    	{
    		int iron = size == 1?4:size==3?23:55;
	    	this.dropItem(Item.getItemFromBlock(Blocks.iron_block), iron);
	    	this.dropItem(Item.getItemFromBlock(ThutBlocks.lift), 1);
    	}
        super.setDead();
    }
	

	@Override
	public void writeSpawnData(ByteBuf data) {
		data.writeDouble(size);
		data.writeLong(id.getMostSignificantBits());
		data.writeLong(id.getLeastSignificantBits());
		for(int i = 0; i<64; i++)
		{
			data.writeInt(floors[i]);
		}
	}

	@Override
	public void readSpawnData(ByteBuf data) {
		size = data.readDouble();
		id = new UUID(data.readLong(), data.readLong());
		for(int i = 0; i<64; i++)
		{
			floors[i] = data.readInt();
		}
		lifts.put(id, this);
		this.setSize((float)this.size, 1f);
	}

	public void setFoor(TileEntityLiftAccess te, int floor)
	{
		if(te.floor == 0)
		{
			floors[floor-1] = te.yCoord-2;
		}
		else if(te.floor!=0)
		{
			floors[te.floor-1] = -1;
			floors[floor-1] = te.yCoord -2;
		}
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);
		axis = nbt.getBoolean("axis");
		size = nbt.getDouble("size");
		id = new UUID(nbt.getLong("higher"), nbt.getLong("lower"));
		lifts.put(id, this);
		readList(nbt);
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound nbt) {
		super.writeEntityToNBT(nbt);
		nbt.setBoolean("axis", axis);
		nbt.setDouble("size", size);
		nbt.setLong("lower", id.getLeastSignificantBits());
		nbt.setLong("higher", id.getMostSignificantBits());
		writeList(nbt);
	}

	public void writeList(NBTTagCompound nbt)
	{
		for(int i = 0; i<64; i++)
		{
			nbt.setInteger("floors "+i, floors[i]);
		}
	}
	
	public void readList(NBTTagCompound nbt)
	{
		for(int i = 0; i<64; i++)
		{
			floors[i] = nbt.getInteger("floors "+i);
			if(floors[i] == 0)
				floors[i] = -1;
		}
	}
	
	public static EntityLift getLiftFromUUID(UUID uuid)
	{
		return lifts.get(uuid);
	}

	@Override
	public void setBoxes()
	{
		mainBox = new Matrix3(new Vector3(-size/2,0,-size/2), new Vector3(size/2,5,size/2));
        boxes.put("base", new Matrix3(size, 1, size));
        boxes.put("top", new Matrix3(size, 0.5, size));
        boxes.put("wall1", new Matrix3(1,5,1));
        boxes.put("wall2", new Matrix3(1,5,1));
        
	}

	@Override
	public void setOffsets() 
	{
		offsets.put("top", new Vector3(0-size/2,5*0.9,0-size/2));
		offsets.put("base", new Vector3(0-size/2,0,0-size/2));
    	double wallOffset = size/2 + 0.5;
    	if(!axis)
    	{
    		offsets.put("wall1",new Vector3(wallOffset-0.5,0,-0.5));
	    	offsets.put("wall2",new Vector3(-wallOffset-0.5,0,-0.5));
    	}
    	else
    	{
    		offsets.put("wall1",new Vector3(0-0.5,0,wallOffset-0.5));
	    	offsets.put("wall2",new Vector3(0-0.5,0,-wallOffset-0.5));
    	}
	}

	@Override
	public ConcurrentHashMap<String, Matrix3> getBoxes() 
	{
		return boxes;
	}

	@Override
	public void addBox(String name, Matrix3 box) 
	{
		boxes.put(name, box);
	}

	@Override
	public ConcurrentHashMap<String, Vector3> getOffsets()
	{
		return offsets;
	}

	@Override
	public void addOffset(String name, Vector3 offset) 
	{
		offsets.put(name, offset);
	}

	@Override
	public Matrix3 bounds(Vector3 target) {
		return new Matrix3(new Vector3(-size/2,0, -size/2), new Vector3(size/2, 5, size/2));
	}
	
	 /**
     * Called when the entity is attacked.
     */
    public boolean attackEntityFrom(DamageSource source, int damage)
    {
    	if(damage>15)
    	{
    		return true;
    	}
    	
    	return false;
    }

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(2, Integer.valueOf(0));
		this.dataWatcher.addObject(3, Integer.valueOf(0));
		this.dataWatcher.addObject(4, Integer.valueOf(-1));
	}

	@Override
	public ItemStack getHeldItem() {
		return null;
	}

	@Override
	public ItemStack getEquipmentInSlot(int var1) {
		return null;
	}

	@Override
	public void setCurrentItemOrArmor(int var1, ItemStack var2) {
	}

	@Override
	public ItemStack[] getLastActiveItems() {
		return new ItemStack[0];
	}

	/**
	 * @return the destinationFloor
	 */
	public int getDestinationFloor() {
		return dataWatcher.getWatchableObjectInt(2);
	}

	/**
	 * @param destinationFloor the destinationFloor to set
	 */
	public void setDestinationFloor(int destinationFloor) {
		dataWatcher.updateObject(2, Integer.valueOf(destinationFloor));
	}

	/**
	 * @return the destinationFloor
	 */
	public int getCurrentFloor() {
		return dataWatcher.getWatchableObjectInt(3);
	}

	/**
	 * @param currentFloor the destinationFloor to set
	 */
	public void setCurrentFloor(int currentFloor) {
		dataWatcher.updateObject(3, Integer.valueOf(currentFloor));
	}

	/**
	 * @return the destinationFloor
	 */
	public int getDestY() {
		return dataWatcher.getWatchableObjectInt(4);
	}

	/**
	 * @param dest the destinationFloor to set
	 */
	public void setDestY(int dest) {
		dataWatcher.updateObject(4, Integer.valueOf(dest));
	}


}
