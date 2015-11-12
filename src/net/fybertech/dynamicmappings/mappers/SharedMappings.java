package net.fybertech.dynamicmappings.mappers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import net.fybertech.dynamicmappings.DynamicMappings;
import net.fybertech.dynamicmappings.Mapping;
import net.fybertech.dynamicmappings.MethodCallIterator;
import net.fybertech.dynamicmappings.MethodCallIterator.MethodCall;
import net.fybertech.dynamicmappings.MethodCallVisitor;
import net.fybertech.meddle.MeddleUtil;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class SharedMappings extends MappingsBase {

	
	// The starting point
	@Mapping(provides="net/minecraft/server/MinecraftServer")
	public boolean getMinecraftServerClass()
	{
		ClassNode cn = getClassNode("net/minecraft/server/MinecraftServer");
		if (cn == null) return false;
		addClassMapping("net/minecraft/server/MinecraftServer", cn);
		return true;
	}


	@Mapping(provides="net/minecraft/world/World", depends="net/minecraft/server/MinecraftServer")
	public boolean getWorldClass()
	{
		ClassNode server = getClassNode(getClassMapping("net/minecraft/server/MinecraftServer"));
		if (server == null) return false;

		List<String> potentialClasses = new ArrayList<String>();

		// Fetch all obfuscated classes used inside MinecraftServer's interfaces
		for (String interfaceClass : server.interfaces) {
			if (interfaceClass.contains("/")) continue;

			ClassNode node = getClassNode(interfaceClass);
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				Type returnType = Type.getReturnType(method.desc);

				if (returnType.getSort() == Type.OBJECT) {
					if (!returnType.getClassName().contains(".")) potentialClasses.add(returnType.getClassName());
				}
			}
		}

		String[] matchStrings = new String[] { "Getting biome", "chunkCheck", "Level name", "Chunk stats" };

		// Search constant pools of potential classes for strings matching the list above
		for (String className : potentialClasses)
		{
			if (searchConstantPoolForStrings(className, matchStrings))
			{
				ClassNode worldClass = getClassNode(className);
				addClassMapping("net/minecraft/world/World", worldClass);
				return true;
			}
		}

		return false;
	}

	
	@Mapping(provides={
			"net/minecraft/world/WorldType",
			"net/minecraft/world/WorldServer"
			},
			providesMethods={
			"net/minecraft/server/MinecraftServer loadAllWorlds (Ljava/lang/String;Ljava/lang/String;JLnet/minecraft/world/WorldType;Ljava/lang/String;)V"
			},
			 depends={
			"net/minecraft/server/MinecraftServer"
			})
	public boolean getWorldServerClass()
	{
		ClassNode server = getClassNode("net/minecraft/server/MinecraftServer");
		if (server == null) return false;
		
		// protected void loadAllWorlds(String p_71247_1_, String p_71247_2_, long seed, WorldType type, String p_71247_6_)
		List<MethodNode> methods = new ArrayList<>();
		for (MethodNode method : server.methods) {
			if (method.desc.startsWith("(Ljava/lang/String;Ljava/lang/String;JL") && method.desc.endsWith(";Ljava/lang/String;)V")) {
				Type t = Type.getMethodType(method.desc);				
				if (t.getArgumentTypes().length == 5) methods.add(method);
			}
		}

		// net/minecraft/world/WorldType
		// net/minecraft/world/WorldServer
		if (methods.size() == 1) {
			Type t = Type.getMethodType(methods.get(0).desc);
			Type[] args = t.getArgumentTypes();
			String worldType = args[3].getClassName();
			
			if (!searchConstantPoolForStrings(worldType, "default", "flat", "amplified")) return false;
			
			addClassMapping("net/minecraft/world/WorldType", worldType);			
			addMethodMapping("net/minecraft/server/MinecraftServer loadAllWorlds (Ljava/lang/String;Ljava/lang/String;JLnet/minecraft/world/WorldType;Ljava/lang/String;)V",
					server.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<String> worldServerNames = new ArrayList<>();
			
			List<TypeInsnNode> list = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), TypeInsnNode.class);
			for (TypeInsnNode mn : list) {
				if (mn.getOpcode() != Opcodes.NEW) continue;
				if (searchConstantPoolForStrings(mn.desc, "doDaylightCycle", "playerCheckLight", "Saving level") && 
						!worldServerNames.contains(mn.desc)) worldServerNames.add(mn.desc);
			}
			
			if (worldServerNames.size() == 1) {
				addClassMapping("net/minecraft/world/WorldServer", worldServerNames.get(0));
			}
		}
				
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/util/IThreadListener"
			},
			depends={
			"net/minecraft/world/WorldServer"
			})
	public boolean processWorldServerClass()
	{
		ClassNode worldServer = getClassNodeFromMapping("net/minecraft/world/WorldServer");
		if (worldServer == null) return false;
		
		List<String> ifaces = new ArrayList<>();
		for (String iface : worldServer.interfaces) {			
			ClassNode cn = getClassNode(iface);
			for (MethodNode method : cn.methods) {
				if (method.desc.equals("(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;") && 
						!ifaces.contains(iface)) ifaces.add(iface);
			}
		}
		
		if (ifaces.size() == 1) {
			addClassMapping("net/minecraft/util/IThreadListener", ifaces.get(0));
		}		
		
		return true;
	}
	
	
	
	@Mapping(providesMethods={
			"net/minecraft/util/IThreadListener addScheduledTask (Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;",
			"net/minecraft/util/IThreadListener isCallingFromMinecraftThread ()Z"
			},
			depends={
			"net/minecraft/util/IThreadListener"
			})
	public boolean processIThreadListenerClass()
	{
		ClassNode threadListener = getClassNodeFromMapping("net/minecraft/util/IThreadListener");
		if (threadListener == null) return false;
		
		// ListenableFuture addScheduledTask(Runnable runnableToSchedule);
		List<MethodNode> methods = getMatchingMethods(threadListener, null, "(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/IThreadListener addScheduledTask (Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;",
					threadListener.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// boolean isCallingFromMinecraftThread();
		methods = getMatchingMethods(threadListener, null, "()Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/IThreadListener isCallingFromMinecraftThread ()Z",
					threadListener.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}
	

	@Mapping(provides="net/minecraft/init/Blocks", depends="net/minecraft/world/World")
	public boolean getBlocksClass()
	{
		ClassNode worldClass = getClassNodeFromMapping("net/minecraft/world/World");
		if (worldClass == null) return false;

		Set<String> potentialClasses = new HashSet<String>();

		// Discover and filter all static fields accessed in World's methods
		for (MethodNode method : worldClass.methods) {
			for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
				if (node.getOpcode() != Opcodes.GETSTATIC) continue;
				FieldInsnNode fieldNode = (FieldInsnNode)node;

				if (!fieldNode.desc.startsWith("L") || fieldNode.desc.contains("/")) continue;
				String descClass = Type.getType(fieldNode.desc).getClassName();
				if (fieldNode.owner.equals(descClass)) continue;

				potentialClasses.add(fieldNode.owner);
			}
		}

		String[] matchStrings = new String[] { "Accessed Blocks before Bootstrap!", "air", "stone" };

		// Find net.minecraft.init.Blocks from what's left
		for (String className : potentialClasses) {
			if (searchConstantPoolForStrings(className, matchStrings))	{
				ClassNode blocksClass = getClassNode(className);
				addClassMapping("net/minecraft/init/Blocks", blocksClass);
				return true;
			}
		}

		return false;
	}


	@Mapping(provides="net/minecraft/block/Block", depends="net/minecraft/init/Blocks")
	public boolean getBlockClass()
	{
		ClassNode blocksClass = getClassNode(getClassMapping("net/minecraft/init/Blocks"));
		if (blocksClass == null) return false;

		Map<String, Integer> classes = new HashMap<String, Integer>();

		// Make a tally of how many times a class was used in the field descriptors
		for (FieldNode field : blocksClass.fields) {
			String descClass = Type.getType(field.desc).getClassName();
			int val = (classes.containsKey(descClass) ? classes.get(descClass) : 0);
			val++;
			classes.put(descClass,  val);
		}

		String mostClass = null;
		int mostCount = 0;

		// Find the one with the highest count
		for (String key : classes.keySet()) {
			if (classes.get(key) > mostCount) { mostClass = key; mostCount = classes.get(key); }
		}

		// The standard Block class is used far more than any other, so we've found it
		if (mostCount > 100) {
			ClassNode blockClass = getClassNode(mostClass);
			addClassMapping("net/minecraft/block/Block", blockClass);
			return true;
		}

		return false;

	}

	
	
	@Mapping(provides="net/minecraft/block/state/BlockState", depends="net/minecraft/block/Block")
	public boolean getBlockStateClass()
	{
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (block == null) return false;
		
		for (FieldNode fn : block.fields) {			
			Type t = Type.getType(fn.desc);
			if (t.getSort() != Type.OBJECT) continue;
			if (searchConstantPoolForStrings(t.getClassName(), "block", "properties", "Block: ", " has invalidly named property: ")) {
				addClassMapping("net/minecraft/block/state/BlockState", t.getClassName());
				return true;
			}
		}
		
		return false;		
	}
	

	@Mapping(provides="net/minecraft/block/state/IBlockState", depends="net/minecraft/block/Block")
	public boolean getIBlockStateClass()
	{
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (block == null) return false;

		List<MethodNode> methods = getMatchingMethods(block, null, null);
		removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		
		for (MethodNode method : methods) {
			if (!method.desc.startsWith("(I)L")) continue;
			Type t = Type.getMethodType(method.desc).getReturnType();		
			
			ClassNode cn = getClassNode(t.getClassName());
			if (((cn.access & Opcodes.ACC_INTERFACE) > 0) && cn.methods.size() == 6) {
				addClassMapping("net/minecraft/block/state/IBlockState", cn.name);
				return true;
			}
		}
		
		return false;
	}

	
	
	
	
	
	@Mapping(provides={
			"net/minecraft/block/state/IBlockWrapper"
			},
			providesMethods={
			"net/minecraft/block/state/IBlockState getPropertyNames ()Ljava/util/Collection;",
			"net/minecraft/block/state/IBlockState getValue (Lnet/minecraft/block/properties/IProperty;)Ljava/lang/Comparable;",
			"net/minecraft/block/state/IBlockState withProperty (Lnet/minecraft/block/properties/IProperty;Ljava/lang/Comparable;)Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/state/IBlockState cycleProperty (Lnet/minecraft/block/properties/IProperty;)Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/state/IBlockState getProperties ()Lcom/google/common/collect/ImmutableMap;",
			"net/minecraft/block/state/IBlockState getBlock ()Lnet/minecraft/block/Block;"
			},
			depends={
			"net/minecraft/block/Block", 
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/block/properties/IProperty"
			})
	public boolean processIBlockStateClass()
	{
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode iProperty = getClassNodeFromMapping("net/minecraft/block/properties/IProperty");
		if (block == null || iBlockState == null || iProperty == null) return false;

		
		if (iBlockState.interfaces.size() == 1) {
			addClassMapping("net/minecraft/block/state/IBlockWrapper", iBlockState.interfaces.get(0));
		}
		
		
		// Collection getPropertyNames();  
		List<MethodNode> methods = getMatchingMethods(iBlockState, null, "()Ljava/util/Collection;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/IBlockState getPropertyNames ()Ljava/util/Collection;",
					iBlockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
	    // Comparable getValue(IProperty property);  
		methods = getMatchingMethods(iBlockState, null, "(L" + iProperty.name + ";)Ljava/lang/Comparable;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/IBlockState getValue (Lnet/minecraft/block/properties/IProperty;)Ljava/lang/Comparable;",
					iBlockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
	    // IBlockState withProperty(IProperty property, Comparable value);
		methods = getMatchingMethods(iBlockState, null, "(L" + iProperty.name + ";Ljava/lang/Comparable;)L" + iBlockState.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/IBlockState withProperty (Lnet/minecraft/block/properties/IProperty;Ljava/lang/Comparable;)Lnet/minecraft/block/state/IBlockState;",
					iBlockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// IBlockState cycleProperty(IProperty property);
		methods = getMatchingMethods(iBlockState, null, "(L" + iProperty.name + ";)L" + iBlockState.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/IBlockState cycleProperty (Lnet/minecraft/block/properties/IProperty;)Lnet/minecraft/block/state/IBlockState;",
					iBlockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
				
	    // ImmutableMap getProperties();  
		methods = getMatchingMethods(iBlockState, null, "()Lcom/google/common/collect/ImmutableMap;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/IBlockState getProperties ()Lcom/google/common/collect/ImmutableMap;",
					iBlockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// Block getBlock();
		methods = getMatchingMethods(iBlockState, null, "()L" + block.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/IBlockState getBlock ()Lnet/minecraft/block/Block;",
					iBlockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	
	

	@Mapping(provides="net/minecraft/world/IBlockAccess", depends="net/minecraft/world/World")
	public boolean getIBlockAccessClass()
	{
		ClassNode world = getClassNode(getClassMapping("net/minecraft/world/World"));
		if (world == null) return false;

		// We won't know for sure if they added more interfaces
		if (world.interfaces.size() != 1) return false;

		ClassNode classBlockAccess = getClassNode(world.interfaces.get(0));
		addClassMapping("net/minecraft/world/IBlockAccess",  classBlockAccess);
		return true;
	}

	
	
	@Mapping(providesMethods={
			"net/minecraft/world/IBlockAccess getTileEntity (Lnet/minecraft/util/BlockPos;)Lnet/minecraft/tileentity/TileEntity;",
			"net/minecraft/world/IBlockAccess getBlockState (Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/world/IBlockAccess isAirBlock (Lnet/minecraft/util/BlockPos;)Z",
			"net/minecraft/world/IBlockAccess getStrongPower (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)I"
			},
			depends={
			"net/minecraft/world/IBlockAccess",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/tileentity/TileEntity",
			"net/minecraft/util/BlockPos",
			"net/minecraft/util/EnumFacing"
			})
	public boolean processIBlockAccessClass()
	{
		ClassNode iBlockAccess = getClassNodeFromMapping("net/minecraft/world/IBlockAccess");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode tileEntity = getClassNodeFromMapping("net/minecraft/tileentity/TileEntity");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		if (!MeddleUtil.notNull(iBlockAccess, iBlockState, tileEntity, blockPos, enumFacing)) return false;
		
		// TileEntity getTileEntity(BlockPos pos)
		List<MethodNode> methods = getMatchingMethods(iBlockAccess, null, assembleDescriptor("(", blockPos, ")", tileEntity));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/IBlockAccess getTileEntity (Lnet/minecraft/util/BlockPos;)Lnet/minecraft/tileentity/TileEntity;",
					iBlockAccess.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		// IBlockState getBlockState(BlockPos pos)
		methods = getMatchingMethods(iBlockAccess, null, assembleDescriptor("(", blockPos, ")", iBlockState));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/IBlockAccess getBlockState (Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;",
					iBlockAccess.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}	
		
		// boolean isAirBlock(BlockPos pos);
		methods = getMatchingMethods(iBlockAccess, null, assembleDescriptor("(", blockPos, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/IBlockAccess isAirBlock (Lnet/minecraft/util/BlockPos;)Z",
					iBlockAccess.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// int getStrongPower(BlockPos pos, EnumFacing direction)
		methods = getMatchingMethods(iBlockAccess, null, assembleDescriptor("(", blockPos, enumFacing, ")I"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/IBlockAccess getStrongPower (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)I",
					iBlockAccess.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
				
		
	    // @ClientOnly
	    // int getCombinedLight(BlockPos pos, int lightValue)

	    // @ClientOnly
	    // BiomeGenBase getBiomeGenForCoords(BlockPos pos);

	    // @ClientOnly
	    // boolean extendedLevelsInChunkCache();    

	    // @ClientOnly
	    // WorldType getWorldType();
		
		return true;
	}
	
	

	@Mapping(provides="net/minecraft/util/BlockPos", depends="net/minecraft/block/Block")
	public boolean getBlockPosClass()
	{
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (block == null) return false;

		Map<String, Integer> counts = new HashMap<String, Integer>();

		// Count the times a class is used as the second parameter of a method
		for (MethodNode method : block.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length < 2) continue;

			String name = args[1].getClassName();
			int count = (counts.containsKey(name) ? counts.get(name) : 0);
			count++;
			counts.put(name,  count);
		}

		int max = 0;
		String maxClass = null;

		// Find the one with the most
		for (String key : counts.keySet()) {
			int count = counts.get(key);
			if (count > max) { max = count; maxClass = key; }
		}

		// It should be over 30
		if (max < 10) return false;

		ClassNode blockPos = getClassNode(maxClass);
		addClassMapping("net/minecraft/util/BlockPos", blockPos);

		return true;
	}

	
	
	@Mapping(provides={
			"net/minecraft/util/Vec3i"
			},
			providesMethods={
			"net/minecraft/util/BlockPos down (I)Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos up (I)Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos north (I)Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos south (I)Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos west (I)Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos east (I)Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos down ()Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos up ()Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos north ()Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos south ()Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos west ()Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos east ()Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos offset (Lnet/minecraft/util/EnumFacing;)Lnet/minecraft/util/BlockPos;"
			},
			dependsFields={
			"net/minecraft/util/EnumFacing DOWN Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing UP Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing NORTH Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing SOUTH Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing WEST Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing EAST Lnet/minecraft/util/EnumFacing;"
			},
			depends={
			"net/minecraft/util/BlockPos",
			"net/minecraft/util/EnumFacing"
			})
	public boolean processBlockPosClass()
	{
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		if (blockPos == null || enumFacing == null) return false;		
		
		if (searchConstantPoolForStrings(blockPos.superName, "x", "y", "z")) {
			addClassMapping("net/minecraft/util/Vec3i", blockPos.superName);
		}
		
		FieldNode[] facings = new FieldNode[6];
		facings[0] = getFieldNodeFromMapping(enumFacing, "net/minecraft/util/EnumFacing DOWN Lnet/minecraft/util/EnumFacing;");
		facings[1] = getFieldNodeFromMapping(enumFacing, "net/minecraft/util/EnumFacing UP Lnet/minecraft/util/EnumFacing;");
		facings[2] = getFieldNodeFromMapping(enumFacing, "net/minecraft/util/EnumFacing NORTH Lnet/minecraft/util/EnumFacing;");
		facings[3] = getFieldNodeFromMapping(enumFacing, "net/minecraft/util/EnumFacing SOUTH Lnet/minecraft/util/EnumFacing;");
		facings[4] = getFieldNodeFromMapping(enumFacing, "net/minecraft/util/EnumFacing WEST Lnet/minecraft/util/EnumFacing;");
		facings[5] = getFieldNodeFromMapping(enumFacing, "net/minecraft/util/EnumFacing EAST Lnet/minecraft/util/EnumFacing;");
		for (int n = 0; n < 6; n++) { if (facings[n] == null) return false; }
		
		String[] dirs = new String[] { "down", "up", "north", "south", "west", "east" };
		
		MethodNode[] offsetMethods = new MethodNode[6];
		
		List<MethodNode> methods = getMatchingMethods(blockPos, null, "(I)L" + blockPos.name + ";");
		if (methods.size() == 6) 
		{		
			for (int methodNum = 0; methodNum < 6; methodNum++) {
				AbstractInsnNode insn = findNextOpcodeNum(methods.get(methodNum).instructions.getFirst(), Opcodes.GETSTATIC);
				if (insn == null) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (!fn.owner.equals(enumFacing.name) || !fn.desc.equals("L" + enumFacing.name + ";")) continue;
				for (int dirNum = 0; dirNum < 6; dirNum++) {
					if (facings[dirNum].name.equals(fn.name)) {
						addMethodMapping("net/minecraft/util/BlockPos " + dirs[dirNum] + " (I)Lnet/minecraft/util/BlockPos;",
								blockPos.name + " " + methods.get(methodNum).name + " " + methods.get(methodNum).desc);						
						offsetMethods[dirNum] = methods.get(methodNum);
					}
				}
			}
		}
		
		
		// Went from 6 to 7 matching methods in 15w39c
		methods = getMatchingMethods(blockPos, null, "()L" + blockPos.name + ";");
		if (methods.size() >= 6) {
			for (MethodNode method : methods) {
				AbstractInsnNode insn = findNextOpcodeNum(method.instructions.getFirst(), Opcodes.INVOKEVIRTUAL);
				if (insn == null) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (!mn.owner.equals(blockPos.name) || !mn.desc.equals("(I)L" + blockPos.name + ";")) continue;
				for (int dirNum = 0; dirNum < 6; dirNum++) {
					if (offsetMethods[dirNum].name.equals(mn.name)) {
						addMethodMapping("net/minecraft/util/BlockPos " + dirs[dirNum] + " ()Lnet/minecraft/util/BlockPos;",
								blockPos.name + " " + method.name + " " + method.desc);
					}
				}
			}
		}
		
		
		// public BlockPos offset(EnumFacing facing)
		methods = getMatchingMethods(blockPos, null, "(L" + enumFacing.name + ";)L" + blockPos.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/BlockPos offset (Lnet/minecraft/util/EnumFacing;)Lnet/minecraft/util/BlockPos;",
					blockPos.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}
	
	
	@Mapping(providesFields={
			"net/minecraft/util/Vec3i x I",
			"net/minecraft/util/Vec3i y I",
			"net/minecraft/util/Vec3i z I"
			},
			providesMethods={
			"net/minecraft/util/Vec3i getX ()I",
			"net/minecraft/util/Vec3i getY ()I",
			"net/minecraft/util/Vec3i getZ ()I"
			},
			depends={
			"net/minecraft/util/Vec3i"
			})
	public boolean processVec3iClass()
	{
		ClassNode vec3i = getClassNodeFromMapping("net/minecraft/util/Vec3i");
		if (vec3i == null) return false;		
		
		String x = null;
		String y = null;
		String z = null;
		
		List<FieldNode> fields = getMatchingFields(vec3i, null, "I");
		if (fields.size() == 3) {
			x = fields.get(0).name;
			y = fields.get(1).name;
			z = fields.get(2).name;
			addFieldMapping("net/minecraft/util/Vec3i x I", vec3i.name + " " + x + " I");
			addFieldMapping("net/minecraft/util/Vec3i y I", vec3i.name + " " + y + " I");
			addFieldMapping("net/minecraft/util/Vec3i z I", vec3i.name + " " + z + " I");			
		}
		
		
		// public int getX()
		// public int getY()
		// public int getZ()
		List<MethodNode> methods = getMatchingMethods(vec3i, null, "()I");
		for (MethodNode method : methods) {
			if (matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.IRETURN)) {
				FieldInsnNode fn = (FieldInsnNode)findNextOpcodeNum(method.instructions.getFirst(), Opcodes.GETFIELD);
				if (!fn.owner.equals(vec3i.name) || !fn.desc.equals("I")) continue;
				
				if (fn.name.equals(x)) addMethodMapping("net/minecraft/util/Vec3i getX ()I", vec3i.name + " " + method.name + " ()I");
				else if (fn.name.equals(y)) addMethodMapping("net/minecraft/util/Vec3i getY ()I", vec3i.name + " " + method.name + " ()I");
				else if (fn.name.equals(z)) addMethodMapping("net/minecraft/util/Vec3i getZ ()I", vec3i.name + " " + method.name + " ()I");
			}
		}
		
		
		return true;
	}
	
	
	@Mapping(providesFields={
			"net/minecraft/util/Vec3 xCoord D",
			"net/minecraft/util/Vec3 yCoord D",
			"net/minecraft/util/Vec3 zCoord D"
			},
			providesMethods={
			},
			depends={
			"net/minecraft/util/Vec3"
			})
	public boolean processVec3Class()
	{
		ClassNode vec3 = getClassNodeFromMapping("net/minecraft/util/Vec3");
		if (!MeddleUtil.notNull(vec3)) return false;
		
		List<FieldNode> fields = getMatchingFields(vec3, null, "D");
		if (fields.size() == 3) {
			addFieldMapping("net/minecraft/util/Vec3 xCoord D", vec3.name + " " + fields.get(0).name + " D");
			addFieldMapping("net/minecraft/util/Vec3 yCoord D", vec3.name + " " + fields.get(1).name + " D");
			addFieldMapping("net/minecraft/util/Vec3 zCoord D", vec3.name + " " + fields.get(2).name + " D");
		}
		
		return true;
	}
	
	

	@Mapping(provides={
			"net/minecraft/entity/item/EntityItem",
			"net/minecraft/item/ItemStack"},
			providesMethods={
			"net/minecraft/block/Block spawnAsEntity (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/item/ItemStack;)V"
			},
			depends={
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/Block"})
	public boolean getEntityItemClass()
	{
		ClassNode worldClass = getClassNode(getClassMapping("net/minecraft/world/World"));
		ClassNode blockPosClass = getClassNode(getClassMapping("net/minecraft/util/BlockPos"));
		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (!MeddleUtil.notNull(worldClass, blockPosClass, blockClass)) return false;

		// search for Block.spawnAsEntity(World worldIn, BlockPos pos, ItemStack stack)
		for (MethodNode method : blockClass.methods)
		{
			// We're looking for a static method
			if ((method.access & Opcodes.ACC_STATIC) != Opcodes.ACC_STATIC) continue;

			// Look for methods with three arguments
			Type methodType = Type.getMethodType(method.desc);
			Type[] arguments = methodType.getArgumentTypes();
			if (arguments.length != 3) continue;

			// Make sure all arguments are objects
			if (arguments[0].getSort() != Type.OBJECT || arguments[1].getSort() != Type.OBJECT || arguments[2].getSort() != Type.OBJECT ) continue;
			// Make sure arg0 is World and arg1 is BlockPos
			if (!arguments[0].getClassName().equals(worldClass.name) || !arguments[1].getClassName().equals(blockPosClass.name)) continue;

			boolean foundString = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				if (!foundString && isLdcWithString(insn, "doTileDrops")) { foundString = true; continue; }
				if (foundString && insn.getOpcode() == Opcodes.NEW)
				{
					TypeInsnNode newNode = (TypeInsnNode)insn;
					addClassMapping("net/minecraft/entity/item/EntityItem", getClassNode(newNode.desc));

					// This also means that the last arg for spawnAsEntity is ItemStack
					addClassMapping("net/minecraft/item/ItemStack", getClassNode(arguments[2].getClassName()));

					addMethodMapping("net/minecraft/block/Block spawnAsEntity (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/item/ItemStack;)V",
							blockClass.name + " " + method.name + " " + method.desc);

					return true;
				}
			}

		}

		return false;
	}


	@Mapping(provides={
			"net/minecraft/block/BlockFire",
			"net/minecraft/block/BlockLeaves",
			"net/minecraft/block/BlockChest",
			"net/minecraft/block/BlockSlab",
			"net/minecraft/block/BlockHopper",
			"net/minecraft/block/BlockGrass"
			},
			providesFields={
			"net/minecraft/init/Blocks air Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks stone Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks grass Lnet/minecraft/block/BlockGrass;",
			"net/minecraft/init/Blocks dirt Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks cobblestone Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks planks Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks sapling Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks bedrock Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks iron_block Lnet/minecraft/block/Block;",
			
			"net/minecraft/init/Blocks chest Lnet/minecraft/block/BlockChest;",			
			"net/minecraft/init/Blocks obsidian Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks hopper Lnet/minecraft/block/BlockHopper;",
			"net/minecraft/init/Blocks glass_pane Lnet/minecraft/block/Block;"			
			},
			depends={
			"net/minecraft/init/Blocks",
			"net/minecraft/block/Block"})
	public boolean processBlocksClass()
	{
		Map<String, String> blocksClassFields = new HashMap<String, String>();
		Map<String, FieldInsnNode> blocksFields = new HashMap<String, FieldInsnNode>();

		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode blocksClass = getClassNode(getClassMapping("net/minecraft/init/Blocks"));
		if (blockClass == null || blocksClass == null) return false;

		// Generate list
		for (MethodNode method : blocksClass.methods)
		{
			if (!method.name.equals("<clinit>")) continue;

			String lastString = null;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				String s = getLdcString(insn);
				if (s != null) lastString = s;
				// Avoid any strings that definitely aren't block names
				if (lastString == null || lastString.contains(" ")) continue;

				if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
				FieldInsnNode fieldNode = (FieldInsnNode)insn;
				// Filter out non-objects and packaged classes, just in case
				if (!fieldNode.desc.startsWith("L") || fieldNode.desc.contains("/")) continue;
				
				blocksFields.put(lastString, fieldNode);
				
				// Filter out generic ones extending just the block class
				if (fieldNode.desc.equals("L" + blockClass.name + ";")) continue;
				blocksClassFields.put(lastString, fieldNode.desc.substring(1, fieldNode.desc.length() - 1));
			}
		}

		String className;
		

		/*
			grass, flowing_water, water, flowing_lava, lava, sand, leaves2, sticky_piston, tallgrass,
			deadbush, piston, piston_head, piston_extension, yellow_flower, red_flower, brown_mushroom,
			red_mushroom, double_stone_slab, stone_slab, redstone_wire, cactus, reeds,
			portal, unpowered_repeater, powered_repeater, mycelium, cauldron, double_wooden_slab, wooden_slab,
			tripwire_hook, beacon, skull, unpowered_comparator, powered_comparator, daylight_detector,
			daylight_detector_inverted, hopper, double_plant, stained_glass, stained_glass_pane,
			double_stone_slab2, stone_slab2, purpur_double_slab, purpur_slab
		 */

		className = blocksClassFields.get("fire");
		if (className != null && searchConstantPoolForStrings(className, "doFireTick")) {
			addClassMapping("net/minecraft/block/BlockFire", getClassNode(className));
		}

		className = blocksClassFields.get("leaves");
		if (className != null && searchConstantPoolForStrings(className, "decayable")) {
			addClassMapping("net/minecraft/block/BlockLeaves", getClassNode(className));
		}

		className = blocksClassFields.get("chest");
		if (className != null && searchConstantPoolForStrings(className, "container.chestDouble")) {
			addClassMapping("net/minecraft/block/BlockChest", getClassNode(className));
		}
		
		className = blocksClassFields.get("stone_slab");
		if (className != null && searchConstantPoolForStrings(className, "half")) {
			addClassMapping("net/minecraft/block/BlockSlab", className);
		}
		
		className = blocksClassFields.get("hopper");
		if (className != null && searchConstantPoolForStrings(className, "facing", "enabled")) {			
			addClassMapping("net/minecraft/block/BlockHopper", className);
		}
		
		className = blocksClassFields.get("grass");
		if (className != null && searchConstantPoolForStrings(className, "snowy")) {			
			addClassMapping("net/minecraft/block/BlockGrass", className);
		}	
		
			
		FieldInsnNode field;
		
		field = blocksFields.get("air");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks air Lnet/minecraft/block/Block;", field);
		
		field = blocksFields.get("stone");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks stone Lnet/minecraft/block/Block;", field);
		
		field = blocksFields.get("grass");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks grass Lnet/minecraft/block/BlockGrass;", field);
		
		field = blocksFields.get("dirt");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks dirt Lnet/minecraft/block/Block;", field);
		
		field = blocksFields.get("cobblestone");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks cobblestone Lnet/minecraft/block/Block;", field);
		
		field = blocksFields.get("planks");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks planks Lnet/minecraft/block/Block;", field);
		
		field = blocksFields.get("sapling");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks sapling Lnet/minecraft/block/Block;", field);
		
		field = blocksFields.get("bedrock");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks bedrock Lnet/minecraft/block/Block;", field);
		
		field = blocksFields.get("chest");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks chest Lnet/minecraft/block/BlockChest;", field);
			
		field = blocksFields.get("glass_pane");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks glass_pane Lnet/minecraft/block/Block;", field);					
		
		
		field = blocksFields.get("iron_block");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks iron_block Lnet/minecraft/block/Block;", field);
		
		field = blocksFields.get("obsidian");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks obsidian Lnet/minecraft/block/Block;", field);		
				
		field = blocksFields.get("hopper");
		if (field != null) addFieldMapping("net/minecraft/init/Blocks hopper Lnet/minecraft/block/BlockHopper;", field);
		
		
		
		return true;
	}
	
	
	
	@Mapping(providesFields={
			"net/minecraft/init/Sounds entity_creeper_primed Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds entity_hostile_hurt Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds entity_hostile_swim Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds block_water_ambient Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds block_lava_pop Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds entity_generic_explode Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds ui_button_click Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds block_portal_trigger Lnet/minecraft/util/Sound;"
			},
			depends={
			"net/minecraft/util/Sound",
			"net/minecraft/init/Sounds"
			})
	public boolean processSoundsClass()
	{
		ClassNode sound = getClassNodeFromMapping("net/minecraft/util/Sound");
		ClassNode sounds = getClassNodeFromMapping("net/minecraft/init/Sounds");
		if (sound == null || sounds == null) return false;
		
		Map<String, FieldInsnNode> soundFields = new HashMap<>();
		
		// Generate list
		for (MethodNode method : sounds.methods)
		{
			if (!method.name.equals("<clinit>")) continue;

			String lastString = null;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				String s = getLdcString(insn);
				if (s != null) lastString = s;
				// Avoid any strings that definitely aren't block names
				if (lastString == null || lastString.contains(" ")) continue;

				if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
				FieldInsnNode fieldNode = (FieldInsnNode)insn;
				
				// We only want sound objects
				if (!fieldNode.desc.equals("L" + sound.name + ";")) continue;				
				soundFields.put(lastString, fieldNode);
				
				String newName = lastString.replace(".", "_");
				addFieldMapping("net/minecraft/init/Sounds " + newName + " Lnet/minecraft/util/Sound;", fieldNode);
			}
		}
		
		
		
		return true;
	}
	
	
	
	@Mapping(providesFields={
			"net/minecraft/block/BlockHopper FACING Lnet/minecraft/block/properties/PropertyDirection;",
			"net/minecraft/block/BlockHopper ENABLED Lnet/minecraft/block/properties/PropertyBool;",
			"net/minecraft/block/material/Material iron Lnet/minecraft/block/material/Material;"
			},
			providesMethods={
			"net/minecraft/block/properties/PropertyDirection create (Ljava/lang/String;Lcom/google/common/base/Predicate;)Lnet/minecraft/block/properties/PropertyDirection;",
			"net/minecraft/item/ItemStack hasDisplayName ()Z",
			"net/minecraft/block/Block hasComparatorInputOverride (Lnet/minecraft/block/state/IBlockState;)Z",
			"net/minecraft/util/EnumFacing getOpposite ()Lnet/minecraft/util/EnumFacing;"
			},
			dependsMethods={
			"net/minecraft/block/Block onBlockPlacedBy (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V",
			"net/minecraft/block/Block isFullCube (Lnet/minecraft/block/state/IBlockState;)Z",
			"net/minecraft/block/Block isOpaqueCube (Lnet/minecraft/block/state/IBlockState;)Z",
			"net/minecraft/block/Block onBlockPlaced (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;FFFILnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/block/state/IBlockState;",
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/block/BlockHopper",
			"net/minecraft/block/properties/PropertyDirection",
			"net/minecraft/block/properties/PropertyBool",
			"net/minecraft/block/material/Material",
			"net/minecraft/item/ItemStack",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/util/EnumFacing"
			})
	public boolean processBlockHopperClass()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode blockHopper = getClassNodeFromMapping("net/minecraft/block/BlockHopper");
		ClassNode propertyDirection = getClassNodeFromMapping("net/minecraft/block/properties/PropertyDirection");
		ClassNode propertyBool = getClassNodeFromMapping("net/minecraft/block/properties/PropertyBool");
		ClassNode material = getClassNodeFromMapping("net/minecraft/block/material/Material");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		if (!MeddleUtil.notNull(block, blockHopper, propertyDirection, propertyBool, material, itemStack, iBlockState, enumFacing)) return false;

		
		// Parse fields to find:
		//   PropertyDirection FACING, 
		//   PropertyBool ENABLED
		List<FieldNode> fields = getMatchingFields(blockHopper, null, "L" + propertyDirection.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/block/BlockHopper FACING Lnet/minecraft/block/properties/PropertyDirection;",
					blockHopper.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}		
		fields = getMatchingFields(blockHopper, null, "L" + propertyBool.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/block/BlockHopper ENABLED Lnet/minecraft/block/properties/PropertyBool;",
					blockHopper.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		// Parse class init to find:
		//   public static PropertyDirection PropertyDirection.create(String, Predicate<EnumFacing>)
		List<MethodNode> methods = getMatchingMethods(blockHopper, "<clinit>", "()V");
		if (methods.size() == 1) {
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			nodes = filterMethodInsnNodes(nodes, propertyDirection.name, "(Ljava/lang/String;Lcom/google/common/base/Predicate;)L" + propertyDirection.name + ";");			
			if (nodes.size() == 1) {
				addMethodMapping("net/minecraft/block/properties/PropertyDirection create (Ljava/lang/String;Lcom/google/common/base/Predicate;)Lnet/minecraft/block/properties/PropertyDirection;",
						propertyDirection.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// Parse class init to find:
		//   Material.iron
		methods = getMatchingMethods(blockHopper, "<init>", "()V");
		if (methods.size() == 1) {
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			// Get just the materials
			List<FieldInsnNode> materials = filterFieldInsnNodes(nodes, material.name, "L" + material.name + ";");
			// Should only be one
			if (materials.size() == 1) {
				addFieldMapping("net/minecraft/block/material/Material iron Lnet/minecraft/block/material/Material;",
						material.name + " " + materials.get(0).name + " " + materials.get(0).desc);
			}
		}
		
		
		// Parse onBlockPlacedBy to find: 
		//   public boolean ItemStack.hasDisplayName()
		MethodNode onBlockPlacedBy = getMethodNodeFromMapping(blockHopper, "net/minecraft/block/Block onBlockPlacedBy (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V");
		if (onBlockPlacedBy != null) {
			// Filter just the boolean-returning methods from ItemStack used in onBlockPlacedBy
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(onBlockPlacedBy, MethodInsnNode.class);
			List<MethodInsnNode> hasDisplayNameList = filterMethodInsnNodes(nodes, itemStack.name, "()Z");
			// Should only be one
			if (hasDisplayNameList.size() == 1) {
				// Get the actual method from ItemStack
				MethodNode hasDisplayName = getMethodNode(itemStack, "--- " + hasDisplayNameList.get(0).name + " " + hasDisplayNameList.get(0).desc);
				if (hasDisplayName != null) {
					// Double check that the method has a "Name" string used in it, then add mapping
					for (AbstractInsnNode insn = hasDisplayName.instructions.getFirst(); insn != null; insn = insn.getNext()) {
						if (isLdcWithString(insn, "Name")) {
							addMethodMapping("net/minecraft/item/ItemStack hasDisplayName ()Z",
									itemStack.name + " " + hasDisplayName.name + " " + hasDisplayName.desc);
							break;
						}
					}					
				}
			}
			
		}
		
		MethodNode isFullCube = getMethodNodeFromMapping(blockHopper, "net/minecraft/block/Block isFullCube (Lnet/minecraft/block/state/IBlockState;)Z");
		MethodNode isOpaqueCube = getMethodNodeFromMapping(blockHopper, "net/minecraft/block/Block isOpaqueCube (Lnet/minecraft/block/state/IBlockState;)Z");
		
		methods = getMatchingMethods(blockHopper, null, "(L" + iBlockState.name + ";)Z");
		if (methods.size() > 0 && isFullCube != null && isOpaqueCube != null) {
			for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
				MethodNode mn = it.next();
				if (mn.name.equals(isFullCube.name)) it.remove();
				if (mn.name.equals(isOpaqueCube.name)) it.remove();
			}
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/block/Block hasComparatorInputOverride (Lnet/minecraft/block/state/IBlockState;)Z",
						block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		
		// Parse onBlockPlaced to find: 
		//   public EnumFacing EnumFacing.getOpposite()
		MethodNode onBlockPlaced = getMethodNodeFromMapping(blockHopper, "net/minecraft/block/Block onBlockPlaced (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;FFFILnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/block/state/IBlockState;");
		if (onBlockPlaced != null) {
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(onBlockPlaced, MethodInsnNode.class);
			List<MethodInsnNode> getOpposite = filterMethodInsnNodes(nodes, enumFacing.name, "()L" + enumFacing.name + ";");
			if (getOpposite.size() == 1) {
				addMethodMapping("net/minecraft/util/EnumFacing getOpposite ()Lnet/minecraft/util/EnumFacing;",
						enumFacing.name + " " + getOpposite.get(0).name + " " + getOpposite.get(0).desc);
			}
			
		}
		
		return true;
	}
	
	

	@Mapping(provides="net/minecraft/block/BlockLeavesBase",
			providesMethods={
			"net/minecraft/block/Block setTickRandomly (Z)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block isVisuallyOpaque ()Z"
			},
			depends={
			"net/minecraft/block/Block", 
			"net/minecraft/block/BlockLeaves"
			})
	public boolean processBlockLeavesClass()
	{
		ClassNode blockLeaves = getClassNode(getClassMapping("net/minecraft/block/BlockLeaves"));
		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (blockClass == null || blockLeaves == null) return false;

		// BlockLeaves extends BlockLeavesBase, which extends Block
		ClassNode blockLeavesBase = getClassNode(blockLeaves.superName);
		if (blockLeavesBase != null && blockLeavesBase.superName != null) {
			if (blockClass.name.equals(blockLeavesBase.superName))
				addClassMapping("net/minecraft/block/BlockLeavesBase", blockLeavesBase);
		}
		

		// protected Block setTickRandomly(boolean)
		MethodNode init = getMethodNode(blockLeaves, "--- <init> ()V");
		if (init != null) {
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(init, MethodInsnNode.class);
			for (Iterator<MethodInsnNode> it = nodes.iterator(); it.hasNext();) {
				MethodInsnNode mn = it.next();
				if (mn.owner.equals(blockLeaves.name) && mn.desc.equals("(Z)L" + blockClass.name + ";")) continue;
				it.remove();
			}
			if (nodes.size() == 1) {
				addMethodMapping("net/minecraft/block/Block setTickRandomly (Z)Lnet/minecraft/block/Block;",
						blockClass.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public boolean isVisuallyOpaque()
		List<MethodNode> methods = getMatchingMethods(blockLeaves, null, "()Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block isVisuallyOpaque ()Z", blockClass.name + " " + methods.get(0).name + " ()Z");
		}
		
		
		return true;
	}


	@Mapping(providesFields={
			"net/minecraft/block/Block fullBlock Z"
			},
			providesMethods={
			"net/minecraft/block/Block canSilkHarvest ()Z",
			"net/minecraft/block/Block getBlockBoundsBasedOnState (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;",
			"net/minecraft/block/Block isFullCube (Lnet/minecraft/block/state/IBlockState;)Z"
			},
			dependsMethods={
			"net/minecraft/block/Block isOpaqueCube (Lnet/minecraft/block/state/IBlockState;)Z"	
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/block/BlockSlab",
			"net/minecraft/block/material/Material",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/world/IBlockAccess",
			"net/minecraft/util/BlockPos",
			"net/minecraft/util/AxisAlignedBB"
			})
	public boolean processBlockSlabClass() 
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode blockSlab = getClassNodeFromMapping("net/minecraft/block/BlockSlab");
		ClassNode material = getClassNodeFromMapping("net/minecraft/block/material/Material");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode iBlockAccess = getClassNodeFromMapping("net/minecraft/world/IBlockAccess");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode aabb = getClassNodeFromMapping("net/minecraft/util/AxisAlignedBB");
		if (!MeddleUtil.notNull(block, blockSlab, material, iBlockState, iBlockAccess, blockPos, aabb)) return false;
		
		
		MethodNode init = getMethodNode(blockSlab, "--- <init> (L" + material.name + ";)V");
		if (init != null) {
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(init, FieldInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(blockSlab.name) && nodes.get(0).desc.equals("Z")) {
				addFieldMapping("net/minecraft/block/Block fullBlock Z", block.name + " " + nodes.get(0).name + " Z");
			}
		}
		
		
		// protected boolean canSilkHarvest()
		List<MethodNode> methods = getMatchingMethods(blockSlab, null, "()Z");
		methods = removeMethodsWithFlags(methods, Opcodes.ACC_ABSTRACT);
		if (methods.size() == 1) {
			MethodNode canSilkHarvest = getMethodNode(block, "--- " + methods.get(0).name + " " + methods.get(0).desc);
			if (canSilkHarvest != null) {
				addMethodMapping("net/minecraft/block/Block canSilkHarvest ()Z", block.name + " " + canSilkHarvest.name + " ()Z");
			}
		}
		
		
		// public AxisAlignedBB getBlockBoundsBasedOnState(IBlockState state, IBlockAccess worldIn, BlockPos pos)
		methods = getMatchingMethods(blockSlab, null, assembleDescriptor("(", iBlockState, iBlockAccess, blockPos, ")", aabb));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getBlockBoundsBasedOnState (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean isFullCube(IBlockState state)
		MethodNode isOpaqueCube = getMethodNodeFromMapping(block, "net/minecraft/block/Block isOpaqueCube (Lnet/minecraft/block/state/IBlockState;)Z");
		methods = getMatchingMethods(blockSlab, null, "(L" + iBlockState.name + ";)Z");
		methods = removeMethodsWithFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 2 && isOpaqueCube != null) {
			for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
				MethodNode method = it.next();
				if (method.name.equals(isOpaqueCube.name) && method.desc.equals(isOpaqueCube.desc)) it.remove();
			}
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/block/Block isFullCube (Lnet/minecraft/block/state/IBlockState;)Z",
						block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		
		return true;		
	}
	
	
	@Mapping(provides="net/minecraft/item/Item",
			depends={
			"net/minecraft/block/Block", 
			"net/minecraft/item/ItemStack"
			})
	public boolean getItemClass()
	{
		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode itemStackClass = getClassNode(getClassMapping("net/minecraft/item/ItemStack"));
		if (blockClass == null || itemStackClass == null) return false;

		List<String> possibleClasses = new ArrayList<String>();

		for (MethodNode method : itemStackClass.methods)
		{
			if (!method.name.equals("<init>")) continue;
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length != 1 || args[0].getSort() != Type.OBJECT) continue;

			// Get the class of the first method parameter
			String className = args[0].getClassName();
			// One of them will be the block class, ignore it
			if (className.equals(blockClass.name)) continue;
			possibleClasses.add(className);
		}

		if (possibleClasses.size() == 1) {
			String className = possibleClasses.get(0);
			if (searchConstantPoolForStrings(className, "item.", "arrow")) {
				addClassMapping("net/minecraft/item/Item", getClassNode(possibleClasses.get(0)));
				return true;
			}
		}

		return false;
	}



	@Mapping(provides={
			"net/minecraft/block/BlockContainer", 
			"net/minecraft/block/ITileEntityProvider"
			},
			depends={
			"net/minecraft/block/Block", 
			"net/minecraft/block/BlockChest"
			})
	public boolean getBlockContainerClass()
	{
		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode blockChestClass = getClassNode(getClassMapping("net/minecraft/block/BlockChest"));
		if (blockClass == null || blockChestClass == null || blockChestClass.superName == null) return false;

		ClassNode containerClass = getClassNode(blockChestClass.superName);
		if (!containerClass.superName.equals(blockClass.name)) return false;

		if (containerClass.interfaces.size() != 1) return false;

		addClassMapping("net/minecraft/block/BlockContainer", containerClass);
		addClassMapping("net/minecraft/block/ITileEntityProvider", getClassNode(containerClass.interfaces.get(0)));

		return true;
	}


	@Mapping(provides="net/minecraft/tileentity/TileEntity", 
			providesMethods="net/minecraft/block/ITileEntityProvider createNewTileEntity (Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;",
			 depends={
			"net/minecraft/block/ITileEntityProvider",
			"net/minecraft/world/World"
			})
	public boolean getTileEntityClass()
	{
		ClassNode teProviderClass = getClassNode(getClassMapping("net/minecraft/block/ITileEntityProvider"));
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		if (teProviderClass == null || world == null) return false;

		String tileEntity_name = null;
		
		for (MethodNode method : teProviderClass.methods)
		{
			Type t = Type.getMethodType(method.desc);
			Type returnType = t.getReturnType();
			if (returnType.getSort() != Type.OBJECT) return false;

			String className = returnType.getClassName();
			if (tileEntity_name == null && searchConstantPoolForStrings(className, "Furnace", "MobSpawner")) {
				addClassMapping("net/minecraft/tileentity/TileEntity", getClassNode(className));
				tileEntity_name = className;
			}
			
			// TileEntity createNewTileEntity(World worldIn, int meta);
			if (tileEntity_name != null && method.desc.equals("(L" + world.name + ";I)L" + tileEntity_name + ";")) {
				addMethodMapping("net/minecraft/block/ITileEntityProvider createNewTileEntity (Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;",
						teProviderClass.name + " " + method.name + " " + method.desc);
				return true;
			}
			
		}

		return false;
	}

	
	@Mapping(provides={
			"net/minecraft/tileentity/TileEntityChest",
			"net/minecraft/network/PacketBuffer",
			"net/minecraft/network/Packet",
			"net/minecraft/crash/CrashReportCategory"
			},
			providesFields={
			"net/minecraft/tileentity/TileEntity worldObj Lnet/minecraft/world/World;",
			"net/minecraft/tileentity/TileEntity pos Lnet/minecraft/util/BlockPos;",
			"net/minecraft/tileentity/TileEntity tileEntityInvalid Z",
			"net/minecraft/tileentity/TileEntity blockMetadata I",
			"net/minecraft/tileentity/TileEntity blockType Lnet/minecraft/block/Block;"
			},
			providesMethods={
			"net/minecraft/tileentity/TileEntity addMapping (Ljava/lang/Class;Ljava/lang/String;)V",
			"net/minecraft/tileentity/TileEntity getWorld ()Lnet/minecraft/world/World;",
			"net/minecraft/tileentity/TileEntity setWorldObj (Lnet/minecraft/world/World;)V",
			"net/minecraft/tileentity/TileEntity createAndLoadEntity (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/tileentity/TileEntity;",
			"net/minecraft/tileentity/TileEntity getBlockMetadata ()I",
			"net/minecraft/tileentity/TileEntity getPos ()Lnet/minecraft/util/BlockPos;",
			"net/minecraft/tileentity/TileEntity setPos (Lnet/minecraft/util/BlockPos;)V",
			"net/minecraft/tileentity/TileEntity getBlockType ()Lnet/minecraft/block/Block;",
			"net/minecraft/tileentity/TileEntity receiveClientEvent (II)Z",
			"net/minecraft/tileentity/TileEntity getDescriptionPacket ()Lnet/minecraft/network/Packet;",
			"net/minecraft/tileentity/TileEntity addInfoToCrashReport (Lnet/minecraft/crash/CrashReportCategory;)V",
			"net/minecraft/tileentity/TileEntity validate ()V",
			"net/minecraft/tileentity/TileEntity invalidate ()V",
			"net/minecraft/tileentity/TileEntity updateContainingBlockInfo ()V",
			"net/minecraft/tileentity/TileEntity markDirty ()V",
			"net/minecraft/tileentity/TileEntity readFromNBT (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/nbt/NBTTagCompound;)V",
			"net/minecraft/tileentity/TileEntity writeToNBT (Lnet/minecraft/nbt/NBTTagCompound;)V"
			},
			depends={
			"net/minecraft/tileentity/TileEntity",
			"net/minecraft/world/World",
			"net/minecraft/nbt/NBTTagCompound",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/Block",
			"net/minecraft/init/Blocks"
			})
	public boolean processTileEntityClass()
	{
		ClassNode minecraftServer = getClassNode("net/minecraft/server/MinecraftServer");
		ClassNode tileEntity = getClassNodeFromMapping("net/minecraft/tileentity/TileEntity");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode tagCompound = getClassNodeFromMapping("net/minecraft/nbt/NBTTagCompound");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode blocks = getClassNodeFromMapping("net/minecraft/init/Blocks");
		if (!MeddleUtil.notNull(tileEntity, world, tagCompound, blockPos, block, blocks)) return false;
		
		List<MethodNode> methods = getMatchingMethods(tileEntity, "<clinit>", "()V");
		if (methods.size() != 1) return false;
		
		Map<String, String> teMap = new HashMap<>();
		
		String lastClass = null;
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
			String c = getLdcClass(insn);
			if (c != null) { lastClass = c; continue; }
			
			String name = getLdcString(insn);
			if (name == null || lastClass == null) continue;
			
			teMap.put(name, lastClass);			
		}
		
		
		String className = teMap.get("Chest");
		if (className != null) {
			addClassMapping("net/minecraft/tileentity/TileEntityChest", className);
		}
		
		
		
		
		// protected World worldObj
		List<FieldNode> fields = getMatchingFields(tileEntity, null, "L" + world.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/tileentity/TileEntity worldObj Lnet/minecraft/world/World;",
					tileEntity.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		// protected BlockPos pos
		fields = getMatchingFields(tileEntity, null, "L" + blockPos.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/tileentity/TileEntity pos Lnet/minecraft/util/BlockPos;",
					tileEntity.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		// protected boolean tileEntityInvalid
		fields = getMatchingFields(tileEntity, null, "Z");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/tileentity/TileEntity tileEntityInvalid Z",
					tileEntity.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		// private int blockMetadata;
		fields = getMatchingFields(tileEntity, null, "I");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/tileentity/TileEntity blockMetadata I",
					tileEntity.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		// protected Block blockType
		fields = getMatchingFields(tileEntity, null, "L" + block.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/tileentity/TileEntity blockType Lnet/minecraft/block/Block;",
					tileEntity.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		
		
		
		// public static void addMapping(Class cl, String id)
		methods = getMatchingMethods(tileEntity, null, "(Ljava/lang/Class;Ljava/lang/String;)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity addMapping (Ljava/lang/Class;Ljava/lang/String;)V",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public World getWorld()
		methods = getMatchingMethods(tileEntity, null, "()L" + world.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity getWorld ()Lnet/minecraft/world/World;",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		// public void setWorldObj(World worldIn)
		methods = getMatchingMethods(tileEntity, null, "(L" + world.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity setWorldObj (Lnet/minecraft/world/World;)V",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public static TileEntity createAndLoadEntity(NBTTagCompound nbt)
		// CHANGED in 15w38b
		// public static TileEntity createAndLoadEntity(MinecraftServer serevr, NBTTagCompound nbt)
		methods = getMatchingMethods(tileEntity, null, assembleDescriptor("(", minecraftServer, tagCompound, ")", tileEntity));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity createAndLoadEntity (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/tileentity/TileEntity;",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public int getBlockMetadata()
		methods = getMatchingMethods(tileEntity, null, "()I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity getBlockMetadata ()I",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
				
		// public BlockPos getPos()
		methods = getMatchingMethods(tileEntity, null, "()L" + blockPos.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity getPos ()Lnet/minecraft/util/BlockPos;",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public void setPos(BlockPos posIn)
		methods = getMatchingMethods(tileEntity, null, "(L" + blockPos.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity setPos (Lnet/minecraft/util/BlockPos;)V",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public Block getBlockType()
		methods = getMatchingMethods(tileEntity, null, "()L" + block.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity getBlockType ()Lnet/minecraft/block/Block;",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean receiveClientEvent(int id, int type)	
		methods = getMatchingMethods(tileEntity, null, "(II)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity receiveClientEvent (II)Z",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public Packet getDescriptionPacket()
		for (MethodNode method : tileEntity.methods) {
			if (!method.desc.startsWith("()L")) continue;
			String packetClass = Type.getMethodType(method.desc).getReturnType().getClassName();
			if (packetClass.contains(".")) continue;
			ClassNode packet = getClassNode(packetClass);
			if (packet == null) continue;
			if ((packet.access & Opcodes.ACC_INTERFACE) == 0) continue;
			
			boolean isPacket = false;
			for (MethodNode m : packet.methods) {		
				Type t = Type.getMethodType(m.desc);
				Type[] args = t.getArgumentTypes();
				if (args.length != 1) continue;
				String packetBuffer_name = args[0].getClassName();
				if (searchConstantPoolForStrings(packetBuffer_name, "VarInt too big", "String too big (was"))
				{
					addClassMapping("net/minecraft/network/PacketBuffer", packetBuffer_name);
					isPacket = true;
					break;
				}
			}			
			if (!isPacket) continue;
			
			addClassMapping("net/minecraft/network/Packet", packet);
			addMethodMapping("net/minecraft/tileentity/TileEntity getDescriptionPacket ()Lnet/minecraft/network/Packet;",
					tileEntity.name + " " + method.name + " " + method.desc);
			break;
		}
		
		
		// public void addInfoToCrashReport(CrashReportCategory reportCategory)
		methods.clear();
		for (MethodNode method : tileEntity.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			
			if (t.getReturnType().getSort() != Type.VOID) continue;			
			if (args.length != 1) continue;
			
			className = args[0].getClassName();
			if (searchConstantPoolForStrings(className, "(Error finding world loc)", "Details:")) {
				addClassMapping("net/minecraft/crash/CrashReportCategory", className);
				methods.add(method);
			}
		}
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity addInfoToCrashReport (Lnet/minecraft/crash/CrashReportCategory;)V",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void markDirty()	
		// public void updateContainingBlockInfo()
		// public void invalidate()		
		// public void validate()
		methods = getMatchingMethods(tileEntity, null, "()V");
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) { if (it.next().name.contains("<")) it.remove(); }
		if (methods.size() == 4) {
			List<MethodNode> matchedMethods = new ArrayList<>();
			
			// public void validate()
			for (MethodNode method : methods) {
				if (matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.ICONST_0, Opcodes.PUTFIELD, Opcodes.RETURN)) {
					matchedMethods.add(method);
				}
			}
			if (matchedMethods.size() == 1) {
				addMethodMapping("net/minecraft/tileentity/TileEntity validate ()V", tileEntity.name + " " + matchedMethods.get(0).name + " ()V");
				methods.remove(matchedMethods.get(0));
			}
			
			// public void invalidate()	
			matchedMethods.clear();			
			for (MethodNode method : methods) {
				if (matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.ICONST_1, Opcodes.PUTFIELD, Opcodes.RETURN)) {
					matchedMethods.add(method);
				}
			}
			if (matchedMethods.size() == 1) {				
				addMethodMapping("net/minecraft/tileentity/TileEntity invalidate ()V", tileEntity.name + " " + matchedMethods.get(0).name + " ()V");
				methods.remove(matchedMethods.get(0));
			}
			
			// public void updateContainingBlockInfo()
			matchedMethods.clear();
			for (MethodNode method : methods) {
				if (matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.ACONST_NULL, Opcodes.PUTFIELD, Opcodes.ALOAD, Opcodes.ICONST_M1, Opcodes.PUTFIELD, Opcodes.RETURN)) {
					matchedMethods.add(method);
				}
			}
			if (matchedMethods.size() == 1) {
				addMethodMapping("net/minecraft/tileentity/TileEntity updateContainingBlockInfo ()V", tileEntity.name + " " + matchedMethods.get(0).name + " ()V");
				methods.remove(matchedMethods.get(0));
			}
			
			// public void markDirty()	
			if (methods.size() == 1) {
				for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() != Opcodes.GETSTATIC) continue;
					if (((FieldInsnNode)insn).owner.equals(blocks.name)) {
						addMethodMapping("net/minecraft/tileentity/TileEntity markDirty ()V", tileEntity.name + " " + methods.get(0).name + " ()V");
						break;
					}
				}
			}
			
		}
				
		// TODO
		// public boolean hasWorldObj()
		// public boolean isInvalid()
		
		
		
		// public void readFromNBT(NBTTagCompound compound)		
		// public void writeToNBT(NBTTagCompound compound)		
		/*methods = getMatchingMethods(tileEntity, null, "(L" + tagCompound.name + ";)V");
		if (methods.size() == 2) {
			MethodNode readFromNBT = null;
			MethodNode writeToNBT = null;
			
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() != Opcodes.NEW) continue;
					className = ((TypeInsnNode)insn).desc;
					if (className.equals(blockPos.name)) readFromNBT = method;
					else if (className.equals("java/lang/RuntimeException")) writeToNBT = method;
				}
			}
			
			if (readFromNBT != null && writeToNBT != null && readFromNBT != writeToNBT) {
				addMethodMapping("net/minecraft/tileentity/TileEntity readFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)V",
						tileEntity.name + " " + readFromNBT.name + " " + readFromNBT.desc);
				addMethodMapping("net/minecraft/tileentity/TileEntity writeToNBT (Lnet/minecraft/nbt/NBTTagCompound;)V",
						tileEntity.name + " " + writeToNBT.name + " " + writeToNBT.desc);
			}
		}*/
		
		// CHANGED in 15w38b
		// public void readFromNBT(MinecraftServer server, NBTTagCompound compound)
		methods = getMatchingMethods(tileEntity, null, assembleDescriptor("(", minecraftServer, tagCompound, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity readFromNBT (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/nbt/NBTTagCompound;)V",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public void writeToNBT(NBTTagCompound compound)
		methods = getMatchingMethods(tileEntity, null, assembleDescriptor("(", tagCompound, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity writeToNBT (Lnet/minecraft/nbt/NBTTagCompound;)V",
					tileEntity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// TODO
		// @ClientOnly
		// public double getDistanceSq(double x, double y, double z)
	    // @ClientOnly
	    // public double getMaxRenderDistanceSquared()
		
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/inventory/ContainerChest",
			"net/minecraft/tileentity/TileEntityLockable",
			"net/minecraft/server/gui/IUpdatePlayerListBox"
			},
			providesMethods={
			},
			depends={
			"net/minecraft/tileentity/TileEntity",
			"net/minecraft/tileentity/TileEntityChest",
			"net/minecraft/entity/player/InventoryPlayer",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/inventory/Container",
			"net/minecraft/inventory/IInventory"
			})
	public boolean processTileEntityChestClass()
	{
		ClassNode tileEntity = getClassNodeFromMapping("net/minecraft/tileentity/TileEntity");
		ClassNode tileEntityChest = getClassNodeFromMapping("net/minecraft/tileentity/TileEntityChest");
		ClassNode inventoryPlayer = getClassNodeFromMapping("net/minecraft/entity/player/InventoryPlayer");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode container = getClassNodeFromMapping("net/minecraft/inventory/Container");
		ClassNode iInventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		if (!MeddleUtil.notNull(tileEntity, tileEntityChest, inventoryPlayer, entityPlayer, container, iInventory)) return false;
		
			
		
		if (tileEntityChest.interfaces.size() == 2) {
			String iUpdatePlayerListBox_name = null;
			int count = 0;
			for (String iface : tileEntityChest.interfaces) {
				if (iface.equals(iInventory.name)) continue;
				count++;
				iUpdatePlayerListBox_name = iface;
			}
			if (count == 1) {
				ClassNode iUpdatePlayerListBox = getClassNode(iUpdatePlayerListBox_name);
				if (iUpdatePlayerListBox.methods.size() == 1) {
					addClassMapping("net/minecraft/server/gui/IUpdatePlayerListBox", iUpdatePlayerListBox_name);
				}
			}
		}
		
		
		String tileEntityLockable_name = tileEntityChest.superName;
		ClassNode tileEntityLockable = getClassNode(tileEntityLockable_name);
		if (tileEntityLockable.superName.equals(tileEntity.name)) {
			addClassMapping("net/minecraft/tileentity/TileEntityLockable", tileEntityLockable_name);
		}
		
		
		// public Container createContainer(InventoryPlayer playerInventory, EntityPlayer playerIn)
		List<MethodNode> methods = getMatchingMethods(tileEntityChest, null, assembleDescriptor("(", inventoryPlayer, entityPlayer, ")", container));
		if (methods.size() == 1) {
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.NEW) continue;
				TypeInsnNode tn = (TypeInsnNode)insn;
				ClassNode containerChest = getClassNode(tn.desc);
				if (!containerChest.superName.equals(container.name)) continue;
				addClassMapping("net/minecraft/inventory/ContainerChest", containerChest);
			}
		}
		
		return true;
	}
	
	
	
	@Mapping(provides={
			},
			providesMethods={
			"net/minecraft/inventory/IInventory openInventory (Lnet/minecraft/entity/player/EntityPlayer;)V",
			"net/minecraft/inventory/IInventory closeInventory (Lnet/minecraft/entity/player/EntityPlayer;)V"
			},
			depends={			
			"net/minecraft/inventory/ContainerChest",
			"net/minecraft/inventory/IInventory",
			"net/minecraft/entity/player/EntityPlayer"
			})
	public boolean processContainerChest()
	{		
		ClassNode containerChest = getClassNodeFromMapping("net/minecraft/inventory/ContainerChest");
		ClassNode iInventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		if (!MeddleUtil.notNull(containerChest, iInventory, entityPlayer)) return false;
		
		String openInventory = null;
		
		List<MethodNode> methods = getMatchingMethods(containerChest, "<init>", assembleDescriptor("(", iInventory, iInventory, entityPlayer, ")V"));
		if (methods.size() == 1) {
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.INVOKEINTERFACE) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (mn.owner.equals(iInventory.name) && mn.desc.equals("(L" + entityPlayer.name + ";)V")) {
					openInventory = mn.name;
				}
			}
		}
		
		methods = getMatchingMethods(iInventory, null, "(L" + entityPlayer.name + ";)V");
		if (openInventory != null && methods.size() == 2) {
			for (MethodNode method : methods) {
				if (method.name.equals(openInventory)) {			
					addMethodMapping("net/minecraft/inventory/IInventory openInventory (Lnet/minecraft/entity/player/EntityPlayer;)V",
							iInventory.name + " " + method.name + " " + method.desc);
				}
				else {
					addMethodMapping("net/minecraft/inventory/IInventory closeInventory (Lnet/minecraft/entity/player/EntityPlayer;)V",
							iInventory.name + " " + method.name + " " + method.desc);
				}
			}
		}
		
		
		return true;
	}
	
	

	@Mapping(provides="net/minecraft/entity/player/EntityPlayer",
			depends={
			"net/minecraft/server/MinecraftServer",
			"net/minecraft/world/World",
	"net/minecraft/util/BlockPos"})
	public boolean getEntityPlayerClass()
	{
		ClassNode serverClass = getClassNode(getClassMapping("net/minecraft/server/MinecraftServer"));
		ClassNode worldClass = getClassNode(getClassMapping("net/minecraft/world/World"));
		ClassNode blockPosClass = getClassNode(getClassMapping("net/minecraft/util/BlockPos"));
		if (serverClass == null || worldClass == null || blockPosClass == null) return false;

		List<String> potentialClasses = new ArrayList<String>();

		// Find isBlockProtected(World, BlockPos, EntityPlayer)Z
		for (MethodNode method : serverClass.methods)
		{
			Type t = Type.getMethodType(method.desc);
			if (t.getReturnType().getSort() != Type.BOOLEAN) continue;

			if (!checkMethodParameters(method, Type.OBJECT, Type.OBJECT, Type.OBJECT)) continue;
			Type[] args = t.getArgumentTypes();

			if (!args[0].getClassName().equals(worldClass.name) || !args[1].getClassName().equals(blockPosClass.name)) continue;
			potentialClasses.add(args[2].getClassName());
		}

		if (potentialClasses.size() != 1) return false;
		String className = potentialClasses.get(0);
		if (!searchConstantPoolForStrings(className, "Inventory", "Notch")) return false;

		addClassMapping("net/minecraft/entity/player/EntityPlayer", getClassNode(className));
		return true;
	}


	@Mapping(provides="net/minecraft/entity/EntityLivingBase", depends="net/minecraft/entity/player/EntityPlayer")
	public boolean getEntityLivingBaseClass()
	{
		ClassNode entityPlayerClass = getClassNode(getClassMapping("net/minecraft/entity/player/EntityPlayer"));
		if (entityPlayerClass == null || entityPlayerClass.superName == null) return false;

		if (!searchConstantPoolForStrings(entityPlayerClass.superName, "Health", "doMobLoot", "ai")) return false;

		addClassMapping("net/minecraft/entity/EntityLivingBase", getClassNode(entityPlayerClass.superName));
		return true;
	}


	@Mapping(provides="net/minecraft/entity/Entity", depends="net/minecraft/entity/EntityLivingBase")
	public boolean getEntityClass()
	{
		ClassNode entityLivingBase = getClassNode(getClassMapping("net/minecraft/entity/EntityLivingBase"));
		if (entityLivingBase == null || entityLivingBase.superName == null) return false;

		ClassNode entity = getClassNode(entityLivingBase.superName);
		if (!entity.superName.equals("java/lang/Object")) return false;

		addClassMapping("net/minecraft/entity/Entity", entity);
		return true;
	}

	
	@Mapping(providesMethods={
			"net/minecraft/entity/Entity getFlag (I)Z",
			"net/minecraft/entity/Entity isSneaking ()Z"
			},
			depends={
			"net/minecraft/entity/Entity"
			})
	public boolean processEntityClass()
	{
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");
		if (entity == null) return false;
		
		MethodNode getFlag = null;
		
		// protected boolean getFlag(int flag)
		List<MethodNode> methods = getMatchingMethods(entity, null, "(I)Z");
		if (methods.size() == 1) {
			getFlag = methods.get(0);
			addMethodMapping("net/minecraft/entity/Entity getFlag (I)Z",
					entity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public boolean isSneaking()
		methods = getMatchingMethods(entity, null, "()Z");
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) 
		{
			MethodNode method = it.next();
			AbstractInsnNode[] nodes = getOpcodeSequenceArray(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.ICONST_1, Opcodes.INVOKEVIRTUAL, Opcodes.IRETURN);
			if (nodes == null) { it.remove(); continue; }
			MethodInsnNode mn = (MethodInsnNode)nodes[2];
			if (!mn.owner.equals(entity.name) || !mn.name.equals(getFlag.name) || !mn.desc.equals(getFlag.desc)) it.remove();
		}
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/entity/Entity isSneaking ()Z", 
					entity.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}
	
	

	@Mapping(provides="net/minecraft/entity/EntityList", depends="net/minecraft/entity/Entity")
	public boolean getEntityListClass()
	{
		ClassNode entity = getClassNode(getClassMapping("net/minecraft/entity/Entity"));
		if (entity == null) return false;

		String className = null;

		// Try to find: public void travelToDimension(int)
		// NOTE: Mojang changed to public Entity travelToDimension(int, BlockPos)
		for (MethodNode method : entity.methods)
		{
			if (!method.desc.startsWith("(I")) continue;

			boolean foundFirst = false;
			boolean foundSecond = false;

			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (!foundFirst && !isLdcWithString(insn, "changeDimension")) continue;
				else foundFirst = true;

				if (!foundSecond && !isLdcWithString(insn, "reloading")) continue;
				else foundSecond = true;

				if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
					MethodInsnNode mn = (MethodInsnNode)insn;
					if (className == null) { className = mn.owner; continue; }
					else if (!className.equals(mn.owner)) return false;
				}

				if (className != null) break;
			}
			if (className != null) break;
		}

		if (!searchConstantPoolForStrings(className, "ThrownPotion", "EnderDragon")) return false;

		addClassMapping("net/minecraft/entity/EntityList", getClassNode(className));
		return true;
	}



	// Parses net.minecraft.entity.EntityList to match entity names to
	// their associated classes.
	// Note: Doesn't handle minecarts, as those aren't registered directly with names.
	@Mapping(provides={
			"net/minecraft/entity/monster/EntityZombie",
			"net/minecraft/entity/passive/EntityVillager",
			"net/minecraft/entity/passive/EntitySheep",
			"net/minecraft/entity/monster/EntityEnderman",
			"net/minecraft/entity/item/EntityEnderPearl",
			"net/minecraft/entity/monster/EntityCreeper"
	},
	depends="net/minecraft/entity/EntityList")
	public boolean parseEntityList()
	{
		Map<String, String> entityListClasses = new HashMap<String, String>();

		ClassNode entityList = getClassNode(getClassMapping("net/minecraft/entity/EntityList"));
		if (entityList == null) return false;

		List<MethodNode> methods = getMatchingMethods(entityList, "<clinit>", "()V");
		if (methods.size() != 1) return false;

		String entityClass = null;
		String entityName = null;

		// Create entity list
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext())
		{
			entityClass = getLdcClass(insn);
			if (entityClass == null) continue;

			insn = insn.getNext();
			if (insn == null) break;

			entityName = getLdcString(insn);
			if (entityName == null) continue;

			entityListClasses.put(entityName, entityClass);
		}

		// Get EntityZombie
		String zombieClass = entityListClasses.get("Zombie");
		if (zombieClass != null) {
			if (searchConstantPoolForStrings(zombieClass,  "zombie.spawnReinforcements", "IsBaby")) {
				addClassMapping("net/minecraft/entity/monster/EntityZombie", zombieClass);
			}
		}

		String villagerClass = entityListClasses.get("Villager");
		if (villagerClass != null) {
			if (searchConstantPoolForStrings(villagerClass, "Profession", "entity.Villager.")) {
				addClassMapping("net/minecraft/entity/passive/EntityVillager", villagerClass);
			}
		}
		
		// Can't detect "mob.sheep.shear" as of 15w43a
		String sheepClass = entityListClasses.get("Sheep");
		if (sheepClass != null) {
			if (searchConstantPoolForStrings(sheepClass, "Sheared")) {
				addClassMapping("net/minecraft/entity/passive/EntitySheep", sheepClass);
			}
		}

		
		// Can't detect for "mob.endermen.portal" as of 15w43a		
		String endermanClass = entityListClasses.get("Enderman");
		if (endermanClass != null) {
			if (searchConstantPoolForStrings(endermanClass, "carried", "carriedData")) {
				addClassMapping("net/minecraft/entity/monster/EntityEnderman", endermanClass);
			}
		}
		
		String enderpearlClass = entityListClasses.get("ThrownEnderpearl");
		if (enderpearlClass != null) {
			if (searchConstantPoolForStrings(enderpearlClass, "doMobSpawning")) { // TODO - Better detection
				addClassMapping("net/minecraft/entity/item/EntityEnderPearl", enderpearlClass);
			}
		}
		
		String creeperClass = entityListClasses.get("Creeper");
		if (creeperClass != null) {
			if (searchConstantPoolForStrings(creeperClass, "mobGriefing", "Fuse", "ExplosionRadius")) {
				addClassMapping("net/minecraft/entity/monster/EntityCreeper", creeperClass);
			}
		}

		return true;

	}	
	
	
	@Mapping(provides={
			"net/minecraft/entity/monster/EntityMob",
			"net/minecraft/world/GameRules",
			"net/minecraft/world/Explosion"
			},
			providesMethods={
			"net/minecraft/entity/Entity onUpdate ()V",
			"net/minecraft/entity/Entity playSound (Lnet/minecraft/util/Sound;FF)V",
			"net/minecraft/entity/monster/EntityCreeper explode ()V",
			"net/minecraft/world/World getGameRules ()Lnet/minecraft/world/GameRules;",
			"net/minecraft/world/World createExplosion (Lnet/minecraft/entity/Entity;DDDFZ)Lnet/minecraft/world/Explosion;"
			},
			dependsFields={
			"net/minecraft/init/Sounds entity_creeper_primed Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds entity_hostile_hurt Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds entity_hostile_swim Lnet/minecraft/util/Sound;",
			"net/minecraft/init/Sounds entity_generic_explode Lnet/minecraft/util/Sound;"
			},
			depends={
			"net/minecraft/entity/monster/EntityCreeper",
			"net/minecraft/entity/Entity",
			"net/minecraft/world/World",
			"net/minecraft/init/Sounds",
			"net/minecraft/util/Sound"
			})
	public boolean processEntityCreeperClass()
	{
		ClassNode creeper = getClassNodeFromMapping("net/minecraft/entity/monster/EntityCreeper");
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode sounds = getClassNodeFromMapping("net/minecraft/init/Sounds");
		ClassNode sound = getClassNodeFromMapping("net/minecraft/util/Sound");
		if (!MeddleUtil.notNull(creeper, entity, world, sound, sounds)) return false;		
		
		
		String hurtSound = getSoundFieldFull("entity.hostile.hurt");
		String swimSound = getSoundFieldFull("entity.hostile.swim");
		if (searchConstantPoolForFields(creeper.superName, hurtSound, swimSound))
			addClassMapping("net/minecraft/entity/monster/EntityMob", creeper.superName);

		
		String fieldPrimed = getSoundField("entity.creeper.primed");		
		
		// public void onUpdate()
		List<MethodNode> methods = getMatchingMethods(creeper, null, "()V");
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {			
			MethodNode method = it.next();
			boolean hasIt = false;
			//for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				//if (isLdcWithString(insn, "creeper.primed")) hasIt = true;				
			//}
			List<FieldInsnNode> fields = getAllInsnNodesOfType(method.instructions.getFirst(), FieldInsnNode.class);
			for (FieldInsnNode fn : fields) {
				if (fn.owner.equals(sounds.name) && fn.name.equals(fieldPrimed)) hasIt = true;
			}
			
			if (!hasIt) it.remove();
		}
		String explodeName = null;
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/entity/Entity onUpdate ()V", entity.name + " " + methods.get(0).name + " ()V");
			
			MethodInsnNode playSound = null;
			int playSoundCount = 0;
			MethodInsnNode explode = null;
			int explodeCount = 0;
			
			// public void playSound(String name, float volume, float pitch)
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : nodes) {
				//System.out.println(mn.owner + "(" + DynamicMappings.getReverseClassMapping(mn.owner) + ") " + mn.name + " " + mn.desc);
				if (mn.owner.equals(creeper.name) && mn.desc.equals("(L" + sound.name + ";FF)V")) {
					playSound = mn;
					playSoundCount++;
				}
				if (mn.owner.equals(creeper.name) && mn.desc.equals("()V")) {
					explode = mn;
					explodeCount++;
				}
				
				if (playSoundCount == 1) {
					addMethodMapping("net/minecraft/entity/Entity playSound (Lnet/minecraft/util/Sound;FF)V", 
							entity.name + " " + playSound.name + " " + playSound.desc);
				}
				if (explodeCount == 1) {
					addMethodMapping("net/minecraft/entity/monster/EntityCreeper explode ()V", creeper.name + " " + explode.name + " ()V");
					explodeName = explode.name;
				}
			}
		}

		String gameRules = null;
		
		String soundExplosion = getSoundFieldFull("entity.generic.explode");		
		
		// net/minecraft/world/GameRules 
		// World: public GameRules getGameRules()
		// World: public Explosion createExplosion(Entity param0, double param1, double param2, double param3, float param4, boolean param5) {
		MethodNode explode = getMethodNodeFromMapping(creeper, "net/minecraft/entity/monster/EntityCreeper explode ()V");
		if (explode != null) {
			List<MethodInsnNode> miNodes = getAllInsnNodesOfType(explode.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : miNodes) {
				//System.out.println(mn.owner + "(" + DynamicMappings.getReverseClassMapping(mn.owner) + ") " + mn.name + " " + mn.desc);
				
				if (mn.owner.equals(world.name)) {
					if (mn.desc.startsWith("()L")) {
						String returnClass = Type.getMethodType(mn.desc).getReturnType().getClassName();
						if (searchConstantPoolForStrings(returnClass, "doFireTick", "doDaylightCycle", "reducedDebugInfo")) {
							gameRules = returnClass;
							addClassMapping("net/minecraft/world/GameRules", returnClass);
							addMethodMapping("net/minecraft/world/World getGameRules ()Lnet/minecraft/world/GameRules;",
									world.name + " " + mn.name + " " + mn.desc);
						}
					}
					else if (mn.desc.startsWith("(L" + entity.name + ";DDDFZ)L")) {
						String returnClass = Type.getMethodType(mn.desc).getReturnType().getClassName();
						if (searchConstantPoolForFields(returnClass, soundExplosion)) {
							addClassMapping("net/minecraft/world/Explosion", returnClass);
							addMethodMapping("net/minecraft/world/World createExplosion (Lnet/minecraft/entity/Entity;DDDFZ)Lnet/minecraft/world/Explosion;",
									world.name + " " + mn.name + " " + mn.desc);
						}
					}
				}
				
			}
		}		
		
		
		return true;
	}
	

	@Mapping(providesMethods={
			"net/minecraft/world/Explosion doExplosionA ()V",
			"net/minecraft/world/Explosion doExplosionB (Z)V"
			},
			depends={
			"net/minecraft/world/Explosion"
			})
	public boolean processExplosionClass()
	{
		ClassNode explosion = getClassNodeFromMapping("net/minecraft/world/Explosion");
		if (explosion == null) return false;
		
		// public void doExplosionA()
		List<MethodNode> methods = getMatchingMethods(explosion, null, "()V");
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			MethodNode mn = it.next();
			if (mn.name.startsWith("<")) { it.remove(); continue; }
			if (matchOpcodeSequence(mn.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.INVOKEINTERFACE, Opcodes.RETURN)) { 
				it.remove(); 
				continue; 
			}
		}
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/Explosion doExplosionA ()V", 
					explosion.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void doExplosionB(boolean p_77279_1_)
		methods = getMatchingMethods(explosion, null, "(Z)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/Explosion doExplosionB (Z)V", 
					explosion.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}
	
	
	
	@Mapping(provides={
			}, 
			providesFields={
			"net/minecraft/entity/monster/EntityEnderman carriableBlocks Ljava/util/Set;"
			}, 
			providesMethods={
			"net/minecraft/entity/Entity setPositionAndUpdate (DDD)V"
			}, 
			depends={
			"net/minecraft/entity/monster/EntityEnderman",
			"net/minecraft/entity/EntityLivingBase",
			"net/minecraft/entity/Entity"
			})
	public boolean processEntityEndermanClass()
	{
		ClassNode entityEnderman = getClassNodeFromMapping("net/minecraft/entity/monster/EntityEnderman");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");
		if (!MeddleUtil.notNull(entityEnderman, entityLivingBase, entity)) return false;
		
		List<FieldNode> fields = getMatchingFields(entityEnderman, null, "Ljava/util/Set;");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/entity/monster/EntityEnderman carriableBlocks Ljava/util/Set;",
					entityEnderman.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
				
		
		List<MethodNode> methods = getMatchingMethods(entityEnderman, null, "(L" + entityLivingBase.name + ";DDD)Z");
		if (methods.size() == 1) {			
			
			Set<String> methodNames = new HashSet<>();
			
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
					MethodInsnNode mn = (MethodInsnNode)insn;
					if (mn.owner.equals(entityLivingBase.name) && mn.desc.equals("(DDD)V")) methodNames.add(mn.name);
					continue;
				}
			}
			
			// Entity.setPositionAndUpdate(DDD)V
			if (methodNames.size() == 1) {
				String name = methodNames.iterator().next();
				addMethodMapping("net/minecraft/entity/Entity setPositionAndUpdate (DDD)V", entity.name + " " + name + " (DDD)V");
			}			
		}
		
		
		// TODO
		/*for (FieldNode field : entityEnderman.fields) {
			Type t = Type.getType(field.desc);
			if (t.getSort() != Type.OBJECT) continue;
			System.out.println(t.getClassName());
		}*/
		
		return true;
	}
	
	
	@Mapping(providesFields={
			"net/minecraft/entity/Entity fallDistance F"
			},
			depends={
			"net/minecraft/entity/item/EntityEnderPearl",
			"net/minecraft/entity/Entity",
			"net/minecraft/entity/EntityLivingBase",
			"net/minecraft/util/MovingObjectPosition"
			})
	public boolean processEntityEnderPearlClass()
	{
		ClassNode pearl = getClassNodeFromMapping("net/minecraft/entity/item/EntityEnderPearl");
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");
		ClassNode movingObjectPos = getClassNodeFromMapping("net/minecraft/util/MovingObjectPosition");
		if (pearl == null || entity == null || movingObjectPos == null) return false;
		
		// protected void onImpact(MovingObjectPosition param0)
		List<MethodNode> methods = getMatchingMethods(pearl, null, "(L" + movingObjectPos.name + ";)V");
		if (methods.size() == 1) {
			Set<String> fieldNames = new HashSet<>();
			
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {				
				if (insn.getOpcode() == Opcodes.FCONST_0) {
					AbstractInsnNode node = getNextRealOpcode(insn.getNext());
					if (node != null && node.getOpcode() == Opcodes.PUTFIELD) {
						FieldInsnNode fn = (FieldInsnNode)node;
						if (fn.owner.equals(entityLivingBase.name) && fn.desc.equals("F")) fieldNames.add(fn.name);
					}
				}
			}		

			if (fieldNames.size() == 1 ) {
				String name = fieldNames.iterator().next();
				addFieldMapping("net/minecraft/entity/Entity fallDistance F", entity.name + " " + name + " F");
			}
		}
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/block/BlockAir",
			"net/minecraft/block/BlockStone",
			"net/minecraft/block/BlockGrass",
			"net/minecraft/block/BlockDirt",
			"net/minecraft/block/BlockPlanks",
			"net/minecraft/block/BlockSapling",
			"net/minecraft/block/BlockDynamicLiquid",
			"net/minecraft/block/BlockStaticLiquid",
			"net/minecraft/block/BlockSand",
			"net/minecraft/block/BlockGravel",
			"net/minecraft/block/BlockOre",
			"net/minecraft/block/BlockOldLog",
			"net/minecraft/block/BlockOldLeaf",
			"net/minecraft/block/BlockSponge",
			"net/minecraft/block/BlockGlass",
			"net/minecraft/block/BlockDispenser",
			"net/minecraft/block/BlockSandStone",
			"net/minecraft/block/BlockNote",
			"net/minecraft/block/BlockBed",
			"net/minecraft/block/BlockRailPowered",
			"net/minecraft/block/BlockRailDetector",
			"net/minecraft/block/BlockPistonBase",
			"net/minecraft/block/BlockWeb",
			"net/minecraft/block/BlockTallGrass",
			"net/minecraft/block/BlockDeadBush",
			"net/minecraft/block/BlockPistonExtension",
			"net/minecraft/block/BlockColored",
			"net/minecraft/block/BlockPistonMoving",
			"net/minecraft/block/BlockYellowFlower",
			"net/minecraft/block/BlockRedFlower",
			"net/minecraft/block/BlockMushroom",
			"net/minecraft/block/BlockDoubleStoneSlab",
			"net/minecraft/block/BlockHalfStoneSlab",
			"net/minecraft/block/BlockTNT",
			"net/minecraft/block/BlockBookshelf",
			"net/minecraft/block/BlockObsidian",
			"net/minecraft/block/BlockTorch",
			"net/minecraft/block/BlockFire",
			"net/minecraft/block/BlockMobSpawner",
			"net/minecraft/block/BlockStairs",
			"net/minecraft/block/BlockChest",
			"net/minecraft/block/BlockRedstoneWire",
			"net/minecraft/block/BlockWorkbench",
			"net/minecraft/block/BlockWorkbench$InterfaceCraftingTable",
			"net/minecraft/block/BlockCrops",
			"net/minecraft/block/BlockFarmland",
			"net/minecraft/block/BlockFurnace",
			"net/minecraft/block/BlockStandingSign",
			"net/minecraft/block/BlockDoor",
			"net/minecraft/block/BlockLadder",
			"net/minecraft/block/BlockRail",
			"net/minecraft/block/BlockWallSign",
			"net/minecraft/block/BlockLever",
			"net/minecraft/block/BlockPressurePlate",
			"net/minecraft/block/BlockRedstoneOre",
			"net/minecraft/block/BlockRedstoneTorch",
			"net/minecraft/block/BlockButtonStone",
			"net/minecraft/block/BlockSnow",
			"net/minecraft/block/BlockIce",
			"net/minecraft/block/BlockSnowBlock",
			"net/minecraft/block/BlockCactus",
			"net/minecraft/block/BlockClay",
			"net/minecraft/block/BlockReed",
			"net/minecraft/block/BlockJukebox",
			"net/minecraft/block/BlockFence",
			"net/minecraft/block/BlockPumpkin",
			"net/minecraft/block/BlockNetherrack",
			"net/minecraft/block/BlockSoulSand",
			"net/minecraft/block/BlockGlowstone",
			"net/minecraft/block/BlockPortal",
			"net/minecraft/block/BlockCake",
			"net/minecraft/block/BlockRedstoneRepeater",
			"net/minecraft/block/BlockStainedGlass",
			"net/minecraft/block/BlockTrapDoor",
			"net/minecraft/block/BlockSilverfish",
			"net/minecraft/block/BlockStoneBrick",
			"net/minecraft/block/BlockHugeMushroom",
			"net/minecraft/block/BlockPane",
			"net/minecraft/block/BlockMelon",
			"net/minecraft/block/BlockStem",
			"net/minecraft/block/BlockVine",
			"net/minecraft/block/BlockFenceGate",
			"net/minecraft/block/BlockMycelium",
			"net/minecraft/block/BlockLilyPad",
			"net/minecraft/block/BlockNetherBrick",
			"net/minecraft/block/BlockNetherWart",
			"net/minecraft/block/BlockEnchantmentTable",
			"net/minecraft/block/BlockBrewingStand",
			"net/minecraft/block/BlockCauldron",
			"net/minecraft/block/BlockEndPortal",
			"net/minecraft/block/BlockEndPortalFrame",
			"net/minecraft/block/BlockDragonEgg",
			"net/minecraft/block/BlockRedstoneLight",
			"net/minecraft/block/BlockDoubleWoodSlab",
			"net/minecraft/block/BlockHalfWoodSlab",
			"net/minecraft/block/BlockCocoa",
			"net/minecraft/block/BlockEnderChest",
			"net/minecraft/block/BlockTripWireHook",
			"net/minecraft/block/BlockTripWire",
			"net/minecraft/block/BlockCommandBlock",
			"net/minecraft/block/BlockBeacon",
			"net/minecraft/block/BlockWall",
			"net/minecraft/block/BlockFlowerPot",
			"net/minecraft/block/BlockCarrot",
			"net/minecraft/block/BlockPotato",
			"net/minecraft/block/BlockButtonWood",
			"net/minecraft/block/BlockSkull",
			"net/minecraft/block/BlockAnvil",
			"net/minecraft/block/BlockPressurePlateWeighted",
			"net/minecraft/block/BlockRedstoneComparator",
			"net/minecraft/block/BlockDaylightDetector",
			"net/minecraft/block/BlockCompressedPowered",
			"net/minecraft/block/BlockHopper",
			"net/minecraft/block/BlockQuartz",
			"net/minecraft/block/BlockDropper",
			"net/minecraft/block/BlockStainedGlassPane",
			"net/minecraft/block/BlockNewLeaf",
			"net/minecraft/block/BlockNewLog",
			"net/minecraft/block/BlockSlime",
			"net/minecraft/block/BlockBarrier",
			"net/minecraft/block/BlockPrismarine",
			"net/minecraft/block/BlockSeaLantern",
			"net/minecraft/block/BlockHay",
			"net/minecraft/block/BlockCarpet",
			"net/minecraft/block/BlockHardenedClay",
			"net/minecraft/block/BlockPackedIce",
			"net/minecraft/block/BlockDoublePlant",
			"net/minecraft/block/BlockBanner$BlockBannerStanding",
			"net/minecraft/block/BlockBanner$BlockBannerHanging",
			"net/minecraft/block/BlockRedSandstone",
			"net/minecraft/block/BlockDoubleStoneSlabNew",
			"net/minecraft/block/BlockHalfStoneSlabNew"
			},			
			dependsMethods={
			"net/minecraft/block/Block registerBlocks ()V"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/util/ResourceLocation"
			})
	public boolean discoverBlocks()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		MethodNode registerBlocks = getMethodNodeFromMapping(block, "net/minecraft/block/Block registerBlocks ()V");
		MethodNode registerBlock1 = getMethodNodeFromMapping(block, "net/minecraft/block/Block registerBlock (ILjava/lang/String;Lnet/minecraft/block/Block;)V");
		MethodNode registerBlock2 = getMethodNodeFromMapping(block, "net/minecraft/block/Block registerBlock (ILnet/minecraft/util/ResourceLocation;Lnet/minecraft/block/Block;)V");
		if (registerBlocks == null || registerBlock1 == null || registerBlock2 == null) return false;
		
		
		// Create a map if the block ids and their respective classes.
		Map<Integer, String> blockClassMap = new HashMap<>();
		blockClassMap.put(0, "net/minecraft/block/BlockAir");	// 0 - air
		blockClassMap.put(1, "net/minecraft/block/BlockStone");	// 1 - stone
		blockClassMap.put(2, "net/minecraft/block/BlockGrass");	// 2 - grass
		blockClassMap.put(3, "net/minecraft/block/BlockDirt");	// 3 - dirt
		// 4 - cobblestone already mapped (Block)
		blockClassMap.put(5, "net/minecraft/block/BlockPlanks");	// 5 - planks
		blockClassMap.put(6, "net/minecraft/block/BlockSapling");	// 6 - sapling
		// 7 - bedrock already mapped (Block)
		blockClassMap.put(8, "net/minecraft/block/BlockDynamicLiquid");	// 8 - flowing_water
		blockClassMap.put(9, "net/minecraft/block/BlockStaticLiquid");	// 9 - water
		// 10 - flowing_lava already mapped (BlockDynamicLiquid)
		// 11 - lava already mapped (BlockStaticLiquid)
		blockClassMap.put(12, "net/minecraft/block/BlockSand");	// 12 - sand
		blockClassMap.put(13, "net/minecraft/block/BlockGravel");	// 13 - gravel
		blockClassMap.put(14, "net/minecraft/block/BlockOre");	// 14 - gold_ore
		// 15 - iron_ore already mapped (BlockOre)
		// 16 - coal_ore already mapped (BlockOre)
		blockClassMap.put(17, "net/minecraft/block/BlockOldLog");	// 17 - log
		blockClassMap.put(18, "net/minecraft/block/BlockOldLeaf");	// 18 - leaves
		blockClassMap.put(19, "net/minecraft/block/BlockSponge");	// 19 - sponge
		blockClassMap.put(20, "net/minecraft/block/BlockGlass");	// 20 - glass
		// 21 - lapis_ore already mapped (BlockOre)
		// blockClassMap.put(22, "net/minecraft/block/BlockCompressed");	// 22 - lapis_block - now it's a Block
		blockClassMap.put(23, "net/minecraft/block/BlockDispenser");	// 23 - dispenser
		blockClassMap.put(24, "net/minecraft/block/BlockSandStone");	// 24 - sandstone
		blockClassMap.put(25, "net/minecraft/block/BlockNote");	// 25 - noteblock
		blockClassMap.put(26, "net/minecraft/block/BlockBed");	// 26 - bed
		blockClassMap.put(27, "net/minecraft/block/BlockRailPowered");	// 27 - golden_rail
		blockClassMap.put(28, "net/minecraft/block/BlockRailDetector");	// 28 - detector_rail
		blockClassMap.put(29, "net/minecraft/block/BlockPistonBase");	// 29 - sticky_piston
		blockClassMap.put(30, "net/minecraft/block/BlockWeb");	// 30 - web
		blockClassMap.put(31, "net/minecraft/block/BlockTallGrass");	// 31 - tallgrass
		blockClassMap.put(32, "net/minecraft/block/BlockDeadBush");	// 32 - deadbush
		// 33 - piston already mapped (BlockPistonBase)
		blockClassMap.put(34, "net/minecraft/block/BlockPistonExtension");	// 34 - piston_head
		blockClassMap.put(35, "net/minecraft/block/BlockColored");	// 35 - wool
		blockClassMap.put(36, "net/minecraft/block/BlockPistonMoving");	// 36 - piston_extension
		blockClassMap.put(37, "net/minecraft/block/BlockYellowFlower");	// 37 - yellow_flower
		blockClassMap.put(38, "net/minecraft/block/BlockRedFlower");	// 38 - red_flower
		blockClassMap.put(39, "net/minecraft/block/BlockMushroom");	// 39 - brown_mushroom
		// 40 - red_mushroom already mapped (BlockMushroom)
		// 41 - gold_block already mapped (BlockCompressed)
		// 42 - iron_block already mapped (BlockCompressed)
		blockClassMap.put(43, "net/minecraft/block/BlockDoubleStoneSlab");	// 43 - double_stone_slab
		blockClassMap.put(44, "net/minecraft/block/BlockHalfStoneSlab");	// 44 - stone_slab
		// 45 - brick_block already mapped (Block)
		blockClassMap.put(46, "net/minecraft/block/BlockTNT");	// 46 - tnt
		blockClassMap.put(47, "net/minecraft/block/BlockBookshelf");	// 47 - bookshelf
		// 48 - mossy_cobblestone already mapped (Block)
		blockClassMap.put(49, "net/minecraft/block/BlockObsidian");	// 49 - obsidian
		blockClassMap.put(50, "net/minecraft/block/BlockTorch");	// 50 - torch
		blockClassMap.put(51, "net/minecraft/block/BlockFire");	// 51 - fire
		blockClassMap.put(52, "net/minecraft/block/BlockMobSpawner");	// 52 - mob_spawner
		blockClassMap.put(53, "net/minecraft/block/BlockStairs");	// 53 - oak_stairs
		blockClassMap.put(54, "net/minecraft/block/BlockChest");	// 54 - chest
		blockClassMap.put(55, "net/minecraft/block/BlockRedstoneWire");	// 55 - redstone_wire
		// 56 - diamond_ore already mapped (BlockOre)
		// 57 - diamond_block already mapped (BlockCompressed)
		blockClassMap.put(58, "net/minecraft/block/BlockWorkbench");	// 58 - crafting_table
		blockClassMap.put(59, "net/minecraft/block/BlockCrops");	// 59 - wheat
		blockClassMap.put(60, "net/minecraft/block/BlockFarmland");	// 60 - farmland
		blockClassMap.put(61, "net/minecraft/block/BlockFurnace");	// 61 - furnace
		// 62 - lit_furnace already mapped (BlockFurnace)
		blockClassMap.put(63, "net/minecraft/block/BlockStandingSign");	// 63 - standing_sign
		blockClassMap.put(64, "net/minecraft/block/BlockDoor");	// 64 - wooden_door
		blockClassMap.put(65, "net/minecraft/block/BlockLadder");	// 65 - ladder
		blockClassMap.put(66, "net/minecraft/block/BlockRail");	// 66 - rail
		// 67 - stone_stairs already mapped (BlockStairs)
		blockClassMap.put(68, "net/minecraft/block/BlockWallSign");	// 68 - wall_sign
		blockClassMap.put(69, "net/minecraft/block/BlockLever");	// 69 - lever
		blockClassMap.put(70, "net/minecraft/block/BlockPressurePlate");	// 70 - stone_pressure_plate
		// 71 - iron_door already mapped (BlockDoor)
		// 72 - wooden_pressure_plate already mapped (BlockPressurePlate)
		blockClassMap.put(73, "net/minecraft/block/BlockRedstoneOre");	// 73 - redstone_ore
		// 74 - lit_redstone_ore already mapped (BlockRedstoneOre)
		blockClassMap.put(75, "net/minecraft/block/BlockRedstoneTorch");	// 75 - unlit_redstone_torch
		// 76 - redstone_torch already mapped (BlockRedstoneTorch)
		blockClassMap.put(77, "net/minecraft/block/BlockButtonStone");	// 77 - stone_button
		blockClassMap.put(78, "net/minecraft/block/BlockSnow");	// 78 - snow_layer
		blockClassMap.put(79, "net/minecraft/block/BlockIce");	// 79 - ice
		blockClassMap.put(80, "net/minecraft/block/BlockSnowBlock");	// 80 - snow
		blockClassMap.put(81, "net/minecraft/block/BlockCactus");	// 81 - cactus
		blockClassMap.put(82, "net/minecraft/block/BlockClay");	// 82 - clay
		blockClassMap.put(83, "net/minecraft/block/BlockReed");	// 83 - reeds
		blockClassMap.put(84, "net/minecraft/block/BlockJukebox");	// 84 - jukebox
		blockClassMap.put(85, "net/minecraft/block/BlockFence");	// 85 - fence
		blockClassMap.put(86, "net/minecraft/block/BlockPumpkin");	// 86 - pumpkin
		blockClassMap.put(87, "net/minecraft/block/BlockNetherrack");	// 87 - netherrack
		blockClassMap.put(88, "net/minecraft/block/BlockSoulSand");	// 88 - soul_sand
		blockClassMap.put(89, "net/minecraft/block/BlockGlowstone");	// 89 - glowstone
		blockClassMap.put(90, "net/minecraft/block/BlockPortal");	// 90 - portal
		// 91 - lit_pumpkin already mapped (BlockPumpkin)
		blockClassMap.put(92, "net/minecraft/block/BlockCake");	// 92 - cake
		blockClassMap.put(93, "net/minecraft/block/BlockRedstoneRepeater");	// 93 - unpowered_repeater
		// 94 - powered_repeater already mapped (BlockRedstoneRepeater)
		blockClassMap.put(95, "net/minecraft/block/BlockStainedGlass");	// 95 - stained_glass
		blockClassMap.put(96, "net/minecraft/block/BlockTrapDoor");	// 96 - trapdoor
		blockClassMap.put(97, "net/minecraft/block/BlockSilverfish");	// 97 - monster_egg
		blockClassMap.put(98, "net/minecraft/block/BlockStoneBrick");	// 98 - stonebrick
		blockClassMap.put(99, "net/minecraft/block/BlockHugeMushroom");	// 99 - brown_mushroom_block
		// 100 - red_mushroom_block already mapped (BlockHugeMushroom)
		blockClassMap.put(101, "net/minecraft/block/BlockPane");	// 101 - iron_bars
		// 102 - glass_pane already mapped (BlockPane)
		blockClassMap.put(103, "net/minecraft/block/BlockMelon");	// 103 - melon_block
		blockClassMap.put(104, "net/minecraft/block/BlockStem");	// 104 - pumpkin_stem
		// 105 - melon_stem already mapped (BlockStem)
		blockClassMap.put(106, "net/minecraft/block/BlockVine");	// 106 - vine
		blockClassMap.put(107, "net/minecraft/block/BlockFenceGate");	// 107 - fence_gate
		// 108 - brick_stairs already mapped (BlockStairs)
		// 109 - stone_brick_stairs already mapped (BlockStairs)
		blockClassMap.put(110, "net/minecraft/block/BlockMycelium");	// 110 - mycelium
		blockClassMap.put(111, "net/minecraft/block/BlockLilyPad");	// 111 - waterlily
		blockClassMap.put(112, "net/minecraft/block/BlockNetherBrick");	// 112 - nether_brick
		// 113 - nether_brick_fence already mapped (BlockFence)
		// 114 - nether_brick_stairs already mapped (BlockStairs)
		blockClassMap.put(115, "net/minecraft/block/BlockNetherWart");	// 115 - nether_wart
		blockClassMap.put(116, "net/minecraft/block/BlockEnchantmentTable");	// 116 - enchanting_table
		blockClassMap.put(117, "net/minecraft/block/BlockBrewingStand");	// 117 - brewing_stand
		blockClassMap.put(118, "net/minecraft/block/BlockCauldron");	// 118 - cauldron
		blockClassMap.put(119, "net/minecraft/block/BlockEndPortal");	// 119 - end_portal
		blockClassMap.put(120, "net/minecraft/block/BlockEndPortalFrame");	// 120 - end_portal_frame
		// 121 - end_stone already mapped (Block)
		blockClassMap.put(122, "net/minecraft/block/BlockDragonEgg");	// 122 - dragon_egg
		blockClassMap.put(123, "net/minecraft/block/BlockRedstoneLight");	// 123 - redstone_lamp
		// 124 - lit_redstone_lamp already mapped (BlockRedstoneLight)
		blockClassMap.put(125, "net/minecraft/block/BlockDoubleWoodSlab");	// 125 - double_wooden_slab
		blockClassMap.put(126, "net/minecraft/block/BlockHalfWoodSlab");	// 126 - wooden_slab
		blockClassMap.put(127, "net/minecraft/block/BlockCocoa");	// 127 - cocoa
		// 128 - sandstone_stairs already mapped (BlockStairs)
		// 129 - emerald_ore already mapped (BlockOre)
		blockClassMap.put(130, "net/minecraft/block/BlockEnderChest");	// 130 - ender_chest
		blockClassMap.put(131, "net/minecraft/block/BlockTripWireHook");	// 131 - tripwire_hook
		blockClassMap.put(132, "net/minecraft/block/BlockTripWire");	// 132 - tripwire
		// 133 - emerald_block already mapped (BlockCompressed)
		// 134 - spruce_stairs already mapped (BlockStairs)
		// 135 - birch_stairs already mapped (BlockStairs)
		// 136 - jungle_stairs already mapped (BlockStairs)
		blockClassMap.put(137, "net/minecraft/block/BlockCommandBlock");	// 137 - command_block
		blockClassMap.put(138, "net/minecraft/block/BlockBeacon");	// 138 - beacon
		blockClassMap.put(139, "net/minecraft/block/BlockWall");	// 139 - cobblestone_wall
		blockClassMap.put(140, "net/minecraft/block/BlockFlowerPot");	// 140 - flower_pot
		blockClassMap.put(141, "net/minecraft/block/BlockCarrot");	// 141 - carrots
		blockClassMap.put(142, "net/minecraft/block/BlockPotato");	// 142 - potatoes
		blockClassMap.put(143, "net/minecraft/block/BlockButtonWood");	// 143 - wooden_button
		blockClassMap.put(144, "net/minecraft/block/BlockSkull");	// 144 - skull
		blockClassMap.put(145, "net/minecraft/block/BlockAnvil");	// 145 - anvil
		// 146 - trapped_chest already mapped (BlockChest)
		blockClassMap.put(147, "net/minecraft/block/BlockPressurePlateWeighted");	// 147 - light_weighted_pressure_plate
		// 148 - heavy_weighted_pressure_plate already mapped (BlockPressurePlateWeighted)
		blockClassMap.put(149, "net/minecraft/block/BlockRedstoneComparator");	// 149 - unpowered_comparator
		// 150 - powered_comparator already mapped (BlockRedstoneComparator)
		blockClassMap.put(151, "net/minecraft/block/BlockDaylightDetector");	// 151 - daylight_detector
		blockClassMap.put(152, "net/minecraft/block/BlockCompressedPowered");	// 152 - redstone_block
		// 153 - quartz_ore already mapped (BlockOre)
		blockClassMap.put(154, "net/minecraft/block/BlockHopper");	// 154 - hopper
		blockClassMap.put(155, "net/minecraft/block/BlockQuartz");	// 155 - quartz_block
		// 156 - quartz_stairs already mapped (BlockStairs)
		// 157 - activator_rail already mapped (BlockRailPowered)
		blockClassMap.put(158, "net/minecraft/block/BlockDropper");	// 158 - dropper
		// 159 - stained_hardened_clay already mapped (BlockColored)
		blockClassMap.put(160, "net/minecraft/block/BlockStainedGlassPane");	// 160 - stained_glass_pane
		blockClassMap.put(161, "net/minecraft/block/BlockNewLeaf");	// 161 - leaves2
		blockClassMap.put(162, "net/minecraft/block/BlockNewLog");	// 162 - log2
		// 163 - acacia_stairs already mapped (BlockStairs)
		// 164 - dark_oak_stairs already mapped (BlockStairs)
		blockClassMap.put(165, "net/minecraft/block/BlockSlime");	// 165 - slime
		blockClassMap.put(166, "net/minecraft/block/BlockBarrier");	// 166 - barrier
		// 167 - iron_trapdoor already mapped (BlockTrapDoor)
		blockClassMap.put(168, "net/minecraft/block/BlockPrismarine");	// 168 - prismarine
		blockClassMap.put(169, "net/minecraft/block/BlockSeaLantern");	// 169 - sea_lantern
		blockClassMap.put(170, "net/minecraft/block/BlockHay");	// 170 - hay_block
		blockClassMap.put(171, "net/minecraft/block/BlockCarpet");	// 171 - carpet
		blockClassMap.put(172, "net/minecraft/block/BlockHardenedClay");	// 172 - hardened_clay
		// 173 - coal_block already mapped (Block)
		blockClassMap.put(174, "net/minecraft/block/BlockPackedIce");	// 174 - packed_ice
		blockClassMap.put(175, "net/minecraft/block/BlockDoublePlant");	// 175 - double_plant
		blockClassMap.put(176, "net/minecraft/block/BlockBanner$BlockBannerStanding");	// 176 - standing_banner
		blockClassMap.put(177, "net/minecraft/block/BlockBanner$BlockBannerHanging");	// 177 - wall_banner
		// 178 - daylight_detector_inverted already mapped (BlockDaylightDetector)
		blockClassMap.put(179, "net/minecraft/block/BlockRedSandstone");	// 179 - red_sandstone
		// 180 - red_sandstone_stairs already mapped (BlockStairs)
		blockClassMap.put(181, "net/minecraft/block/BlockDoubleStoneSlabNew");	// 181 - double_stone_slab2
		blockClassMap.put(182, "net/minecraft/block/BlockHalfStoneSlabNew");	// 182 - stone_slab2
		// 183 - spruce_fence_gate already mapped (BlockFenceGate)
		// 184 - birch_fence_gate already mapped (BlockFenceGate)
		// 185 - jungle_fence_gate already mapped (BlockFenceGate)
		// 186 - dark_oak_fence_gate already mapped (BlockFenceGate)
		// 187 - acacia_fence_gate already mapped (BlockFenceGate)
		// 188 - spruce_fence already mapped (BlockFence)
		// 189 - birch_fence already mapped (BlockFence)
		// 190 - jungle_fence already mapped (BlockFence)
		// 191 - dark_oak_fence already mapped (BlockFence)
		// 192 - acacia_fence already mapped (BlockFence)
		// 193 - spruce_door already mapped (BlockDoor)
		// 194 - birch_door already mapped (BlockDoor)
		// 195 - jungle_door already mapped (BlockDoor)
		// 196 - acacia_door already mapped (BlockDoor)
		// 197 - dark_oak_door already mapped (BlockDoor)

		MethodCallVisitor registerBlocksMethodVisitor = new MethodCallVisitor(registerBlocks, false);
		for (MethodCall call : registerBlocksMethodVisitor) {
			MethodInsnNode mi = call.methodNode;
			
			if ((mi.name.equals(registerBlock1.name) && mi.desc.equals(registerBlock1.desc)) ||
					(mi.name.equals(registerBlock2.name) && mi.desc.equals(registerBlock2.desc))) {
				int blockId = (Integer)call.args[0];
				String blockClass = (String)call.args[2];
				if (blockClassMap.containsKey(blockId)) {
					addClassMapping(blockClassMap.get(blockId), blockClass);
					
					if (blockClassMap.get(blockId).equals("net/minecraft/block/BlockWorkbench")) {
						ClassNode workbench = getClassNode(blockClass);
						if (workbench != null) {
							List<TypeInsnNode> typeNodes = getAllInsnNodesOfType(workbench, TypeInsnNode.class);
							for (TypeInsnNode tn : typeNodes) {
								if (tn.desc.startsWith(blockClass + "$") && searchConstantPoolForStrings(tn.desc, "minecraft:crafting_table")) {
									addClassMapping("net/minecraft/block/BlockWorkbench$InterfaceCraftingTable", tn.desc);						
									break;
								}
							}			
						}
					}
				}
			}
		}

		return true;
	}
	
	
	public static int argCount(String methodDesc) {
		int count = 0;
		boolean complexType = false;
		for (int i = 1; i < methodDesc.length() && methodDesc.charAt(i) != ')'; i++) {
			if (complexType) {
				if (methodDesc.charAt(i) == ';') {
					complexType = false;
					count++;
				}
				continue;
			}
			if (methodDesc.charAt(i) == '[' || methodDesc.charAt(i) == 'L')
				complexType = true;
			else
				count++;
		}
		return count;
	}
	
	
	@Mapping(providesFields={
			"net/minecraft/block/BlockTNT EXPLODE Lnet/minecraft/block/properties/PropertyBool;"
			},
			providesMethods={
			"net/minecraft/block/Block onBlockAdded (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
			"net/minecraft/block/Block onBlockDestroyedByPlayer (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
			"net/minecraft/block/properties/PropertyBool create (Ljava/lang/String;)Lnet/minecraft/block/properties/PropertyBool;",
			"net/minecraft/block/Block onEntityCollidedWithBlock (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/Entity;)V",
			"net/minecraft/world/World setBlockToAir (Lnet/minecraft/util/BlockPos;)Z",
			"net/minecraft/block/BlockTNT explode (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/EntityLivingBase;)V",
			"net/minecraft/world/World spawnEntityInWorld (Lnet/minecraft/entity/Entity;)Z",
			"net/minecraft/world/World playSoundAtEntity (Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Sound;FF)V"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/block/BlockTNT",
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/block/properties/PropertyBool",
			"net/minecraft/entity/Entity",
			"net/minecraft/entity/EntityLivingBase",
			"net/minecraft/util/Sound"
			})
	public boolean processBlockTNTClass()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode tnt = getClassNodeFromMapping("net/minecraft/block/BlockTNT");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode propertyBool = getClassNodeFromMapping("net/minecraft/block/properties/PropertyBool");
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");
		ClassNode sound = getClassNodeFromMapping("net/minecraft/util/Sound");
		if (!MeddleUtil.notNull(block, tnt, world, blockPos, iBlockState, propertyBool, entity, entityLivingBase, sound)) return false;
		
		
		MethodNode clinit = getMethodNode(tnt, "--- <clinit> ()V");
		if (clinit != null) {
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(clinit, MethodInsnNode.class);
			for (Iterator<MethodInsnNode> it = nodes.iterator(); it.hasNext();) {
				MethodInsnNode mn = it.next();
				if (mn.owner.equals(propertyBool.name) && mn.desc.equals("(Ljava/lang/String;)L" + propertyBool.name + ";")) continue;
				it.remove();				
			}
			if (nodes.size() == 1) {
				addMethodMapping("net/minecraft/block/properties/PropertyBool create (Ljava/lang/String;)Lnet/minecraft/block/properties/PropertyBool;",
						propertyBool.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		List<FieldNode> fields = getMatchingFields(tnt, null, "L" + propertyBool.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/block/BlockTNT EXPLODE Lnet/minecraft/block/properties/PropertyBool;",
					tnt.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		// public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state)
		// public void onBlockDestroyedByPlayer(World worldIn, BlockPos pos, IBlockState state)
		List<MethodNode> methods = getMatchingMethods(tnt, null, assembleDescriptor("(", world, blockPos, iBlockState, ")V"));
		if (methods.size() == 2) {
			List<MethodNode> onDestroyed = new ArrayList<>();
			for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
				MethodNode method = it.next();
				// Match instructions for onBlockDestroyedByPlayer					
				if (matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.ACONST_NULL, Opcodes.INVOKEVIRTUAL, Opcodes.RETURN)) {
					onDestroyed.add(method);
					it.remove();
				}
			}
			
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/block/Block onBlockAdded (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
						block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
			if (onDestroyed.size() == 1) {
				addMethodMapping("net/minecraft/block/Block onBlockDestroyedByPlayer (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
						block.name + " " + onDestroyed.get(0).name + " " + onDestroyed.get(0).desc);
			}
		}
		
		
		// public void onEntityCollidedWithBlock(World worldIn, BlockPos pos, IBlockState state, Entity entityIn)
		methods = getMatchingMethods(tnt, null, assembleDescriptor("(", world, blockPos, iBlockState, entity, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onEntityCollidedWithBlock (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/Entity;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			for (Iterator<MethodInsnNode> it = nodes.iterator(); it.hasNext();) {
				MethodInsnNode mn = it.next();
				if (mn.owner.equals(world.name) && mn.desc.equals("(L" + blockPos.name + ";)Z")) continue;
				it.remove();
			}
			if (nodes.size() == 1) {
				addMethodMapping("net/minecraft/world/World setBlockToAir (Lnet/minecraft/util/BlockPos;)Z", 
						world.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public void explode(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase igniter)
		methods = getMatchingMethods(tnt, null, assembleDescriptor("(", world, blockPos, iBlockState, entityLivingBase, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/BlockTNT explode (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/EntityLivingBase;)V",
					tnt.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			List<MethodInsnNode> spawnEntityInWorld = new ArrayList<>();
			List<MethodInsnNode> playSoundAtEntity = new ArrayList<>();
			for (MethodInsnNode mn : nodes) {
				if (mn.owner.equals(world.name)) {
					if (mn.desc.equals("(L" + entity.name + ";)Z")) spawnEntityInWorld.add(mn);
					else if (mn.desc.equals("(L" + entity.name + ";L" + sound.name + ";FF)V")) playSoundAtEntity.add(mn);
				}
			}
			
			if (spawnEntityInWorld.size() == 1) {
				addMethodMapping("net/minecraft/world/World spawnEntityInWorld (Lnet/minecraft/entity/Entity;)Z",
						world.name + " " + spawnEntityInWorld.get(0).name + " " + spawnEntityInWorld.get(0).desc);
			}
			if (playSoundAtEntity.size() == 1) {
				addMethodMapping("net/minecraft/world/World playSoundAtEntity (Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Sound;FF)V",
						world.name + " " + playSoundAtEntity.get(0).name + " " + playSoundAtEntity.get(0).desc);
			}			
		}
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/block/IGrowable"
			},
			providesMethods={
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/block/BlockGrass",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/util/BlockPos"
			})
	public boolean processBlockGrassClass()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode grass = getClassNodeFromMapping("net/minecraft/block/BlockGrass");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		if (!MeddleUtil.notNull(block, grass, iBlockState, blockPos)) return false;
		
		ClassNode iGrowable = null;
		
		if (grass.interfaces.size() == 1) {
			iGrowable = getClassNode(grass.interfaces.get(0));
			if (iGrowable != null && iGrowable.methods.size() == 3) {
				addClassMapping("net/minecraft/block/IGrowable", iGrowable.name);
			}
			else iGrowable = null;
		}	
		
		return true;
	}
	
	
	
	@Mapping(providesMethods={
			"net/minecraft/block/IGrowable canGrow (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Z)Z",
			"net/minecraft/block/IGrowable canUseBonemeal (Lnet/minecraft/world/World;Ljava/util/Random;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z",
			"net/minecraft/block/IGrowable grow (Lnet/minecraft/world/World;Ljava/util/Random;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V"
			},
			depends={
			"net/minecraft/block/IGrowable",
			"net/minecraft/block/Block",
			"net/minecraft/block/BlockGrass",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/util/BlockPos",
			"net/minecraft/world/World"
			})
	public boolean processIGrowableClass()
	{
		ClassNode iGrowable = getClassNodeFromMapping("net/minecraft/block/IGrowable");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode grass = getClassNodeFromMapping("net/minecraft/block/BlockGrass");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		if (!MeddleUtil.notNull(iGrowable, block, grass, iBlockState, blockPos, world)) return false;
		
		// boolean canGrow(World worldIn, BlockPos pos, IBlockState state, boolean isClient);
		List<MethodNode> methods = getMatchingMethods(iGrowable, null, assembleDescriptor("(", world, blockPos, iBlockState, "Z)Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/IGrowable canGrow (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Z)Z",
					iGrowable.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// boolean canUseBonemeal(World worldIn, Random rand, BlockPos pos, IBlockState state);
		methods = getMatchingMethods(iGrowable, null, assembleDescriptor("(", world, "Ljava/util/Random;", blockPos, iBlockState, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/IGrowable canUseBonemeal (Lnet/minecraft/world/World;Ljava/util/Random;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z",
					iGrowable.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// void grow(World worldIn, Random rand, BlockPos pos, IBlockState state);
		methods = getMatchingMethods(iGrowable, null, assembleDescriptor("(", world, "Ljava/util/Random;", blockPos, iBlockState, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/IGrowable grow (Lnet/minecraft/world/World;Ljava/util/Random;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
					iGrowable.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	
	
	
	@Mapping(provides="net/minecraft/block/EnumRenderType",
			providesFields={
			"net/minecraft/block/material/Material air Lnet/minecraft/block/material/Material;"
			},
			providesMethods={
			"net/minecraft/block/Block getCollisionBoundingBox (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;",
			"net/minecraft/block/Block isOpaqueCube (Lnet/minecraft/block/state/IBlockState;)Z",
			"net/minecraft/block/Block canCollideCheck (Lnet/minecraft/block/state/IBlockState;Z)Z",
			"net/minecraft/block/Block isReplaceable (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Z",
			"net/minecraft/block/Block getRenderType ()Lnet/minecraft/block/EnumRenderType;"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/block/BlockAir",
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/block/material/Material",
			"net/minecraft/util/AxisAlignedBB"
			})
	public boolean processBlockAirClass()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode blockAir = getClassNodeFromMapping("net/minecraft/block/BlockAir");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode material = getClassNodeFromMapping("net/minecraft/block/material/Material");
		ClassNode aabb = getClassNodeFromMapping("net/minecraft/util/AxisAlignedBB");
		if (!MeddleUtil.notNull(block, blockAir, world, blockPos, iBlockState, material, aabb)) return false;
		
		List<FieldInsnNode> fieldNodes = getAllInsnNodesOfType(blockAir, FieldInsnNode.class);
		for (Iterator<FieldInsnNode> it = fieldNodes.iterator(); it.hasNext();) {
			if (!it.next().desc.equals("L" + material.name + ";")) it.remove();
		}
		if (fieldNodes.size() == 1) {
			addFieldMapping("net/minecraft/block/material/Material air Lnet/minecraft/block/material/Material;",
					material.name + " " + fieldNodes.get(0).name + " " + fieldNodes.get(0).desc);
		}
		
		
		// public AxisAlignedBB getCollisionBoundingBox(IBlockState state, World worldIn, BlockPos pos)
		List<MethodNode> methods = getMatchingMethods(blockAir, null, assembleDescriptor("(", iBlockState, world, blockPos, ")", aabb));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getCollisionBoundingBox (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean isOpaqueCube(IBlockState state)
		methods = getMatchingMethods(blockAir, null, assembleDescriptor("(", iBlockState, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block isOpaqueCube (Lnet/minecraft/block/state/IBlockState;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean canCollideCheck(IBlockState state, boolean hitIfLiquid)
		methods = getMatchingMethods(blockAir, null, assembleDescriptor("(", iBlockState, "Z)Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block canCollideCheck (Lnet/minecraft/block/state/IBlockState;Z)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean isReplaceable(World worldIn, BlockPos pos)
		methods = getMatchingMethods(blockAir, null, assembleDescriptor("(", world, blockPos, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block isReplaceable (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public EnumRenderType getRenderType()
		methods.clear();
		for (MethodNode method : blockAir.methods) {
			if (method.desc.startsWith("(L" + iBlockState.name + ";)L")) methods.add(method);
		}
		if (methods.size() == 1) {
			String renderType = Type.getMethodType(methods.get(0).desc).getReturnType().getClassName();
			if (searchConstantPoolForStrings(renderType, "INVISIBLE", "LIQUID")) {
				addClassMapping("net/minecraft/block/EnumRenderType", renderType);
				addMethodMapping("net/minecraft/block/Block getRenderType ()Lnet/minecraft/block/EnumRenderType;",
						block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		return true;
	}
	
	
	
	@Mapping(providesFields={
			"net/minecraft/block/EnumRenderType INVISIBLE Lnet/minecraft/block/EnumRenderType;",
			"net/minecraft/block/EnumRenderType LIQUID Lnet/minecraft/block/EnumRenderType;",
			"net/minecraft/block/EnumRenderType ENTITYBLOCK_ANIMATED Lnet/minecraft/block/EnumRenderType;",
			"net/minecraft/block/EnumRenderType MODEL Lnet/minecraft/block/EnumRenderType;"
			},
			depends="net/minecraft/block/EnumRenderType")
	public boolean processEnumRenderTypeClass()
	{
		ClassNode renderType = getClassNodeFromMapping("net/minecraft/block/EnumRenderType");
		if (renderType == null) return false;
		
		Map<String, FieldInsnNode> map = new HashMap<>();
		
		MethodNode clinit = getMethodNode(renderType, "--- <clinit> ()V");
		if (clinit == null) return false;
		
		// Extract LDC strings and the PUTSTATICs that follow them, into a map
		String lastString = null;
		for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			String string = getLdcString(insn);
			if (string != null) {
				lastString = string;
				continue;
			}			
			
			if (insn.getOpcode() != Opcodes.PUTSTATIC || lastString == null) continue;
			
			FieldInsnNode fn = (FieldInsnNode)insn;
			if (fn.owner.equals(renderType.name) && fn.desc.equals("L" + renderType.name + ";")) {
				if (lastString.equals("INVISIBLE")) {
					addFieldMapping("net/minecraft/block/EnumRenderType INVISIBLE Lnet/minecraft/block/EnumRenderType;",
							renderType.name + " " + fn.name + " " + fn.desc);
				}
				else if (lastString.equals("LIQUID")) {
					addFieldMapping("net/minecraft/block/EnumRenderType LIQUID Lnet/minecraft/block/EnumRenderType;",
							renderType.name + " " + fn.name + " " + fn.desc);
				}
				else if (lastString.equals("ENTITYBLOCK_ANIMATED")) {
					addFieldMapping("net/minecraft/block/EnumRenderType ENTITYBLOCK_ANIMATED Lnet/minecraft/block/EnumRenderType;",
							renderType.name + " " + fn.name + " " + fn.desc);
				}
				else if (lastString.equals("MODEL")) {
					addFieldMapping("net/minecraft/block/EnumRenderType MODEL Lnet/minecraft/block/EnumRenderType;",
							renderType.name + " " + fn.name + " " + fn.desc);
				}
				
				lastString = null;
			}
			 
		}
		
		
		
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/stats/StatList",
			"net/minecraft/util/ChatComponentTranslation",
			"net/minecraft/inventory/ContainerWorkbench"
			},
			depends={
			"net/minecraft/block/BlockWorkbench",
			"net/minecraft/block/BlockWorkbench$InterfaceCraftingTable",
			"net/minecraft/inventory/Container"
			})
	public boolean processBlockWorkbenchClass()
	{
		ClassNode workbench = getClassNodeFromMapping("net/minecraft/block/BlockWorkbench");
		ClassNode workbenchIface = getClassNodeFromMapping("net/minecraft/block/BlockWorkbench$InterfaceCraftingTable");
		ClassNode container = getClassNodeFromMapping("net/minecraft/inventory/Container");
		if (!MeddleUtil.notNull(workbench, workbenchIface, container)) return false;
		
		List<FieldInsnNode> fieldNodes = getAllInsnNodesOfType(workbench, FieldInsnNode.class);
		for (FieldInsnNode fn : fieldNodes) {
			if (fn.getOpcode() != Opcodes.GETSTATIC) continue;
			if (searchConstantPoolForStrings(fn.owner, "stat.leaveGame", "stat.playerKills")) {
				addClassMapping("net/minecraft/stats/StatList", fn.owner);						
			}
		}
		
		boolean foundTranslate = false;
		
		List<TypeInsnNode> typeNodes = getAllInsnNodesOfType(workbenchIface, TypeInsnNode.class);
		List<TypeInsnNode> containers = new ArrayList<>();
		for (TypeInsnNode tn : typeNodes) {			
			if (!foundTranslate && searchConstantPoolForStrings(tn.desc, "TranslatableComponent{key=\'")) {
				foundTranslate = true;
				addClassMapping("net/minecraft/util/ChatComponentTranslation", tn.desc);
			}
			else {
				ClassNode cn = getClassNode(tn.desc);
				if (cn != null && cn.superName != null && cn.superName.equals(container.name)) containers.add(tn);
			}
		}
		if (containers.size() == 1) {
			addClassMapping("net/minecraft/inventory/ContainerWorkbench", containers.get(0).desc);
		}
		
		return true;
	}
	
	
		
	@Mapping(provides={
			"net/minecraft/block/BlockLog",
			"net/minecraft/block/BlockRotatedPillar"
			},
			depends={
			"net/minecraft/block/BlockOldLog",
			"net/minecraft/block/Block"			
			})
	public boolean processBlockOldLog()
	{
		ClassNode oldLog = getClassNodeFromMapping("net/minecraft/block/BlockOldLog");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		if (oldLog == null || block == null) return false;
		
		if (searchConstantPoolForStrings(oldLog.superName, "axis")) {
			ClassNode blockLog = getClassNode(oldLog.superName);
			if (blockLog != null && searchConstantPoolForStrings(blockLog.superName, "axis")) {
				ClassNode rotatedPillar = getClassNode(blockLog.superName);
				if (rotatedPillar != null && rotatedPillar.superName.equals(block.name)) {
					addClassMapping("net/minecraft/block/BlockLog", blockLog.name);
					addClassMapping("net/minecraft/block/BlockRotatedPillar", rotatedPillar.name);
					return true;
				}				
			}
		}
		
		return false;
	}
	
	
	
	private boolean verifyBlockSoundTypeClass(String className)
	{
		ClassNode soundType = getClassNode(className);
		ClassNode sound = getClassNodeFromMapping("net/minecraft/util/Sound");
		if (soundType == null || sound == null) return false;
		
		List<MethodNode> methods = getMatchingMethods(soundType, "<init>", assembleDescriptor("(FF", sound, sound, sound, sound, sound, ")V"));
		if (methods.size() == 1) return true;
		
		return false;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/block/BlockSoundType"
			},
			providesFields={
			"net/minecraft/block/material/Material wood Lnet/minecraft/block/material/Material;",
			"net/minecraft/block/Block soundTypeWood Lnet/minecraft/block/Block$SoundType;",
			"net/minecraft/block/material/Material leaves Lnet/minecraft/block/material/Material;"
			},
			providesMethods={
			"net/minecraft/block/Block setHardness (F)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block setStepSound (Lnet/minecraft/block/Block$SoundType;)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block breakBlock (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
			"net/minecraft/world/World isAreaLoaded (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/BlockPos;)Z",
			"net/minecraft/util/BlockPos add (III)Lnet/minecraft/util/BlockPos;",
			"net/minecraft/util/BlockPos getAllInBox (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/BlockPos;)Ljava/lang/Iterable;"
			},
			depends={
			"net/minecraft/block/BlockLog",
			"net/minecraft/block/Block",
			"net/minecraft/block/material/Material",
			"net/minecraft/creativetab/CreativeTabs",
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/util/Sound"
			})
	public boolean processBlockLog()
	{
		ClassNode blockLog = getClassNodeFromMapping("net/minecraft/block/BlockLog");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode material = getClassNodeFromMapping("net/minecraft/block/material/Material");
		ClassNode creativeTabs = getClassNodeFromMapping("net/minecraft/creativetab/CreativeTabs");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");		
		if (!MeddleUtil.notNull(blockLog, block, material, creativeTabs, world, blockPos, iBlockState)) return false;
		
		List<MethodNode> methods = getMatchingMethods(blockLog, "<init>", "()V");
		if (methods.size() == 1) {
			List<FieldInsnNode> fieldNodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), FieldInsnNode.class);
			List<MethodInsnNode> methodNodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			
			String wood_name = null;
			int materialCount = 0;
			
			String soundType_name = null;
			String sound_name = null;
			int soundCount = 0;
			
			for (FieldInsnNode fn : fieldNodes) {
				if (!fn.desc.startsWith("L")) continue;
				String className = Type.getType(fn.desc).getClassName();
				
				if (className.equals(creativeTabs.name)) continue;
				else if (className.equals(material.name)) {
					wood_name = fn.name;
					materialCount++;
				}				
				else if (verifyBlockSoundTypeClass(className)) {
					if (soundType_name == null) {
						soundType_name = className;
						addClassMapping("net/minecraft/block/BlockSoundType", className);
					}					
					sound_name = fn.name;
					soundCount++;
				}
			}
			
			if (materialCount == 1) {
				addFieldMapping("net/minecraft/block/material/Material wood Lnet/minecraft/block/material/Material;", 
						material.name + " " + wood_name + " L" + material.name + ";");
			}
			if (soundCount == 1) {
				addFieldMapping("net/minecraft/block/Block soundTypeWood Lnet/minecraft/block/Block$SoundType;",
						block.name + " " + sound_name + " L" + soundType_name + ";");
			}
			
			
			String setHardness_name = null;
			int setHardness_count = 0;
			
			String setStepSound_name = null;
			int setStepSound_count = 0;
			
			for (MethodInsnNode mn : methodNodes) {
				if (!mn.owner.equals(blockLog.name)) continue;				
				if (mn.desc.equals("(F)L" + block.name + ";")) {
					setHardness_name = mn.name;
					setHardness_count++;					
				}
				else if (soundType_name != null && mn.desc.equals("(L" + soundType_name + ";)L" + block.name + ";")) {
					setStepSound_name = mn.name;
					setStepSound_count++;
				}
			}
			
			if (setHardness_count == 1) {
				addMethodMapping("net/minecraft/block/Block setHardness (F)Lnet/minecraft/block/Block;",
						block.name + " " + setHardness_name + " (F)L" + block.name + ";");
			}
			if (setStepSound_count == 1) {
				addMethodMapping("net/minecraft/block/Block setStepSound (Lnet/minecraft/block/Block$SoundType;)Lnet/minecraft/block/Block;",
						block.name + " " + setStepSound_name + " (L" + soundType_name + ";)L" + block.name + ";");
			}
			
		}
		
		
		// public void breakBlock(World worldIn, BlockPos pos, IBlockState state)
		methods = getMatchingMethods(blockLog, null, assembleDescriptor("(", world, blockPos, iBlockState, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block breakBlock (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);		
			
			// World: public boolean isAreaLoaded(BlockPos param0, BlockPos param1)
			List<MethodInsnNode> methodNodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			for (Iterator<MethodInsnNode> it = methodNodes.iterator(); it.hasNext();) {
				MethodInsnNode mn = it.next();
				if (!(mn.owner.equals(world.name) && mn.desc.equals("(L" + blockPos.name + ";L" + blockPos.name + ";)Z"))) it.remove();
			}
			if (methodNodes.size() == 1) {
				addMethodMapping("net/minecraft/world/World isAreaLoaded (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/BlockPos;)Z",
						world.name + " " + methodNodes.get(0).name + " " + methodNodes.get(0).desc);
			}
			
			// Blockpos: public BlockPos add(int param0, int param1, int param2) 
			methodNodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			for (Iterator<MethodInsnNode> it = methodNodes.iterator(); it.hasNext();) {
				MethodInsnNode mn = it.next();
				if (!(mn.owner.equals(blockPos.name) && mn.desc.equals("(III)L" + blockPos.name + ";"))) it.remove();
			}
			if (methodNodes.size() > 0) {
				String add_name = methodNodes.get(0).name;
				int add_matches = 0;				
				for (MethodInsnNode mn : methodNodes) {
					if (mn.name.equals(add_name)) add_matches++;
				}
			
				if (methodNodes.size() == add_matches) {					
					addMethodMapping("net/minecraft/util/BlockPos add (III)Lnet/minecraft/util/BlockPos;",
							blockPos.name + " " + methodNodes.get(0).name + " " + methodNodes.get(0).desc);
				}
			}
			
			
			// BlockPos: public static Iterable getAllInBox(BlockPos from, BlockPos to)
			methodNodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			for (Iterator<MethodInsnNode> it = methodNodes.iterator(); it.hasNext();) {
				MethodInsnNode mn = it.next();
				if (!(mn.owner.equals(blockPos.name) && mn.desc.equals(assembleDescriptor("(", blockPos, blockPos, ")Ljava/lang/Iterable;")))) it.remove();
			}
			if (methodNodes.size() == 1) {
				addMethodMapping("net/minecraft/util/BlockPos getAllInBox (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/BlockPos;)Ljava/lang/Iterable;",
						blockPos.name + " " + methodNodes.get(0).name + " " + methodNodes.get(0).desc);
			}
			
			
			List<FieldInsnNode> fieldNodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), FieldInsnNode.class);
			for (Iterator<FieldInsnNode> it = fieldNodes.iterator(); it.hasNext();) {
				FieldInsnNode fn = it.next();
				if (!fn.owner.equals(material.name)) it.remove();				
			}
			if (fieldNodes.size() == 1) {
				addFieldMapping("net/minecraft/block/material/Material leaves Lnet/minecraft/block/material/Material;", 
						material.name + " " + fieldNodes.get(0).name + " " + fieldNodes.get(0).desc);
			}
		}
		
		
		return true;
	}
	
	
	@Mapping(provides={			
			},
			providesFields={
			"net/minecraft/block/BlockPane NORTH Lnet/minecraft/block/properties/PropertyBool;",
			"net/minecraft/block/BlockPane SOUTH Lnet/minecraft/block/properties/PropertyBool;",
			"net/minecraft/block/BlockPane WEST Lnet/minecraft/block/properties/PropertyBool;",
			"net/minecraft/block/BlockPane EAST Lnet/minecraft/block/properties/PropertyBool;"
			},
			providesMethods={	
			},
			depends={
			"net/minecraft/block/BlockPane",
			"net/minecraft/block/properties/PropertyBool"
			})
	public boolean processBlockPaneClass()
	{
		ClassNode pane = getClassNodeFromMapping("net/minecraft/block/BlockPane");
		ClassNode propBool = getClassNodeFromMapping("net/minecraft/block/properties/PropertyBool");
		if (!MeddleUtil.notNull(pane, propBool)) return false;
		
		MethodNode clinit = getMethodNode(pane, "- <clinit> ()V");
		if (clinit == null) return false;
		String lastString = null;
		String north = null;
		String south = null;
		String east = null;
		String west = null;
		
		for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			String s = getLdcString(insn);
			if (s != null) { lastString = s; continue; }
			if (lastString == null) continue;
			
			FieldInsnNode fn = getNextInsnNodeOfType(insn, FieldInsnNode.class);
			if (fn == null || fn.getOpcode() != Opcodes.PUTSTATIC) continue;
			if (lastString != null && fn.owner.equals(pane.name) && fn.desc.equals("L" + propBool.name + ";")) {
				if (lastString.equals("north") && north == null) north = fn.name;
				else if (lastString.equals("south") && south == null) south = fn.name;
				else if (lastString.equals("east") && east == null) east = fn.name;
				else if (lastString.equals("west") && west == null) west = fn.name;				
				lastString = null;
				insn = fn;
			}		
		}
		
		if (MeddleUtil.notNull(north, south, east, west)) {
			addFieldMapping("net/minecraft/block/BlockPane NORTH Lnet/minecraft/block/properties/PropertyBool;", pane.name + " " + north + " L" + propBool.name + ";");
			addFieldMapping("net/minecraft/block/BlockPane SOUTH Lnet/minecraft/block/properties/PropertyBool;", pane.name + " " + south + " L" + propBool.name + ";");
			addFieldMapping("net/minecraft/block/BlockPane WEST Lnet/minecraft/block/properties/PropertyBool;", pane.name + " " + west + " L" + propBool.name + ";");
			addFieldMapping("net/minecraft/block/BlockPane EAST Lnet/minecraft/block/properties/PropertyBool;", pane.name + " " + east + " L" + propBool.name + ";");
		}
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/block/BlockLiquid"
			},
			providesMethods={			
			},			
			depends={
			"net/minecraft/block/BlockDynamicLiquid"
			})
	public boolean processBlockDynamicLiquidClass()
	{
		ClassNode dynamicLiquid = getClassNodeFromMapping("net/minecraft/block/BlockDynamicLiquid");
		if (!MeddleUtil.notNull(dynamicLiquid)) return false;
				
		String blockLiquid_name = dynamicLiquid.superName;
		if (searchConstantPoolForStrings(blockLiquid_name, "level")) {
			addClassMapping("net/minecraft/block/BlockLiquid", blockLiquid_name);
		}
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/block/properties/PropertyBool",
			"net/minecraft/block/BlockDoor$EnumHingePosition",
			"net/minecraft/block/BlockDoor$EnumDoorHalf"
			},
			providesFields={
			"net/minecraft/block/BlockDoor FACING Lnet/minecraft/block/properties/PropertyDirection;",
			"net/minecraft/block/BlockDoor HINGE Lnet/minecraft/block/properties/PropertyEnum;",
			"net/minecraft/block/BlockDoor HALF Lnet/minecraft/block/properties/PropertyEnum;",
			"net/minecraft/block/BlockDoor OPEN Lnet/minecraft/block/properties/PropertyBool;",
			"net/minecraft/block/BlockDoor POWERED Lnet/minecraft/block/properties/PropertyBool;"
			},
			providesMethods={},
			depends={
			"net/minecraft/block/BlockDoor",
			"net/minecraft/block/properties/PropertyDirection",
			"net/minecraft/block/properties/PropertyHelper",
			"net/minecraft/block/properties/PropertyEnum"
			})
	public boolean processBlockDoorClass()
	{
		ClassNode blockDoor = getClassNodeFromMapping("net/minecraft/block/BlockDoor");
		ClassNode propertyDirection = getClassNodeFromMapping("net/minecraft/block/properties/PropertyDirection");
		ClassNode propertyHelper = getClassNodeFromMapping("net/minecraft/block/properties/PropertyHelper");
		ClassNode propertyEnum = getClassNodeFromMapping("net/minecraft/block/properties/PropertyEnum");
		if (!MeddleUtil.notNull(blockDoor, propertyDirection, propertyHelper, propertyEnum)) return false;
		
		Set<String> classNames = new HashSet<>();
		
		List<FieldNode> directionFields = new ArrayList<>();
		List<FieldNode> enumFields = new ArrayList<>();
		List<FieldNode> unknownFields = new ArrayList<>();
		
		for (FieldNode field : blockDoor.fields) {
			Type t = Type.getType(field.desc);
			if (t.getSort() != Type.OBJECT) continue;
			if (t.getClassName().equals(propertyDirection.name)) directionFields.add(field);
			else if (t.getClassName().equals(propertyEnum.name)) enumFields.add(field);
			else {
				unknownFields.add(field);
				classNames.add(t.getClassName());
			}
		}
		
		if (directionFields.size() == 1) {
			addFieldMapping("net/minecraft/block/BlockDoor FACING Lnet/minecraft/block/properties/PropertyDirection;",
					blockDoor.name + " " + directionFields.get(0).name + " " + directionFields.get(0).desc);
		}
		
		ClassNode propertyBool = null;
		
		for (String className : classNames) {
			ClassNode cn = getClassNode(className);
			if (!cn.superName.equals(propertyHelper.name)) continue;
			List<MethodNode> methods = getMatchingMethods(cn, "<init>", "(Ljava/lang/String;)V");
			if (methods.size() == 1) {
				AbstractInsnNode insn = findNextOpcodeNum(methods.get(0).instructions.getFirst(), Opcodes.LDC);
				if (insn == null) continue;
				LdcInsnNode ldc = (LdcInsnNode)insn;
				if (!(ldc.cst instanceof Type)) continue;
				if (!((Type)ldc.cst).getClassName().equals("java.lang.Boolean")) continue;
				addClassMapping("net/minecraft/block/properties/PropertyBool", className);
				propertyBool = cn;
				break;
			}
		}
		
		
		// Find the PropertyEnum and PropertyBool fields
		List<MethodNode> methods = getMatchingMethods(blockDoor, "<clinit>", "()V");
		if (methods.size() == 1) {
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) 
			{				
				AbstractInsnNode[] nodes;
				
				// Try to find PropertyEnum fields				
				if ((nodes = getOpcodeSequenceArray(insn, Opcodes.LDC, Opcodes.LDC, Opcodes.INVOKESTATIC, Opcodes.PUTSTATIC)) != null) 
				{			
					LdcInsnNode ldc1 = (LdcInsnNode)nodes[0];
					LdcInsnNode ldc2 = (LdcInsnNode)nodes[1];
					MethodInsnNode mn = (MethodInsnNode)nodes[2];
					FieldInsnNode fn = (FieldInsnNode)nodes[3];
					
					if (!(ldc1.cst instanceof String)) continue;
					if (!(ldc2.cst instanceof Type)) continue;
					if (!mn.owner.equals(propertyEnum.name)) continue;
					if (!fn.owner.equals(blockDoor.name) || !fn.desc.equals("L" + propertyEnum.name + ";")) continue;
					
					if (((String)ldc1.cst).equals("hinge")) {
						String enumHingePosition = ((Type)ldc2.cst).getClassName();
						if (searchConstantPoolForStrings(enumHingePosition, "LEFT", "RIGHT")) {
							addClassMapping("net/minecraft/block/BlockDoor$EnumHingePosition", enumHingePosition);
							addFieldMapping("net/minecraft/block/BlockDoor HINGE Lnet/minecraft/block/properties/PropertyEnum;",
									blockDoor.name + " " + fn.name + " " + fn.desc);
						}
					}
					else if (((String)ldc1.cst).equals("half")) {
						String enumDoorHalf = ((Type)ldc2.cst).getClassName();
						if (searchConstantPoolForStrings(enumDoorHalf, "UPPER", "LOWER")) {
							addClassMapping("net/minecraft/block/BlockDoor$EnumDoorHalf", enumDoorHalf);
							addFieldMapping("net/minecraft/block/BlockDoor HALF Lnet/minecraft/block/properties/PropertyEnum;",
									blockDoor.name + " " + fn.name + " " + fn.desc);
						}
					}
					
					insn = nodes[3];					
					continue;
				}
				// Try to find PropertyBool fields
				else if ((nodes = getOpcodeSequenceArray(insn, Opcodes.LDC, Opcodes.INVOKESTATIC, Opcodes.PUTSTATIC)) != null) 
				{
					LdcInsnNode ldc = (LdcInsnNode)nodes[0];					
					MethodInsnNode mn = (MethodInsnNode)nodes[1];
					FieldInsnNode fn = (FieldInsnNode)nodes[2];
					
					if (!(ldc.cst instanceof String)) continue;
					if (!mn.owner.equals(propertyBool.name)) continue;
					if (!fn.owner.equals(blockDoor.name) || !fn.desc.equals("L" + propertyBool.name + ";")) continue;
					
					if (((String)ldc.cst).equals("open")) {
						addFieldMapping("net/minecraft/block/BlockDoor OPEN Lnet/minecraft/block/properties/PropertyBool;",
								blockDoor.name + " " + fn.name + " " + fn.desc);
					}
					else if (((String)ldc.cst).equals("powered")) {
						addFieldMapping("net/minecraft/block/BlockDoor POWERED Lnet/minecraft/block/properties/PropertyBool;",
								blockDoor.name + " " + fn.name + " " + fn.desc);
					}
					
					insn = nodes[2];					
					continue;
				}
			}
		}
		
		
		
		
		return true;
	}
			
	
	
	@Mapping(provides={
			"net/minecraft/util/IStringSerializable"
			},
			providesMethods={
			"net/minecraft/util/IStringSerializable getName ()Ljava/lang/String;"
			},
			providesFields={
			"net/minecraft/block/BlockDoor$EnumHingePosition LEFT Lnet/minecraft/block/BlockDoor$EnumHingePosition;",
			"net/minecraft/block/BlockDoor$EnumHingePosition RIGHT Lnet/minecraft/block/BlockDoor$EnumHingePosition;",
			"net/minecraft/block/BlockDoor$EnumDoorHalf UPPER Lnet/minecraft/block/BlockDoor$EnumDoorHalf;",
			"net/minecraft/block/BlockDoor$EnumDoorHalf LOWER Lnet/minecraft/block/BlockDoor$EnumDoorHalf;"			
			},
			depends={
			"net/minecraft/block/BlockDoor$EnumHingePosition",
			"net/minecraft/block/BlockDoor$EnumDoorHalf"
			})
	public boolean processBlockDoorProperties()
	{
		ClassNode hingePosition = getClassNodeFromMapping("net/minecraft/block/BlockDoor$EnumHingePosition");
		ClassNode doorHalf = getClassNodeFromMapping("net/minecraft/block/BlockDoor$EnumDoorHalf");
		if (hingePosition == null || doorHalf == null) return false;
		
		List<FieldNode> fields = getMatchingFields(hingePosition, null, "L" + hingePosition.name + ";");
		if (fields.size() == 2) {
			addFieldMapping("net/minecraft/block/BlockDoor$EnumHingePosition LEFT Lnet/minecraft/block/BlockDoor$EnumHingePosition;",
					hingePosition.name + " " + fields.get(0).name + " " + fields.get(0).desc);
			addFieldMapping("net/minecraft/block/BlockDoor$EnumHingePosition RIGHT Lnet/minecraft/block/BlockDoor$EnumHingePosition;",
					hingePosition.name + " " + fields.get(1).name + " " + fields.get(1).desc);
		}
		
		fields = getMatchingFields(doorHalf, null, "L" + doorHalf.name + ";");
		if (fields.size() == 2) {
			addFieldMapping("net/minecraft/block/BlockDoor$EnumDoorHalf UPPER Lnet/minecraft/block/BlockDoor$EnumDoorHalf;",
					doorHalf.name + " " + fields.get(0).name + " " + fields.get(0).desc);
			addFieldMapping("net/minecraft/block/BlockDoor$EnumDoorHalf LOWER Lnet/minecraft/block/BlockDoor$EnumDoorHalf;",
					doorHalf.name + " " + fields.get(1).name + " " + fields.get(1).desc);
		}
		
		
		// Find IStringSerializable while we're here
		if (hingePosition.interfaces.size() == 1 && doorHalf.interfaces.size() == 1 && hingePosition.interfaces.get(0).equals(doorHalf.interfaces.get(0))) {
			String iStringSerializable_name = hingePosition.interfaces.get(0);
			
			ClassNode iStringSerializable = getClassNode(iStringSerializable_name);
			if (iStringSerializable == null) return false;
			
			List<MethodNode> methods = getMatchingMethods(iStringSerializable, null, "()Ljava/lang/String;");
			if (methods.size() == 1) {
				addClassMapping("net/minecraft/util/IStringSerializable", iStringSerializable_name);
				addMethodMapping("net/minecraft/util/IStringSerializable getName ()Ljava/lang/String;", 
						iStringSerializable_name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		return true;
	}
	
	
	
	
	@Mapping(provides={
			"net/minecraft/block/BlockDirectional",
			"net/minecraft/block/properties/PropertyDirection"
			},
			providesFields={
			"net/minecraft/block/BlockDirectional FACING Lnet/minecraft/block/properties/PropertyDirection;"
			},
			depends={
			"net/minecraft/block/BlockBed", 
			"net/minecraft/block/BlockFenceGate", 
			"net/minecraft/block/Block",
			"net/minecraft/util/EnumFacing"
			})
	public boolean getBlockDirectionalClass()
	{
		ClassNode blockBed = getClassNodeFromMapping("net/minecraft/block/BlockBed");
		ClassNode blockFenceGate = getClassNodeFromMapping("net/minecraft/block/BlockFenceGate");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		if (!MeddleUtil.notNull(blockBed, blockFenceGate, block, enumFacing)) return false;
		
		// These should share this class as their parent
		if (!blockBed.superName.equals(blockFenceGate.superName)) return false;
		if (!searchConstantPoolForStrings(blockBed.superName, "facing")) return false;
				
		addClassMapping("net/minecraft/block/BlockDirectional", blockBed.superName);
		
		ClassNode blockDirectional = getClassNode(blockBed.superName);
		
		// Should only have one PropertyDirection field
		if (blockDirectional.fields.size() != 1) return false;
		if (!blockDirectional.fields.get(0).desc.startsWith("L")) return false;
		
		String propertyDirection_name = Type.getType(blockDirectional.fields.get(0).desc).getClassName();
		if (!searchConstantPoolForClasses(propertyDirection_name, enumFacing.name)) return false;
		addClassMapping("net/minecraft/block/properties/PropertyDirection", propertyDirection_name);
		
		addFieldMapping("net/minecraft/block/BlockDirectional FACING Lnet/minecraft/block/properties/PropertyDirection;", 
				blockDirectional.name + " " + blockDirectional.fields.get(0).name + " " + blockDirectional.fields.get(0).desc);
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/block/properties/PropertyEnum",
			"net/minecraft/block/properties/PropertyHelper"
			},
			providesMethods = {
			"net/minecraft/block/properties/PropertyDirection create (Ljava/lang/String;)Lnet/minecraft/block/properties/PropertyDirection;",
			"net/minecraft/block/properties/PropertyEnum create (Ljava/lang/String;Ljava/lang/Class;)Lnet/minecraft/block/properties/PropertyEnum;"
			},
			depends={
			"net/minecraft/block/properties/PropertyDirection",
			"net/minecraft/block/properties/IProperty"
			})
	public boolean processPropertyDirectionClass()
	{
		ClassNode propertyDirection = getClassNodeFromMapping("net/minecraft/block/properties/PropertyDirection");
		ClassNode iProperty = getClassNodeFromMapping("net/minecraft/block/properties/IProperty");
		if (!MeddleUtil.notNull(propertyDirection,iProperty)) return false;
		
		
		List<MethodNode> methods = getMatchingMethods(propertyDirection, null, "(Ljava/lang/String;)L" + propertyDirection.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/properties/PropertyDirection create (Ljava/lang/String;)Lnet/minecraft/block/properties/PropertyDirection;",
					propertyDirection.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		

		String propertyEnum_name = null; 
		if (searchConstantPoolForStrings(propertyDirection.superName, "Multiple values have the same name \'")) {
			propertyEnum_name = propertyDirection.superName;
			addClassMapping("net/minecraft/block/properties/PropertyEnum", propertyEnum_name);
		}
		else return false;
		
		ClassNode propertyEnum = getClassNode(propertyEnum_name);
		if (propertyEnum == null) return false;
		
		String propertyHelper_name = null;
		if (searchConstantPoolForStrings(propertyEnum.superName, "name", "clazz", "values")) {
			propertyHelper_name = propertyEnum.superName;
			addClassMapping("net/minecraft/block/properties/PropertyHelper", propertyHelper_name);
		}
		
		
		// public static PropertyEnum create(String param0, Class param1)
		methods = getMatchingMethods(propertyEnum, null, "(Ljava/lang/String;Ljava/lang/Class;)L" + propertyEnum.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/properties/PropertyEnum create (Ljava/lang/String;Ljava/lang/Class;)Lnet/minecraft/block/properties/PropertyEnum;",
					propertyEnum.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/world/IInteractionObject",
			"net/minecraft/block/BlockAnvil$Anvil",
			"net/minecraft/entity/player/InventoryPlayer",
			"net/minecraft/inventory/Container"
			},
			providesMethods={
			"net/minecraft/world/IInteractionObject getGuiID ()Ljava/lang/String;",
			"net/minecraft/world/IInteractionObject createContainer (Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/inventory/Container;",
			"net/minecraft/entity/player/EntityPlayer displayGui (Lnet/minecraft/world/IInteractionObject;)V"
			},
			dependsMethods={
			"net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z"
			},
			depends={
			"net/minecraft/block/BlockAnvil",
			"net/minecraft/entity/player/EntityPlayer"
			})
	public boolean getIInteractionObjectClass()
	{
		ClassNode blockAnvil = getClassNodeFromMapping("net/minecraft/block/BlockAnvil");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		if (blockAnvil == null || entityPlayer == null) return false;

		MethodNode onBlockActivated = getMethodNodeFromMapping(blockAnvil, "net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z");
		if (onBlockActivated == null) return false;

		String iInteractionObject = null;
		String anvilInteractionObject = null;
		
		for (AbstractInsnNode insn = onBlockActivated.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.NEW) continue;
			TypeInsnNode tn = (TypeInsnNode)insn;
			if (searchConstantPoolForStrings(tn.desc, "anvil")) {
				anvilInteractionObject = tn.desc;
				addClassMapping("net/minecraft/block/BlockAnvil$Anvil", tn.desc);
				break;
			}
		}
		
		if (anvilInteractionObject != null) {
			ClassNode anvil = getClassNode(anvilInteractionObject);
			if (anvil.interfaces.size() == 1) {
				iInteractionObject = anvil.interfaces.get(0);
				addClassMapping("net/minecraft/world/IInteractionObject", iInteractionObject);
			}
		}
		
		if (iInteractionObject != null) {
			ClassNode iiobject = getClassNode(iInteractionObject);			
			
			// String getGuiID();
			List<MethodNode> methods = getMatchingMethods(iiobject, null, "()Ljava/lang/String;");
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/world/IInteractionObject getGuiID ()Ljava/lang/String;",
						iInteractionObject + " " + methods.get(0).name + " " + methods.get(0).desc);
			}			
			
			// Container createContainer(InventoryPlayer, EntityPlayer);
			methods.clear();
			for (MethodNode method : iiobject.methods) {
				if (!checkMethodParameters(method, Type.OBJECT, Type.OBJECT)) continue;
				Type t = Type.getMethodType(method.desc);
				if (t.getReturnType().getSort() != Type.OBJECT) continue;
				Type[] args = t.getArgumentTypes();
				if (!args[1].getClassName().equals(entityPlayer.name)) continue;
				methods.add(method);
			}
			if (methods.size() == 1) {
				MethodNode m = methods.get(0);
				Type t = Type.getMethodType(m.desc);
				String inventoryPlayer = t.getArgumentTypes()[0].getClassName();
				String container = t.getReturnType().getClassName();
				
				boolean match1 = false;
				if (searchConstantPoolForStrings(inventoryPlayer, "Adding item to inventory", "container.inventory")) {
					match1 = true;
					addClassMapping("net/minecraft/entity/player/InventoryPlayer", inventoryPlayer);
				}
				
				boolean match2 = false;
				if (searchConstantPoolForStrings(container, "Listener already listening")) {
					match2 = true;
					addClassMapping("net/minecraft/inventory/Container", container);
				}
				
				if (match1 && match2) {
					addMethodMapping("net/minecraft/world/IInteractionObject createContainer (Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/inventory/Container;",
							iInteractionObject + " " + m.name + " " + m.desc);
				}
			}
			
			
			// EntityPlayer.displayGui(IInteractionObject)V
			for (AbstractInsnNode insn = onBlockActivated.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (mn.desc.equals("(L" + iInteractionObject + ";)V")) {
					addMethodMapping("net/minecraft/entity/player/EntityPlayer displayGui (Lnet/minecraft/world/IInteractionObject;)V",
							entityPlayer.name + " " + mn.name + " " + mn.desc);
				}
			}
		}
		
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/inventory/Slot",
			"net/minecraft/inventory/EnumContainerAction"
			},
			providesFields={
			"net/minecraft/inventory/Container inventorySlots Ljava/util/List;",
			"net/minecraft/inventory/Container crafters Ljava/util/List;"
			},
			providesMethods={
			"net/minecraft/inventory/Container addSlotToContainer (Lnet/minecraft/inventory/Slot;)Lnet/minecraft/inventory/Slot;",
			"net/minecraft/inventory/Container canInteractWith (Lnet/minecraft/entity/player/EntityPlayer;)Z",
			"net/minecraft/inventory/Container transferStackInSlot (Lnet/minecraft/entity/player/EntityPlayer;I)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/Container onContainerClosed (Lnet/minecraft/entity/player/EntityPlayer;)V",
			"net/minecraft/inventory/Container getSlot (I)Lnet/minecraft/inventory/Slot;",
			"net/minecraft/inventory/Container mergeItemStack (Lnet/minecraft/item/ItemStack;IIZ)Z",
			"net/minecraft/inventory/Container slotClick (IILnet/minecraft/inventory/EnumContainerAction;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/Container putStackInSlot (ILnet/minecraft/item/ItemStack;)V",
			"net/minecraft/inventory/Container onCraftMatrixChanged (Lnet/minecraft/inventory/IInventory;)V",
			"net/minecraft/inventory/Container detectAndSendChanges ()V",
			"net/minecraft/inventory/Container canAddItemToSlot (Lnet/minecraft/inventory/Slot;Lnet/minecraft/item/ItemStack;Z)Z"
			},
			depends={
			"net/minecraft/inventory/Container",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/item/ItemStack",
			"net/minecraft/inventory/IInventory",
			"net/minecraft/inventory/ICrafting"
			})
	public boolean processContainerClass()
	{
		ClassNode container = getClassNodeFromMapping("net/minecraft/inventory/Container");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode iInventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		ClassNode iCrafting = getClassNodeFromMapping("net/minecraft/inventory/ICrafting");
		if (!MeddleUtil.notNull(container, entityPlayer, itemStack, iInventory, iCrafting)) return false;
		
		List<MethodNode> methods = new ArrayList<>();
		
		String slotClass = null;
		
		// protected Slot addSlotToContainer(Slot slotIn)
		for (MethodNode method : container.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			Type returnType = t.getReturnType();			
			if (args.length != 1) continue;
			if (args[0].getSort() != Type.OBJECT || returnType.getSort() != Type.OBJECT) continue;			
			if (args[0].getClassName().equals(returnType.getClassName())) methods.add(method);
		}
		if (methods.size() == 1) {
			slotClass = Type.getMethodType(methods.get(0).desc).getReturnType().getClassName();
			// TODO - Better class detection
			addClassMapping("net/minecraft/inventory/Slot", slotClass);
			addMethodMapping("net/minecraft/inventory/Container addSlotToContainer (Lnet/minecraft/inventory/Slot;)Lnet/minecraft/inventory/Slot;",
					container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public abstract boolean canInteractWith(EntityPlayer playerIn);
		methods.clear();
		for (MethodNode method : container.methods) {
			if ((method.access & Opcodes.ACC_ABSTRACT) == 0) continue;
			if (!method.desc.equals("(L" + entityPlayer.name + ";)Z")) continue;
			methods.add(method);
		}
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Container canInteractWith (Lnet/minecraft/entity/player/EntityPlayer;)Z", 
					container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public ItemStack transferStackInSlot(EntityPlayer playerIn, int index)
		methods = getMatchingMethods(container, null, assembleDescriptor("(", entityPlayer, "I)", itemStack));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Container transferStackInSlot (Lnet/minecraft/entity/player/EntityPlayer;I)Lnet/minecraft/item/ItemStack;", 
					container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void onContainerClosed(EntityPlayer playerIn)
		methods = getMatchingMethods(container, null, assembleDescriptor("(", entityPlayer, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Container onContainerClosed (Lnet/minecraft/entity/player/EntityPlayer;)V", 
					container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// inventorySlots
		// public Slot getSlot(int slotId)
		methods = getMatchingMethods(container, null, "(I)L" + slotClass + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Container getSlot (I)Lnet/minecraft/inventory/Slot;", 
					container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.GETFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.desc.equals("Ljava/util/List;")) {
					addFieldMapping("net/minecraft/inventory/Container inventorySlots Ljava/util/List;", 
							container.name + " " + fn.name + " " + fn.desc);
				}
			}
		}
		
		
		// protected boolean mergeItemStack(ItemStack, int, int, boolean)
		methods = getMatchingMethods(container, null, "(L" + itemStack.name + ";IIZ)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Container mergeItemStack (Lnet/minecraft/item/ItemStack;IIZ)Z", 
					container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer playerIn)
		// As of 15w44a: public ItemStack slotClick(int slotId, int clickedButton, EnumContainerAction mode, EntityPlayer playerIn)
		methods.clear(); // = getMatchingMethods(container, null, "(IIIL" + entityPlayer.name + ";)L" + itemStack.name + ";");
		for (MethodNode method : container.methods) {
			if (method.desc.startsWith("(IIL") && method.desc.endsWith(";L" + entityPlayer.name + ";)L" + itemStack.name + ";")) {
				Type[] args = Type.getMethodType(method.desc).getArgumentTypes();
				if (args.length == 4) methods.add(method);
			}
		}
		if (methods.size() == 1) {
			String containerAction = Type.getMethodType(methods.get(0).desc).getArgumentTypes()[2].getClassName();
			if (searchConstantPoolForStrings(containerAction, "PICKUP", "QUICK_CRAFT")) {
				addClassMapping("net/minecraft/inventory/EnumContainerAction", containerAction);
				addMethodMapping("net/minecraft/inventory/Container slotClick (IILnet/minecraft/inventory/EnumContainerAction;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
						container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		
		// public void putStackInSlot(int p_75141_1_, ItemStack p_75141_2_)
		methods = getMatchingMethods(container, null, "(IL" + itemStack.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Container putStackInSlot (ILnet/minecraft/item/ItemStack;)V",
					container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		
		// public void onCraftMatrixChanged(IInventory inventoryIn)
		// public void detectAndSendChanges()
		methods = getMatchingMethods(container, null, "(L" + iInventory.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Container onCraftMatrixChanged (Lnet/minecraft/inventory/IInventory;)V",
					container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> list = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			if (list.size() == 1) {
				MethodInsnNode mn = list.get(0);
				if (mn.owner.equals(container.name) && mn.desc.equals("()V")) {
					addMethodMapping("net/minecraft/inventory/Container detectAndSendChanges ()V",
							container.name + " " + mn.name + " ()V");
				}
			}
		}
		
		
		// public static boolean canAddItemToSlot(Slot slotIn, ItemStack stack, boolean stackSizeMatters)
		methods = getMatchingMethods(container, null, "(L" + slotClass + ";L" + itemStack.name + ";Z)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Container canAddItemToSlot (Lnet/minecraft/inventory/Slot;Lnet/minecraft/item/ItemStack;Z)Z",
					container.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected List crafters
		methods = getMatchingMethods(container, null, "(L" + iCrafting.name + ";)V");
		if (methods.size() > 0) {
			Set<String> fieldNames = new HashSet<>();
			for (MethodNode method : methods) {
				List<FieldInsnNode> list = getAllInsnNodesOfType(method.instructions.getFirst(), FieldInsnNode.class);
				for (FieldInsnNode fn : list) {
					if (fn.owner.equals(container.name) && fn.desc.equals("Ljava/util/List;")) fieldNames.add(fn.name);
				}
			}
			if (fieldNames.size() == 1) {
				String fieldName = fieldNames.toArray(new String[1])[0];
				addFieldMapping("net/minecraft/inventory/Container crafters Ljava/util/List;",
						container.name + " " + fieldName + " Ljava/util/List;");
			}
		}
		
		return true;
	}
	
	
	
	@Mapping(providesMethods={
			"net/minecraft/inventory/ICrafting updateCraftingInventory (Lnet/minecraft/inventory/Container;Ljava/util/List;)V",
			"net/minecraft/inventory/ICrafting sendSlotContents (Lnet/minecraft/inventory/Container;ILnet/minecraft/item/ItemStack;)V",
			"net/minecraft/inventory/ICrafting sendProgressBarUpdate (Lnet/minecraft/inventory/Container;II)V",
			"net/minecraft/inventory/ICrafting func_175173_a (Lnet/minecraft/inventory/Container;Lnet/minecraft/inventory/IInventory;)V"
			},
			depends={
			"net/minecraft/inventory/ICrafting",
			"net/minecraft/inventory/Container",
			"net/minecraft/item/ItemStack",
			"net/minecraft/inventory/IInventory"
			})
	public boolean processICraftingClass()
	{
		ClassNode iCrafting = getClassNodeFromMapping("net/minecraft/inventory/ICrafting");
		ClassNode container = getClassNodeFromMapping("net/minecraft/inventory/Container");
		ClassNode itemStack  = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode iInventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		if (!MeddleUtil.notNull(iCrafting, container, itemStack, iInventory)) return false;
		
		// void updateCraftingInventory(Container containerToSend, List itemsList);
		List<MethodNode> methods = getMatchingMethods(iCrafting, null, "(L" + container.name + ";Ljava/util/List;)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/ICrafting updateCraftingInventory (Lnet/minecraft/inventory/Container;Ljava/util/List;)V",
					iCrafting.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// void sendSlotContents(Container containerToSend, int slotInd, ItemStack stack);
		methods = getMatchingMethods(iCrafting, null, "(L" + container.name + ";IL" + itemStack.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/ICrafting sendSlotContents (Lnet/minecraft/inventory/Container;ILnet/minecraft/item/ItemStack;)V",
					iCrafting.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// void sendProgressBarUpdate(Container containerIn, int varToUpdate, int newValue);
		methods = getMatchingMethods(iCrafting, null, "(L" + container.name + ";II)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/ICrafting sendProgressBarUpdate (Lnet/minecraft/inventory/Container;II)V",
					iCrafting.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// void func_175173_a(Container p_175173_1_, IInventory p_175173_2_);
		methods = getMatchingMethods(iCrafting, null, "(L" + container.name + ";L" + iInventory.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/ICrafting func_175173_a (Lnet/minecraft/inventory/Container;Lnet/minecraft/inventory/IInventory;)V",
					iCrafting.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}
	
	
	
	@Mapping(providesFields={
			"net/minecraft/inventory/Slot inventory Lnet/minecraft/inventory/IInventory;",
			"net/minecraft/inventory/Slot slotIndex I",
			"net/minecraft/inventory/Slot xDisplayPosition I",
			"net/minecraft/inventory/Slot yDisplayPosition I",
			"net/minecraft/inventory/Slot slotNumber I"
			},
			providesMethods={
			"net/minecraft/inventory/Slot onSlotChange (Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V",
			"net/minecraft/inventory/Slot onCrafting (Lnet/minecraft/item/ItemStack;I)V",
			"net/minecraft/inventory/Slot onCrafting (Lnet/minecraft/item/ItemStack;)V",
			"net/minecraft/inventory/Slot putStack (Lnet/minecraft/item/ItemStack;)V",
			"net/minecraft/inventory/Slot onPickupFromSlot (Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)V",
			"net/minecraft/inventory/Slot isItemValid (Lnet/minecraft/item/ItemStack;)Z",
			"net/minecraft/inventory/Slot getStack ()Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/Slot getHasStack ()Z",
			"net/minecraft/inventory/Slot onSlotChanged ()V",
			"net/minecraft/inventory/Slot getSlotStackLimit ()I",
			"net/minecraft/inventory/Slot getItemStackLimit (Lnet/minecraft/item/ItemStack;)I",
			"net/minecraft/inventory/Slot decrStackSize (I)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/Slot isHere (Lnet/minecraft/inventory/IInventory;I)Z",
			"net/minecraft/inventory/Slot canTakeStack (Lnet/minecraft/entity/player/EntityPlayer;)Z"
			},
			depends={
			"net/minecraft/inventory/Slot",
			"net/minecraft/item/ItemStack",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/inventory/IInventory"
			})
	public boolean processSlotClass()
	{
		ClassNode slot = getClassNodeFromMapping("net/minecraft/inventory/Slot");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode iInventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		if (!MeddleUtil.notNull(slot, itemStack, entityPlayer, iInventory)) return false;	
		
		

		List<MethodNode> methods = getMatchingMethods(slot, "<init>", assembleDescriptor("(",iInventory, "III)V"));
		String slotIndex = null;
		String xDisplayPosition = null;
		String yDisplayPosition = null;
		if (methods.size() == 1) {
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.PUTFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (!fn.owner.equals(slot.name)) continue;
				if (fn.desc.equals("L" + iInventory.name + ";")) {
					addFieldMapping("net/minecraft/inventory/Slot inventory Lnet/minecraft/inventory/IInventory;",
							slot.name + " " + fn.name + " " + fn.desc);
					continue;					
				}
				if (fn.desc.equals("I")) {
					if (slotIndex == null) { slotIndex = fn.name; continue; }
					if (xDisplayPosition == null) { xDisplayPosition = fn.name; continue; }
					if (yDisplayPosition == null) { yDisplayPosition = fn.name; continue; }
				}
			}
		}	
		
		if (MeddleUtil.notNull(slotIndex, xDisplayPosition, yDisplayPosition)) {
			List<FieldNode> fields = getMatchingFields(slot, null, "I");
			if (fields.size() == 4) {
				for (FieldNode field : fields) {
					if (field.name.equals(slotIndex))
						addFieldMapping("net/minecraft/inventory/Slot slotIndex I", slot.name + " " + field.name + " I");
					else if (field.name.equals(xDisplayPosition))
						addFieldMapping("net/minecraft/inventory/Slot xDisplayPosition I", slot.name + " " + field.name + " I");
					else if (field.name.equals(yDisplayPosition))
						addFieldMapping("net/minecraft/inventory/Slot yDisplayPosition I", slot.name + " " + field.name + " I");
					else addFieldMapping("net/minecraft/inventory/Slot slotNumber I", slot.name + " " + field.name + " I");
				}
			}
		}
		
		
		// public void onSlotChange(ItemStack, ItemStack)		
		methods = getMatchingMethods(slot, null, assembleDescriptor("(", itemStack, itemStack, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot onSlotChange (Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected void onCrafting(ItemStack, int)
		methods = getMatchingMethods(slot, null, assembleDescriptor("(",itemStack,"I)V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot onCrafting (Lnet/minecraft/item/ItemStack;I)V",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected void onCrafting(ItemStack)
		// public void putStack(ItemStack)
		methods = getMatchingMethods(slot, null, assembleDescriptor("(",itemStack,")V"));		
		if (methods.size() == 2) 
		{
			MethodNode onCrafting = null;
			MethodNode putStack = null;			
				
			boolean isReturn1 = getNextRealOpcode(methods.get(0).instructions.getFirst()).getOpcode() == Opcodes.RETURN;
			boolean isReturn2 = getNextRealOpcode(methods.get(1).instructions.getFirst()).getOpcode() == Opcodes.RETURN;
			
			if (isReturn1 && !isReturn2) {
				onCrafting = methods.get(0);
				putStack = methods.get(1);
			}
			else if (!isReturn1 && isReturn2) {
				onCrafting = methods.get(1);
				putStack = methods.get(0);
			}
			
			if (onCrafting != null && putStack != null) {
				addMethodMapping("net/minecraft/inventory/Slot onCrafting (Lnet/minecraft/item/ItemStack;)V",
						slot.name + " " + onCrafting.name + " " + onCrafting.desc);
				addMethodMapping("net/minecraft/inventory/Slot putStack (Lnet/minecraft/item/ItemStack;)V",
						slot.name + " " + putStack.name + " " + putStack.desc);
			}
			
			
		}
		
		// public void onPickupFromSlot(EntityPlayer, ItemStack)
		methods = getMatchingMethods(slot, null, assembleDescriptor("(",entityPlayer, itemStack,")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot onPickupFromSlot (Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)V",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean isItemValid(ItemStack)
		methods = getMatchingMethods(slot, null, assembleDescriptor("(",itemStack,")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot isItemValid (Lnet/minecraft/item/ItemStack;)Z",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public ItemStack getStack()
		String getStack = null;
		methods = getMatchingMethods(slot, null, assembleDescriptor("()",itemStack));
		if (methods.size() == 1) {
			getStack = slot.name + " " + methods.get(0).name + " " + methods.get(0).desc;
			addMethodMapping("net/minecraft/inventory/Slot getStack ()Lnet/minecraft/item/ItemStack;", getStack);
		}
		
		
		// public boolean getHasStack()
		methods = getMatchingMethods(slot, null, "()Z");
		// Do this since canBeHovered()Z is client-only and may or may not be here
		if (getStack != null && (methods.size() == 1 || methods.size() == 2)) {
			startloop:
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					// getHasStack() will call getStack
					if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
					MethodInsnNode mn = (MethodInsnNode)insn;					
					if (!getStack.equals(mn.owner + " " + mn.name + " " + mn.desc)) continue;
					
					addMethodMapping("net/minecraft/inventory/Slot getHasStack ()Z", slot.name + " " + method.name + " " + method.desc);
					break startloop;
				}
			}
		}
		
		
		// public void onSlotChanged()
		methods = getMatchingMethods(slot, null, "()V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot onSlotChanged ()V",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public int getSlotStackLimit()
		methods = getMatchingMethods(slot, null, "()I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot getSlotStackLimit ()I",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public int getItemStackLimit(ItemStack)
		methods = getMatchingMethods(slot, null, assembleDescriptor("(", itemStack, ")I"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot getItemStackLimit (Lnet/minecraft/item/ItemStack;)I",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public ItemStack decrStackSize(int)
		methods = getMatchingMethods(slot, null, assembleDescriptor("(I)", itemStack));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot decrStackSize (I)Lnet/minecraft/item/ItemStack;",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean isHere(IInventory, int)
		methods = getMatchingMethods(slot, null, assembleDescriptor("(", iInventory, "I)Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot isHere (Lnet/minecraft/inventory/IInventory;I)Z",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean canTakeStack(EntityPlayer)
		methods = getMatchingMethods(slot, null, assembleDescriptor("(", entityPlayer, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/Slot canTakeStack (Lnet/minecraft/entity/player/EntityPlayer;)Z",
					slot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		
		/*		
		TODO:
		
		@ClientOnly
		public String getSlotTexture()
		@ClientOnly
		public boolean canBeHovered()		
		 */
		
		return true;
	}
	
	
	
	
	@Mapping(provides={
			"net/minecraft/item/ItemSword",
			"net/minecraft/item/ItemSoup",
			"net/minecraft/item/ItemBanner",
			"net/minecraft/item/ItemDye",
			"net/minecraft/item/ItemBow",
			"net/minecraft/item/ItemRecord",
			"net/minecraft/item/ItemAxe",
			"net/minecraft/item/ItemTool",
			"net/minecraft/item/ItemPickaxe"
			},
			providesMethods={
			"net/minecraft/item/Item registerItems ()V"
			},
			depends="net/minecraft/item/Item")
	public boolean discoverItems()
	{
		ClassNode itemClass = getClassNodeFromMapping("net/minecraft/item/Item");
		if (itemClass == null) return false;

		// Find: public static void registerItems()
		List<MethodNode> methods = removeMethodsWithoutFlags(getMatchingMethods(itemClass, null,  "()V"), Opcodes.ACC_STATIC);
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			if (it.next().name.contains("<")) it.remove();
		}
		if (methods.size() != 1) return false;
		MethodNode registerItemsMethod = methods.get(0);

		addMethodMapping("net/minecraft/item/Item registerItems ()V", itemClass.name + " " + registerItemsMethod.name + " " + registerItemsMethod.desc);

		Map<String, String> itemClassMap = new HashMap<String, String>();

		// Extract a list of classes from item initializations.
		for (AbstractInsnNode insn = registerItemsMethod.instructions.getFirst(); insn != null; insn = insn.getNext())
		{
			String name = getLdcString(insn);
			if (name == null || insn.getNext().getOpcode() != Opcodes.NEW) continue;
			insn = insn.getNext();
			TypeInsnNode newNode = (TypeInsnNode)insn;
			if (!newNode.desc.equals(itemClass.name)) itemClassMap.put(name, newNode.desc);
		}

		
		
		String className;

		className = itemClassMap.get("iron_sword");
		if (className != null && searchConstantPoolForStrings(className, "Weapon modifier")) {
			addClassMapping("net/minecraft/item/ItemSword", className);
		}

		className = itemClassMap.get("mushroom_stew");
		if (className != null && className.equals(itemClassMap.get("rabbit_stew"))) {
			addClassMapping("net/minecraft/item/ItemSoup", className);
		}

		className = itemClassMap.get("banner");
		if (className != null && searchConstantPoolForStrings(className, "item.banner.")) {
			addClassMapping("net/minecraft/item/ItemBanner", className);
		}
		
		className = itemClassMap.get("dye");
		if (className != null && searchConstantPoolForStrings(className, ".")) { // TODO - better detection method
			addClassMapping("net/minecraft/item/ItemDye", className);
		}
		
		// catch match to "random.bow" anymore with 15w43a
		className = itemClassMap.get("bow");
		if (className != null && searchConstantPoolForStrings(className, "pull", "pulling")) {
			addClassMapping("net/minecraft/item/ItemBow", className);
		}
		
		className = itemClassMap.get("record_13");
		if (className != null && searchConstantPoolForStrings(className, "item.record.")) {
			addClassMapping("net/minecraft/item/ItemRecord", className);
		}
		
		String itemTool = null;
		
		className = itemClassMap.get("iron_axe");
		if (className != null) {			
			ClassNode axe = getClassNode(className);
			if (axe != null) {
				ClassNode tool = getClassNode(axe.superName);
				if (tool != null) {
					if (tool.superName.equals(itemClass.name) && searchConstantPoolForStrings(tool.name, "Tool modifier")) {
						addClassMapping("net/minecraft/item/ItemAxe", axe.name);
						addClassMapping("net/minecraft/item/ItemTool", tool.name);
						itemTool = tool.name;
					}
				}
			}
		}		
		
		className = itemClassMap.get("iron_pickaxe");
		if (className != null && itemTool != null) {
			ClassNode pickaxe = getClassNode(className);
			if (pickaxe != null && pickaxe.superName != null && pickaxe.superName.equals(itemTool)) {
				addClassMapping("net/minecraft/item/ItemPickaxe", pickaxe.name);
			}
		}
		
		className = itemClassMap.get("iron_pickaxe");
		if (className != null) {			
			ClassNode axe = getClassNode(className);
			if (axe != null) {
				ClassNode tool = getClassNode(axe.superName);
				if (tool != null) {
					if (tool.superName.equals(itemClass.name) && searchConstantPoolForStrings(tool.name, "Tool modifier")) {
						addClassMapping("net/minecraft/item/ItemPickaxe", axe.name);
					}
				}
			}
		}
		
		

		return true;
	}

	
	
	@Mapping(providesFields={
			"net/minecraft/item/ItemPickaxe EFFECTIVE_ON Ljava/util/Set;",
			"net/minecraft/item/ItemTool efficiencyOnProperMaterial F"
			},
			providesMethods={
			"net/minecraft/item/Item canHarvestBlock (Lnet/minecraft/block/Block;)Z",
			"net/minecraft/item/ItemTool getStrVsBlock (Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/state/IBlockState;)F"
			},
			depends={
			"net/minecraft/item/ItemPickaxe",
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemStack",
			"net/minecraft/block/Block",
			"net/minecraft/item/ItemTool",
			"net/minecraft/block/state/IBlockState"
			})
	public boolean processItemPickaxeClass()
	{
		ClassNode itemPickaxe = getClassNodeFromMapping("net/minecraft/item/ItemPickaxe");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode itemTool = getClassNodeFromMapping("net/minecraft/item/ItemTool");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		if (!MeddleUtil.notNull(itemPickaxe, item, itemStack, block, itemTool, iBlockState)) return false;
		
		List<FieldNode> fields = getMatchingFields(itemPickaxe, null, "Ljava/util/Set;");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/item/ItemPickaxe EFFECTIVE_ON Ljava/util/Set;", 
					itemPickaxe.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		// public boolean canHarvestBlock(Block blockIn)
		List<MethodNode> methods = getMatchingMethods(itemPickaxe, null, "(L" + block.name + ";)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item canHarvestBlock (Lnet/minecraft/block/Block;)Z",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			// TODO: Get Item.ToolMaterial toolMaterial
		}
		
		
		// public float getStrVsBlock(ItemStack stack, Block block)
		methods = getMatchingMethods(itemPickaxe, null, "(L" + itemStack.name + ";L" + iBlockState.name + ";)F");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemTool getStrVsBlock (Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/state/IBlockState;)F",
					itemTool.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			for (Iterator<FieldInsnNode> it = nodes.iterator(); it.hasNext();) {
				FieldInsnNode fn = it.next();
				if (fn.owner.equals(itemPickaxe.name) && fn.desc.equals("F")) continue;
				else it.remove();
			}
			if (nodes.size() == 1) {
				if (getFieldNode(itemTool, "--- " + nodes.get(0).name + " F") != null) {
					addFieldMapping("net/minecraft/item/ItemTool efficiencyOnProperMaterial F", 
							itemTool.name + " " + nodes.get(0).name + " F");
				}
			}
		}
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/util/Sound"
			},
			depends={
			"net/minecraft/item/ItemRecord"
			})
	public boolean processItemRecordClass()
	{
		ClassNode itemRecord = getClassNodeFromMapping("net/minecraft/item/ItemRecord");
		if (itemRecord == null) return false;
		
		List<MethodNode> methods = new ArrayList<>();
		for (MethodNode method : itemRecord.methods) {
			if (method.name.equals("<init>") && method.desc.startsWith("(Ljava/lang/String;L")) methods.add(method);
		}
		
		if (methods.size() == 1) {
			String soundClass = Type.getMethodType(methods.get(0).desc).getArgumentTypes()[1].getClassName();
			if (searchConstantPoolForStrings(soundClass, "ambient.cave", "weather.rain")) {
				addClassMapping("net/minecraft/util/Sound", soundClass);
			}
		}		
		
		return true;
	}
	
	
	
	@Mapping(providesMethods={
			"net/minecraft/item/Item onPlayerStoppedUsing (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;I)V",
			"net/minecraft/item/Item getMaxItemUseDuration (Lnet/minecraft/item/ItemStack;)I",
			"net/minecraft/item/Item getItemEnchantability ()I",
			"net/minecraft/item/Item getItemUseAction (Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/EnumAction;",
			"net/minecraft/entity/EntityLivingBase setItemInUse (Lnet/minecraft/util/MainOrOffHand;)V"
			},
			provides={
			"net/minecraft/item/EnumAction",
			"net/minecraft/init/Sounds"
			},
			dependsMethods={
			"net/minecraft/item/Item onItemRightClick (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;)Lnet/minecraft/util/ObjectActionHolder;",
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemBow",
			"net/minecraft/item/ItemStack",
			"net/minecraft/world/World",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/entity/EntityLivingBase",
			"net/minecraft/util/MainOrOffHand",
			"net/minecraft/util/Sound"
			})
	public boolean processItemBowClass()
	{		
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemBow = getClassNodeFromMapping("net/minecraft/item/ItemBow");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");
		ClassNode hand = getClassNodeFromMapping("net/minecraft/util/MainOrOffHand");
		ClassNode sound = getClassNodeFromMapping("net/minecraft/util/Sound");
		if (!MeddleUtil.notNull(item, itemBow, itemStack, world, entityPlayer, entityLivingBase, hand, sound)) return false;
		
		// public void onPlayerStoppedUsing(ItemStack stack, World worldIn, EntityLivingBase playerIn, int timeLeft)
		List<MethodNode> methods = getMatchingMethods(itemBow, null, assembleDescriptor("(", itemStack, world, entityLivingBase, "I)V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item onPlayerStoppedUsing (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;I)V",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), FieldInsnNode.class);
			for (FieldInsnNode fn : nodes) {				
				if (fn.desc.equals("L" + sound.name + ";")) {
					if (searchConstantPoolForStrings(fn.owner, "Accessed Sounds before Bootstrap!")) {
						addClassMapping("net/minecraft/init/Sounds", fn.owner);
					}
				}
			}
		}
		
		// public int getMaxItemUseDuration(ItemStack param0)
		methods = getMatchingMethods(itemBow, null, assembleDescriptor("(", itemStack, ")I"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item getMaxItemUseDuration (Lnet/minecraft/item/ItemStack;)I",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public EnumAction getItemUseAction(ItemStack param0)
		methods.clear();
		for (MethodNode method : itemBow.methods) {
			if (method.desc.startsWith("(L" + itemStack.name + ";)L")) methods.add(method);
		}
		if (methods.size() == 1) {
			MethodNode method = methods.get(0);
			Type t = Type.getMethodType(method.desc).getReturnType();
			if (searchConstantPoolForStrings(t.getClassName(), "EAT", "DRINK", "BLOCK")) {
				addClassMapping("net/minecraft/item/EnumAction", t.getClassName());
				addMethodMapping("net/minecraft/item/Item getItemUseAction (Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/EnumAction;",
						item.name + " " + method.name + " " + method.desc);
			}
		}
		
		// public int getItemEnchantability()
		methods = getMatchingMethods(itemBow, null, "()I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item getItemEnchantability ()I",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		MethodNode method = getMethodNodeFromMapping(itemBow, "net/minecraft/item/Item onItemRightClick (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;)Lnet/minecraft/util/ObjectActionHolder;");
		if (method != null) {
			List<MethodInsnNode> methodNodes = new ArrayList<>();
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (mn.owner.equals(entityPlayer.name) && mn.desc.equals("(L" + hand.name + ";)V")) methodNodes.add(mn);
			}
			if (methodNodes.size() == 1) {
				addMethodMapping("net/minecraft/entity/EntityLivingBase setItemInUse (Lnet/minecraft/util/MainOrOffHand;)V",
						entityLivingBase.name + " " + methodNodes.get(0).name + " " + methodNodes.get(0).desc);
			}
		}
		
		return true;
	}
	
	
	@Mapping(providesFields={
			"net/minecraft/item/EnumAction NONE Lnet/minecraft/item/EnumAction;",
			"net/minecraft/item/EnumAction EAT Lnet/minecraft/item/EnumAction;",
			"net/minecraft/item/EnumAction DRINK Lnet/minecraft/item/EnumAction;",
			"net/minecraft/item/EnumAction BLOCK Lnet/minecraft/item/EnumAction;",
			"net/minecraft/item/EnumAction BOW Lnet/minecraft/item/EnumAction;"
			},
			depends={
			"net/minecraft/item/EnumAction"
			})
	public boolean processEnumActionClass()
	{
		ClassNode enumAction = getClassNodeFromMapping("net/minecraft/item/EnumAction");
		if (enumAction == null) return false;
		
		List<String> strings = new ArrayList<>();
		List<String> fields = new ArrayList<>();
		
		MethodNode init = getMethodNode(enumAction, "--- <clinit> ()V");
		if (init == null) return false;
		for (AbstractInsnNode insn = init.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			String s = getLdcString(insn);
			if (s != null) { strings.add(s); continue; }
			
			if (insn.getOpcode() == Opcodes.PUTSTATIC) {
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.owner.equals(enumAction.name) && fn.desc.equals("L" + enumAction.name + ";")) fields.add(fn.name);
			}
		}		

		if (strings.size() == 5 && fields.size() == 5) {
			for (int n = 0; n < 5; n++) {
				addFieldMapping("net/minecraft/item/EnumAction " + strings.get(n) + " Lnet/minecraft/item/EnumAction;", enumAction.name + " " + fields.get(n) + " L" + enumAction.name + ";");
			}
		}
		
		return true;
	}
	
	
	
	@Mapping(providesMethods={
			"net/minecraft/item/Item setHasSubtypes (Z)Lnet/minecraft/item/Item;",
			"net/minecraft/item/ItemStack getMetadata ()I"
			},
			providesFields={
			},
			dependsMethods={
			"net/minecraft/item/Item getUnlocalizedName (Lnet/minecraft/item/ItemStack;)Ljava/lang/String;"
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemDye",
			"net/minecraft/item/ItemStack"
			})
	public boolean processItemDyeClass()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemDye = getClassNodeFromMapping("net/minecraft/item/ItemDye");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		if (!MeddleUtil.notNull(item, itemDye, itemStack)) return false;
		
		// protected Item setHasSubtypes(boolean hasSubtypes)
		List<MethodNode> methods = getMatchingMethods(itemDye, "<init>", "()V");
		if (methods.size() == 1) {
			List<MethodInsnNode> mnodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			List<MethodInsnNode> foundnodes = new ArrayList<>();
			for (MethodInsnNode mn : mnodes) {
				if (!mn.owner.equals(itemDye.name)) continue;
				if (mn.desc.equals("(Z)L" + item.name + ";")) foundnodes.add(mn);
			}
			if (foundnodes.size() == 1) {
				addMethodMapping("net/minecraft/item/Item setHasSubtypes (Z)Lnet/minecraft/item/Item;",
						item.name + " " + foundnodes.get(0).name + " " + foundnodes.get(0).desc);
			}
		}
		
		MethodNode method = getMethodNodeFromMapping(itemDye, "net/minecraft/item/Item getUnlocalizedName (Lnet/minecraft/item/ItemStack;)Ljava/lang/String;");
		if (method != null) {
			List<MethodInsnNode> mnodes = getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (Iterator<MethodInsnNode> iterator = mnodes.iterator(); iterator.hasNext();) {
				MethodInsnNode mn = iterator.next();
				if (!(mn.owner.equals(itemStack.name) && mn.desc.equals("()I"))) iterator.remove();
			}
			if (mnodes.size() == 1) {
				addMethodMapping("net/minecraft/item/ItemStack getMetadata ()I", itemStack.name + " " + mnodes.get(0).name + " ()I");
			}
		}
		
		return true;
	}
	
	

	@Mapping(providesMethods={
			"net/minecraft/item/Item setMaxDamage (I)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item getMaxDamage ()I"
	},
	providesFields={
			"net/minecraft/item/Item maxDamage I"
	},
	depends={
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemBanner"
	})
	public boolean getMaxDamageStuff()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode banner = getClassNodeFromMapping("net/minecraft/item/ItemBanner");
		if (item == null || banner == null) return false;

		List<MethodNode> methods = getMatchingMethods(banner, "<init>", "()V");
		if (methods.size() != 1) return false;

		int count = 0;
		String setMaxDamageName = null;
		String setMaxDamageDesc = "(I)L" + item.name + ";";

		// Locate Item.setMaxDamage used in ItemBanner's constructor
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;
			if (!mn.desc.equals(setMaxDamageDesc)) continue;
			setMaxDamageName = mn.name;
			count++;
		}

		if (count != 1) return false;
		addMethodMapping("net/minecraft/item/Item setMaxDamage (I)Lnet/minecraft/item/Item;",
				item.name + " " + setMaxDamageName + " " + setMaxDamageDesc);


		String maxDamageField = null;

		methods = getMatchingMethods(item, setMaxDamageName, setMaxDamageDesc);
		if (methods.size() != 1) return false;

		// Get Item.maxDamage field from Item.setMaxDamage(I)
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() < 0) continue;
			if (insn.getOpcode() != Opcodes.ALOAD) return false;
			insn = insn.getNext();
			if (insn.getOpcode() != Opcodes.ILOAD) return false;
			insn = insn.getNext();
			if (insn.getOpcode() != Opcodes.PUTFIELD) return false;

			FieldInsnNode fn = (FieldInsnNode)insn;
			if (!fn.desc.equals("I")) return false;
			maxDamageField = fn.name;
			break;
		}
		if (maxDamageField == null) return false;

		addFieldMapping("net/minecraft/item/Item maxDamage I", item.name + " " + maxDamageField + " I");


		// Find Item.getMaxDamage()
		methods = getMatchingMethods(item, null, "()I");
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() < 0) continue;
				if (insn.getOpcode() != Opcodes.ALOAD) return false;
				insn = insn.getNext();
				if (insn.getOpcode() != Opcodes.GETFIELD) return false;

				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.name.equals(maxDamageField)) {
					addMethodMapping("net/minecraft/item/Item getMaxDamage ()I", item.name + " " + method.name + " " + method.desc);
					return true;
				}

				break;
			}
		}


		return false;
	}



	



	@Mapping(provides={
			"net/minecraft/util/Vec3",
			"net/minecraft/block/material/Material",
			"net/minecraft/block/material/MapColor",
			"net/minecraft/block/state/BlockState",
			"net/minecraft/util/RegistryNamespacedDefaultedByKey",
			"net/minecraft/util/ObjectIntIdentityMap"
			},
			providesFields={
			"net/minecraft/block/Block unlocalizedName Ljava/lang/String;",
			"net/minecraft/block/Block blockRegistry Lnet/minecraft/util/RegistryNamespacedDefaultedByKey;",
			"net/minecraft/block/Block BLOCK_STATE_IDS Lnet/minecraft/util/ObjectIntIdentityMap;",
			"net/minecraft/block/Block defaultBlockState Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/Block translucent Z",
			"net/minecraft/block/Block useNeighborBrightness Z"
			},
			providesMethods={
			"net/minecraft/block/Block getIdFromBlock (Lnet/minecraft/block/Block;)I",
			"net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z",
			"net/minecraft/block/Block collisionRayTrace (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;",
			"net/minecraft/block/Block registerBlock (ILnet/minecraft/util/ResourceLocation;Lnet/minecraft/block/Block;)V",
			"net/minecraft/block/Block registerBlock (ILjava/lang/String;Lnet/minecraft/block/Block;)V",
			"net/minecraft/block/Block getStateFromMeta (I)Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/Block getStateById (I)Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/Block getMetaFromState (Lnet/minecraft/block/state/IBlockState;)I",
			"net/minecraft/block/Block setDefaultState (Lnet/minecraft/block/state/IBlockState;)V",
			"net/minecraft/block/Block getDefaultState ()Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/Block getActualState (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/Block setUnlocalizedName (Ljava/lang/String;)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block getLocalizedName ()Ljava/lang/String;",
			"net/minecraft/block/Block getUnlocalizedName ()Ljava/lang/String;",
			"net/minecraft/block/Block onEntityCollidedWithBlock (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/Entity;)V",
			"net/minecraft/block/Block onBlockPlaced (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;FFFILnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/Block onBlockClicked (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/player/EntityPlayer;)V",
			"net/minecraft/block/material/Material getMaterialMapColor ()Lnet/minecraft/block/material/MapColor;",
			"net/minecraft/block/Block createBlockState ()Lnet/minecraft/block/state/BlockState;", 
			"net/minecraft/block/Block getBlockState ()Lnet/minecraft/block/state/BlockState;",
			"net/minecraft/block/Block registerBlocks ()V",
			"net/minecraft/util/ObjectIntIdentityMap put (Ljava/lang/Object;I)V"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemStack",
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/util/MainOrOffHand",
			"net/minecraft/util/EnumFacing",
			"net/minecraft/util/MovingObjectPosition",
			"net/minecraft/util/ResourceLocation",
			"net/minecraft/world/IBlockAccess",
			"net/minecraft/entity/Entity",
			"net/minecraft/entity/EntityLivingBase"
	})
	public boolean processBlockClass()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode mainOrOffHand = getClassNodeFromMapping("net/minecraft/util/MainOrOffHand");
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		ClassNode movingObjectPosition = getClassNodeFromMapping("net/minecraft/util/MovingObjectPosition");
		ClassNode resourceLocation = getClassNodeFromMapping("net/minecraft/util/ResourceLocation");
		ClassNode iBlockAccess = getClassNodeFromMapping("net/minecraft/world/IBlockAccess");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");

		if (!MeddleUtil.notNull(block, item, itemStack, blockPos, world, iBlockState, entityPlayer, 
				mainOrOffHand, enumFacing, movingObjectPosition, resourceLocation, iBlockAccess,
				entityLivingBase, entity)) return false;

		
				
		List<MethodNode> methods = new ArrayList<MethodNode>();
		
		
		
		// public static int getIdFromBlock(Block)
		methods = getMatchingMethods(block, null, "(L" + block.name + ";)I");
		methods = removeMethodsWithoutFlags(methods,  Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getIdFromBlock (Lnet/minecraft/block/Block;)I",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.GETSTATIC) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				String registryNamespacedDefaultedByKey = Type.getType(fn.desc).getClassName();
				addClassMapping("net/minecraft/util/RegistryNamespacedDefaultedByKey", registryNamespacedDefaultedByKey);
				addFieldMapping("net/minecraft/block/Block blockRegistry Lnet/minecraft/util/RegistryNamespacedDefaultedByKey;",
						block.name + " " + fn.name + " " + fn.desc);
			}
		}
			
		
		
		// private static void registerBlock(int, ResourceLocation, Block)
		methods = getMatchingMethods(block,  null, "(IL" + resourceLocation.name + ";L" + block.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block registerBlock (ILnet/minecraft/util/ResourceLocation;Lnet/minecraft/block/Block;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		// private static void registerBlock(int, String, Block)
		methods = getMatchingMethods(block,  null, "(ILjava/lang/String;L" + block.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block registerBlock (ILjava/lang/String;Lnet/minecraft/block/Block;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}	

				
		// protected Block(Material)
		methods.clear();
		String materialClass = null;
		String mapColorClass = null;
		
		for (MethodNode method : block.methods) {
			if (!method.name.equals("<init>")) continue;
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length != 1) continue;
			if (args[0].getSort() != Type.OBJECT) continue;
			methods.add(method);
		}
		
		if (methods.size() == 1) {
			materialClass = Type.getMethodType(methods.get(0).desc).getArgumentTypes()[0].getClassName();
			addClassMapping("net/minecraft/block/material/Material", materialClass);	
			
			startloop:
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) 
			{
				if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (!mn.owner.equals(materialClass)) continue;				
				mapColorClass = Type.getMethodType(mn.desc).getReturnType().getClassName();				
				if (searchConstantPoolForStrings(mapColorClass, "Map colour ID must be between 0 and 63 (inclusive)")) 
				{
					addClassMapping("net/minecraft/block/material/MapColor", mapColorClass);
					addMethodMapping("net/minecraft/block/material/Material getMaterialMapColor ()Lnet/minecraft/block/material/MapColor;",
							materialClass + " " + mn.name + " " + mn.desc);
					break startloop;
				}
				else mapColorClass = null;
			}
		}
		
		
		
		// protected Block(Material, MapColor)
		String blockStateClass = null;
		methods.clear();
		if (materialClass != null && mapColorClass != null) methods = getMatchingMethods(block, "<init>", "(L" + materialClass + ";L" + mapColorClass + ";)V");
		if (methods.size() == 1) {
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				Type returnType = Type.getMethodType(mn.desc).getReturnType();
				if (returnType.getSort() != Type.OBJECT) continue;
				if (!returnType.getClassName().equals(iBlockState.name)) continue;
				if (searchConstantPoolForStrings(mn.owner, "block", "properties")) {
					blockStateClass = mn.owner;
					addClassMapping("net/minecraft/block/state/BlockState", mn.owner);
					break;
				}
			}
		}
		
		

		// public boolean onBlockActivated(World, BlockPos, IBlockState, EntityPlayer, MainOrOffHand, ItemStack, EnumFacing, float, float, float)
		String descriptor = assembleDescriptor("(", world, blockPos, iBlockState, entityPlayer, mainOrOffHand, itemStack, enumFacing, "FFF)Z");
		methods = getMatchingMethods(block,  null,  descriptor);
		if (methods.size() == 1) {
			MethodNode method = methods.get(0);
			addMethodMapping("net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z",
					block.name + " " + method.name + " " + method.desc);
		}

		
		// (15w37a added IBlockState parm)
		// public MovingObjectPosition collisionRayTrace(IBlockState param0, World worldIn, BlockPos pos, Vec3 start, Vec3 end)
		methods.clear();
		for (MethodNode method : block.methods) {
			if (!method.desc.startsWith("(L" + iBlockState.name + ";L" + world.name + ";L" + blockPos.name + ";L")) continue;			
			if (!method.desc.endsWith(";)L" + movingObjectPosition.name + ";")) continue;			
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();			
			if (args.length != 5) continue;
			if (args[3].getSort() != Type.OBJECT) continue;
			if (!args[3].getClassName().equals(args[4].getClassName())) continue;
			methods.add(method);			
		}
		if (methods.size() == 1) {
			MethodNode method = methods.get(0);			
			String vec3_name = Type.getMethodType(method.desc).getArgumentTypes()[3].getClassName();
			addClassMapping("net/minecraft/util/Vec3", vec3_name);
			addMethodMapping("net/minecraft/block/Block collisionRayTrace (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;",
					block.name + " " + method.name + " " + method.desc);
		}
		
		
		methods = getMatchingMethods(block, null, "(I)L" + iBlockState.name + ";");
		if (methods.size() == 2) {
			for (MethodNode method : methods) {
				// public IBlockState getStateFromMeta(int)
				if ((method.access & Opcodes.ACC_STATIC) == 0) {
					addMethodMapping("net/minecraft/block/Block getStateFromMeta (I)Lnet/minecraft/block/state/IBlockState;",
							block.name + " " + method.name + " " + method.desc);
				}
				// public static IBlockState getStateById(int)
				else {
					addMethodMapping("net/minecraft/block/Block getStateById (I)Lnet/minecraft/block/state/IBlockState;",
							block.name + " " + method.name + " " + method.desc);
				}
			}
		}

		
		// public int getMetaFromState(IBlockState)
		methods = getMatchingMethods(block, null, "(L" + iBlockState.name + ";)I");
		outerloop:
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) 
			{
				if (!isLdcWithString(insn, "Don't know how to convert ")) continue;				
				addMethodMapping("net/minecraft/block/Block getMetaFromState (Lnet/minecraft/block/state/IBlockState;)I",
						block.name + " " + method.name + " " + method.desc);
				break outerloop;
			}
		}
		
		
		// protected final void setDefaultState(IBlockState state)
		methods = getMatchingMethods(block, null, "(L" + iBlockState.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block setDefaultState (Lnet/minecraft/block/state/IBlockState;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public final IBlockState getDefaultState()
		methods = getMatchingMethods(block, null, "()L" + iBlockState.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getDefaultState ()Lnet/minecraft/block/state/IBlockState;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals("L" + iBlockState.name + ";")) {
				addFieldMapping("net/minecraft/block/Block defaultBlockState Lnet/minecraft/block/state/IBlockState;",
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos)
		methods = getMatchingMethods(block, null, "(L" + iBlockState.name + ";L" + iBlockAccess.name + ";L" + blockPos.name + ";)L" + iBlockState.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getActualState (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public Block setUnlocalizedName(String name)
		methods = getMatchingMethods(block, null, "(Ljava/lang/String;)L" + block.name + ";");
		methods = removeMethodsWithFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block setUnlocalizedName (Ljava/lang/String;)Lnet/minecraft/block/Block;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.PUTFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.desc.equals("Ljava/lang/String;")) {
					addFieldMapping("net/minecraft/block/Block unlocalizedName Ljava/lang/String;",
							block.name + " " + fn.name + " " + fn.desc);
				}
			}
		}
		

		
		methods = getMatchingMethods(block, null, "()Ljava/lang/String;");
		for (Iterator<MethodNode> iterator = methods.iterator(); iterator.hasNext();) {
			MethodNode method = iterator.next();
			if (method.name.equals("toString")) iterator.remove();
		}
		if (methods.size() == 2) {
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					// public String getLocalizedName()		
					if (isLdcWithString(insn, ".name")) {
						addMethodMapping("net/minecraft/block/Block getLocalizedName ()Ljava/lang/String;",
								block.name + " " + method.name + " " + method.desc);
					}
					// public String getUnlocalizedName()
					else if (isLdcWithString(insn, "tile.")) {
						addMethodMapping("net/minecraft/block/Block getUnlocalizedName ()Ljava/lang/String;",
								block.name + " " + method.name + " " + method.desc);
					}
				}
			}
		}
		
		
		
		// public void onEntityCollidedWithBlock(World, BlockPos, Entity)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, entity, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onEntityCollidedWithBlock (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/Entity;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		

	    // public IBlockState onBlockPlaced(World, BlockPos, EnumFacing, float, float, float, int, EntityLivingBase)		
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, enumFacing, "FFFI", entityLivingBase, ")", iBlockState));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onBlockPlaced (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;FFFILnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/block/state/IBlockState;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
	    // public void onBlockClicked(World, BlockPos, EntityPlayer)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, entityPlayer, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onBlockClicked (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/player/EntityPlayer;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected BlockState createBlockState()
		// public BlockState getBlockState()
		methods.clear();
		if (blockStateClass != null) methods = getMatchingMethods(block, null, "()L" + blockStateClass + ";");
		if (methods.size() == 2) 
		{
			MethodNode createBlockState = null;
			startloop:
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() != Opcodes.NEW) continue;
					TypeInsnNode tn = (TypeInsnNode)insn;
					if (tn.desc.equals(blockStateClass)) {
						if (createBlockState != null) { createBlockState = null; break startloop; }
						createBlockState = method;
						break;
					}
				}
			}
			
			if (createBlockState != null) {
				addMethodMapping("net/minecraft/block/Block createBlockState ()Lnet/minecraft/block/state/BlockState;", 
						block.name + " " + createBlockState.name + " " + createBlockState.desc);
				methods.remove(createBlockState);
				addMethodMapping("net/minecraft/block/Block getBlockState ()Lnet/minecraft/block/state/BlockState;",
						block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		
		methods = getMatchingMethods(block, null, "()V");
		methods = removeMethodsWithoutFlags(methods,  Opcodes.ACC_STATIC);
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			if (it.next().name.contains("<")) it.remove();
		}
		String objectIntIdentityMap = null;
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block registerBlocks ()V", block.name + " " + methods.get(0).name + " ()V");
			
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (mn.desc.equals("(Ljava/lang/Object;I)V")) {
					objectIntIdentityMap = mn.owner;
					addClassMapping("net/minecraft/util/ObjectIntIdentityMap", mn.owner);
					addMethodMapping("net/minecraft/util/ObjectIntIdentityMap put (Ljava/lang/Object;I)V",
							mn.owner + " " + mn.name + " " + mn.desc);
				}
			}
			
			List<FieldInsnNode> fieldNodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			List<FieldInsnNode> fieldGets = new ArrayList<FieldInsnNode>();
			List<FieldInsnNode> fieldPuts = new ArrayList<FieldInsnNode>();
			for (Iterator<FieldInsnNode> it = fieldNodes.iterator(); it.hasNext();) {
				FieldInsnNode fn = it.next();
				if (fn.owner.equals(block.name) && fn.desc.equals("Z")) {
					if (fn.getOpcode() == Opcodes.GETFIELD) fieldGets.add(fn);
					else if (fn.getOpcode() == Opcodes.PUTFIELD) fieldPuts.add(fn);
				}
				else it.remove();
			}
			
			if (fieldGets.size() == 1 && fieldPuts.size() == 2 && fieldPuts.get(0).name.equals(fieldPuts.get(1).name)) {
				addFieldMapping("net/minecraft/block/Block translucent Z", block.name + " " + fieldGets.get(0).name + " Z");
				addFieldMapping("net/minecraft/block/Block useNeighborBrightness Z", block.name + " " + fieldPuts.get(0).name + " Z");
			}			
		}		
		
		List<FieldNode> fields = getMatchingFields(block, null, "L" + objectIntIdentityMap + ";");		
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/block/Block BLOCK_STATE_IDS Lnet/minecraft/util/ObjectIntIdentityMap;",
					block.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		return true;
	}

	
	@Mapping(provides={
			"net/minecraft/item/ItemBlock",
			"net/minecraft/util/AxisAlignedBB"
			},
			providesFields={
			////"net/minecraft/block/Block blockBounds Lnet/minecraft/util/AxisAlignedBB;"
			"net/minecraft/block/Block blockState Lnet/minecraft/block/state/BlockState;",
			"net/minecraft/block/Block displayOnCreativeTab Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/entity/Entity motionY D"
			},
			providesMethods={
			"net/minecraft/block/Block getStateId (Lnet/minecraft/block/state/IBlockState;)I",
			"net/minecraft/block/Block getBlockById (I)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block getBlockFromItem (Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block getBlockFromName (Ljava/lang/String;)Lnet/minecraft/block/Block;",
			//"net/minecraft/block/Block setBlockBounds (Lnet/minecraft/util/AxisAlignedBB;)V",
			//"net/minecraft/block/Block getBlockBounds (Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/util/AxisAlignedBB;",
			"net/minecraft/block/Block onNeighborBlockChange (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/Block;)V",
			//"net/minecraft/block/Block setBlockBoundsBasedOnState (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)V"
			"net/minecraft/block/Block setCreativeTab (Lnet/minecraft/creativetab/CreativeTabs;)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block dropBlockAsItemWithChance (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;FI)V",
			"net/minecraft/block/Block dropBlockAsItem (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;I)V",
			"net/minecraft/block/Block harvestBlock (Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/item/ItemStack;)V",
			"net/minecraft/block/Block onFallenUpon (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/Entity;F)V",
			"net/minecraft/entity/Entity fall (FF)V",
			"net/minecraft/block/Block onLanded (Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;)V",
			"net/minecraft/block/Block damageDropped (Lnet/minecraft/block/state/IBlockState;)I",
			"net/minecraft/block/Block isBlockSolid (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/item/Item",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/block/state/BlockState",
			"net/minecraft/util/BlockPos",
			"net/minecraft/world/World",
			"net/minecraft/world/IBlockAccess",
			"net/minecraft/creativetab/CreativeTabs",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/tileentity/TileEntity",
			"net/minecraft/item/ItemStack",
			"net/minecraft/entity/Entity",
			"net/minecraft/util/EnumFacing"			
			})
	public boolean processBlockClass2()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode blockState = getClassNodeFromMapping("net/minecraft/block/state/BlockState");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode iBlockAccess = getClassNodeFromMapping("net/minecraft/world/IBlockAccess");
		ClassNode creativeTabs = getClassNodeFromMapping("net/minecraft/creativetab/CreativeTabs");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode tileEntity = getClassNodeFromMapping("net/minecraft/tileentity/TileEntity");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		if (!MeddleUtil.notNull(block, item, iBlockState, blockState, blockPos, world, iBlockAccess, creativeTabs,
				entityPlayer, tileEntity, itemStack, entity, enumFacing)) return false;
		
		List<MethodNode> methods;
		
		
		String axisAlignedBB_name = null;
		
		// net/minecraft/util/AxisAlignedBB
		// private AxisAlignedBB blockBounds
		List<FieldNode> fields = getMatchingFields(block, null, null);		
		for (Iterator<FieldNode> it = fields.iterator(); it.hasNext(); ) {
			FieldNode fn = it.next();
			if (!fn.desc.startsWith("L")) { it.remove(); continue; }			
			Type t = Type.getType(fn.desc);
			
			if (axisAlignedBB_name == null && searchConstantPoolForStrings(t.getClassName(), "box[")) {
				ClassNode cn = getClassNode(t.getClassName());
				int doubles = 0;
				for (FieldNode cnfn : cn.fields) { if (cnfn.desc.equals("D")) doubles++; }
				if (cn.fields.size() == 6 && doubles == 6) {
					axisAlignedBB_name = t.getClassName();
					addClassMapping("net/minecraft/util/AxisAlignedBB", axisAlignedBB_name);	
				}
			}
			
			if (axisAlignedBB_name == null || !t.getClassName().equals(axisAlignedBB_name)) it.remove();
		}
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/block/Block blockBounds Lnet/minecraft/util/AxisAlignedBB;", 
					block.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		fields = getMatchingFields(block, null, "L" + blockState.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/block/Block blockState Lnet/minecraft/block/state/BlockState;",
					block.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		// public static int getStateId(IBlockState)
		methods = getMatchingMethods(block, null, "(L" + iBlockState.name + ";)I");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getStateId (Lnet/minecraft/block/state/IBlockState;)I",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public static Block getBlockById(int)
		methods = getMatchingMethods(block, null, "(I)L" + block.name + ";");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getBlockById (I)Lnet/minecraft/block/Block;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public static Block getBlockFromItem(Item)
		methods = getMatchingMethods(block, null, "(L" + item.name + ";)L" + block.name + ";");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getBlockFromItem (Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.INSTANCEOF) continue;
				TypeInsnNode tn = (TypeInsnNode)insn;
				if (searchConstantPoolForStrings(tn.desc, "BlockEntityTag")) {
					addClassMapping("net/minecraft/item/ItemBlock", tn.desc);
					break;
				}
			}
		}
		
		
		// public static Block getBlockFromName(String)
		methods = getMatchingMethods(block, null, "(Ljava/lang/String;)L" + block.name + ";");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getBlockFromName (Ljava/lang/String;)Lnet/minecraft/block/Block;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected final void setBlockBounds(float, float, float, float, float, float)
		// CHANGED in 15w37a
		// public void setBlockBounds(AxisAlignedBB param0)		
		methods = getMatchingMethods(block, null, "(L" + axisAlignedBB_name + ";)V");		
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block setBlockBounds (Lnet/minecraft/util/AxisAlignedBB;)V", 
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// NEW in 15w37a
		// public AxisAlignedBB getBlockBounds(IBlockState param0)
		methods = getMatchingMethods(block, null, "(L" + iBlockState.name + ";)L" + axisAlignedBB_name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getBlockBounds (Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/util/AxisAlignedBB;", 
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		//  public void onNeighborBlockChange(World, BlockPos, IBlockState, Block)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, iBlockState, block, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onNeighborBlockChange (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/Block;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void setBlockBoundsBasedOnState(IBlockAccess param0, BlockPos param1)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockAccess, blockPos, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block setBlockBoundsBasedOnState (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public Block setCreativeTab(CreativeTabs tab)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", creativeTabs, ")", block));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block setCreativeTab (Lnet/minecraft/creativetab/CreativeTabs;)Lnet/minecraft/block/Block;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals("L" + creativeTabs.name + ";")) {
				addFieldMapping("net/minecraft/block/Block displayOnCreativeTab Lnet/minecraft/creativetab/CreativeTabs;",
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public final void dropBlockAsItem(World worldIn, BlockPos pos, IBlockState state, int forture)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, iBlockState, "I)V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block dropBlockAsItem (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;I)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public void dropBlockAsItemWithChance(World worldIn, BlockPos pos, IBlockState state, float chance, int fortune)
		// public int damageDropped(IBlockState state)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, iBlockState, "FI)V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block dropBlockAsItemWithChance (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;FI)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			for (Iterator<MethodInsnNode> it = nodes.iterator(); it.hasNext();) {
				MethodInsnNode mn = it.next();
				if (mn.owner.equals(block.name) && mn.desc.equals("(L" + iBlockState.name + ";)I")) continue;
				it.remove();
			}			
			if (nodes.size() == 1) {
				addMethodMapping("net/minecraft/block/Block damageDropped (Lnet/minecraft/block/state/IBlockState;)I",
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public void harvestBlock(World param0, EntityPlayer param1, BlockPos param2, IBlockState param3, TileEntity param4, ItemStack param5)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, entityPlayer, blockPos, iBlockState, tileEntity, itemStack, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block harvestBlock (Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/item/ItemStack;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		
		// public void onFallenUpon(World param0, BlockPos param1, Entity param2, float param3)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, entity, "F)V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onFallenUpon (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/Entity;F)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);			
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			if (nodes.size() == 1) {
				MethodInsnNode mn = nodes.get(0);
				if (mn.owner.equals(entity.name) && mn.desc.equals("(FF)V")) {
					addMethodMapping("net/minecraft/entity/Entity fall (FF)V", entity.name + " " + mn.name + " " + mn.desc);
				}
			}
		}
				
		
		// public void onLanded(World param0, Entity param1)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, entity, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onLanded (Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);			
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			if (nodes.size() == 1) {
				FieldInsnNode fn = nodes.get(0);
				if (fn.owner.equals(entity.name) && fn.desc.equals("D")) {
					addFieldMapping("net/minecraft/entity/Entity motionY D", entity.name + " " + fn.name + " " + fn.desc);
				}
			}
		}
		
		
		// public boolean isBlockSolid(IBlockAccess param0, BlockPos param1, EnumFacing param2)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockAccess, blockPos, enumFacing, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block isBlockSolid (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);						
		}
		
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/block/EnumBlockRotation",
			"net/minecraft/block/EnumBlockMirroring"
			},
			providesFields={
			"net/minecraft/block/Block blockMaterial Lnet/minecraft/block/material/Material;",
			"net/minecraft/block/Block mapColor Lnet/minecraft/block/material/MapColor;",
			"net/minecraft/block/Block lightOpacity I",
			"net/minecraft/block/Block lightValue I",
			"net/minecraft/block/Block blockResistance F",
			"net/minecraft/block/Block stepSound Lnet/minecraft/block/BlockSoundType;"
			},
			providesMethods={
			"net/minecraft/block/Block getMaterial (Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/material/Material;",
			"net/minecraft/block/Block getMapColor (Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/material/MapColor;",
			"net/minecraft/block/Block getStateFromRotation (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/EnumBlockRotation;)Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/Block getStateFromMirroring (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/EnumBlockMirroring;)Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/Block setLightOpacity (I)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block setLightLevel (F)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block setResistance (F)Lnet/minecraft/block/Block;",
			"net/minecraft/block/Block getStepSound ()Lnet/minecraft/block/BlockSoundType;",
			"net/minecraft/block/Block getComparatorInputOverride (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)I",
			"net/minecraft/block/Block isEqualTo (Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)Z",
			"net/minecraft/block/Block isAssociatedBlock (Lnet/minecraft/block/Block;)Z",
			"net/minecraft/block/Block canDropFromExplosion (Lnet/minecraft/world/Explosion;)Z",
			"net/minecraft/block/Block fillWithRain (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)V",
			"net/minecraft/block/Block onBlockHarvested (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;)V",
			"net/minecraft/block/Block onBlockPlacedBy (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V",
			"net/minecraft/block/Block quantityDroppedWithBonus (ILjava/util/Random;)I",
			"net/minecraft/block/Block quantityDropped (Ljava/util/Random;)I",
			"net/minecraft/block/Block createStackedBlock (Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/Item getHasSubtypes ()Z",
			"net/minecraft/block/Block modifyAcceleration (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/Vec3;",
			"net/minecraft/block/Block canReplace (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/item/ItemStack;)Z",
			"net/minecraft/block/Block canPlaceBlockOnSide (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z",
			"net/minecraft/block/Block canPlaceBlockAt (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Z",
			"net/minecraft/block/Block onBlockDestroyedByExplosion (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/world/Explosion;)V",
			"net/minecraft/block/Block getExplosionResistance (Lnet/minecraft/entity/Entity;)F",
			"net/minecraft/block/Block dropXpOnBlockBreak (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;I)V",
			"net/minecraft/block/Block getPlayerRelativeBlockHardness (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)F",
			"net/minecraft/entity/player/EntityPlayer canHarvestBlock (Lnet/minecraft/block/Block;)Z",
			"net/minecraft/entity/player/EntityPlayer getToolDigEfficiency (Lnet/minecraft/block/state/IBlockState;)F",
			"net/minecraft/block/Block getItemDropped (Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;I)Lnet/minecraft/item/Item;",
			"net/minecraft/block/Block randomTick (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V",
			"net/minecraft/block/Block updateTick (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V",
			"net/minecraft/block/Block addCollisionBoxesToList (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V",
			"net/minecraft/block/state/IBlockWrapper getCollisionBoundingBox (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;",
			"net/minecraft/block/Block addCollisionBoxToList (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/util/AxisAlignedBB;)V"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/item/Item",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/block/state/BlockState",
			"net/minecraft/util/BlockPos",
			"net/minecraft/world/World",
			"net/minecraft/world/IBlockAccess",
			"net/minecraft/creativetab/CreativeTabs",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/tileentity/TileEntity",
			"net/minecraft/item/ItemStack",
			"net/minecraft/block/material/Material",
			"net/minecraft/block/material/MapColor",
			"net/minecraft/block/BlockSoundType",
			"net/minecraft/entity/EntityLivingBase",
			"net/minecraft/util/EnumFacing",
			"net/minecraft/entity/Entity",
			"net/minecraft/util/Vec3",
			"net/minecraft/util/AxisAlignedBB",
			"net/minecraft/block/state/IBlockWrapper"
			})
	public boolean processBlockClass3()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode blockState = getClassNodeFromMapping("net/minecraft/block/state/BlockState");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode iBlockAccess = getClassNodeFromMapping("net/minecraft/world/IBlockAccess");
		ClassNode creativeTabs = getClassNodeFromMapping("net/minecraft/creativetab/CreativeTabs");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode tileEntity = getClassNodeFromMapping("net/minecraft/tileentity/TileEntity");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode material = getClassNodeFromMapping("net/minecraft/block/material/Material");
		ClassNode mapColor = getClassNodeFromMapping("net/minecraft/block/material/MapColor");
		ClassNode blockSoundType = getClassNodeFromMapping("net/minecraft/block/BlockSoundType");
		ClassNode explosion = getClassNodeFromMapping("net/minecraft/world/Explosion");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");
		ClassNode vec3 = getClassNodeFromMapping("net/minecraft/util/Vec3");
		ClassNode aabb = getClassNodeFromMapping("net/minecraft/util/AxisAlignedBB");
		ClassNode iBlockWrapper = getClassNodeFromMapping("net/minecraft/block/state/IBlockWrapper");
		if (!MeddleUtil.notNull(block, item, iBlockState, blockState, blockPos, world, iBlockAccess, creativeTabs,
				entityPlayer, tileEntity, itemStack, material, mapColor, blockSoundType, explosion, entityLivingBase,
				enumFacing, entity, vec3, aabb, iBlockWrapper)) return false;
		
		
		// public Material getMaterial(IBlockState)
		List<MethodNode> methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, ")", material));		
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getMaterial (Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/material/Material;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals("L" + material.name + ";")) {
				addFieldMapping("net/minecraft/block/Block blockMaterial Lnet/minecraft/block/material/Material;",
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public MapColor getMapColor(IBlockState state)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, ")", mapColor));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getMapColor (Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/material/MapColor;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals("L" + mapColor.name + ";")) {
				addFieldMapping("net/minecraft/block/Block mapColor Lnet/minecraft/block/material/MapColor;",
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public IBlockState getStateFromRotation(IBlockState param0, EnumBlockRotation param1)
		// public IBlockState getStateFromMirroring(IBlockState param0, EnumBlockMirroring param1)
		methods.clear();
		for (MethodNode method : block.methods) {
			if (method.desc.startsWith("(L" + iBlockState.name + ";L") && 
					method.desc.endsWith(";)L" + iBlockState.name + ";") && 
					Type.getMethodType(method.desc).getArgumentTypes().length == 2) methods.add(method);			
		}
		if (methods.size() == 2 && !methods.get(0).desc.equals(methods.get(1).desc)) {
			for (MethodNode method : methods) {
				String className = Type.getMethodType(method.desc).getArgumentTypes()[1].getClassName();
				if (searchConstantPoolForStrings(className, "rotate_0", "rotate_90")) {
					addClassMapping("net/minecraft/block/EnumBlockRotation", className);
					addMethodMapping("net/minecraft/block/Block getStateFromRotation (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/EnumBlockRotation;)Lnet/minecraft/block/state/IBlockState;",
							block.name + " " + method.name + " " + method.desc);
				}
				else if (searchConstantPoolForStrings(className, "no_mirror", "mirror_left_right")) {
					addClassMapping("net/minecraft/block/EnumBlockMirroring", className);
					addMethodMapping("net/minecraft/block/Block getStateFromMirroring (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/EnumBlockMirroring;)Lnet/minecraft/block/state/IBlockState;",
							block.name + " " + method.name + " " + method.desc);
				}
			}
		}
		
		
		// protected Block setLightOpacity(int opacity)
		methods = getMatchingMethods(block, null, "(I)L" + block.name + ";");
		methods = removeMethodsWithFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block setLightOpacity (I)Lnet/minecraft/block/Block;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals("I")) {
				addFieldMapping("net/minecraft/block/Block lightOpacity I",
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// protected Block setLightLevel(float value)
		// public Block setResistance(float resistance)
		methods = getMatchingMethods(block, null, "(F)L" + block.name + ";");
		int setLightLevel_num = -1;
		int setResistance_num = -1;
		for (int n = 0; n < methods.size(); n++) {
			MethodNode method = methods.get(n);
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (isLdcWithFloat(insn, 15F)) {
					if (setLightLevel_num == -1) setLightLevel_num = n;
					else setLightLevel_num = -2;
				}
				else if (isLdcWithFloat(insn, 3F)) {
					if (setResistance_num == -1) setResistance_num = n;
					else setResistance_num = -2;
				}
			}
		}
		if (setLightLevel_num >= 0) {
			addMethodMapping("net/minecraft/block/Block setLightLevel (F)Lnet/minecraft/block/Block;",
					block.name + " " + methods.get(setLightLevel_num).name + " " + methods.get(setLightLevel_num).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(setLightLevel_num), FieldInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals("I")) {
				addFieldMapping("net/minecraft/block/Block lightValue I",
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		if (setResistance_num >= 0) {
			addMethodMapping("net/minecraft/block/Block setResistance (F)Lnet/minecraft/block/Block;",
					block.name + " " + methods.get(setResistance_num).name + " " + methods.get(setResistance_num).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(setResistance_num), FieldInsnNode.class);			
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals("F")) {				
				addFieldMapping("net/minecraft/block/Block blockResistance F",
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public BlockSoundType getStepSound()
		methods = getMatchingMethods(block, null, "()L" + blockSoundType.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getStepSound ()Lnet/minecraft/block/BlockSoundType;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals("L" + blockSoundType.name + ";")) {
				addFieldMapping("net/minecraft/block/Block stepSound Lnet/minecraft/block/BlockSoundType;",
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public int getComparatorInputOverride(IBlockState param0, World worldIn, BlockPos pos)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, world, blockPos, ")I"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getComparatorInputOverride (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)I",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public static boolean isEqualTo(Block blockIn, Block other)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", block, block, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block isEqualTo (Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);		
		}
		
		
		// public boolean isAssociatedBlock(Block other)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", block, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block isAssociatedBlock (Lnet/minecraft/block/Block;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);		
		}
		
		
		// public boolean canDropFromExplosion(Explosion explosionIn)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", explosion, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block canDropFromExplosion (Lnet/minecraft/world/Explosion;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);		
		}
		
		
		// public void fillWithRain(World worldIn, BlockPos pos)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block fillWithRain (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);		
		}
		
		
		//  public void onBlockHarvested(World worldIn, BlockPos pos, IBlockState state, EntityPlayer player)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, iBlockState, entityPlayer, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onBlockHarvested (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);		
		}
		
		
		// public void onBlockPlacedBy(World param0, BlockPos param1, IBlockState param2, EntityLivingBase param3, ItemStack param4)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, iBlockState, entityLivingBase, itemStack, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onBlockPlacedBy (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);		
		}
		
		
		// public int quantityDroppedWithBonus(int fortune, Random random)
		methods = getMatchingMethods(block, null, "(ILjava/util/Random;)I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block quantityDroppedWithBonus (ILjava/util/Random;)I",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals("(Ljava/util/Random;)I"))
			{
				addMethodMapping("net/minecraft/block/Block quantityDropped (Ljava/util/Random;)I", 
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		//  protected ItemStack createStackedBlock(IBlockState param0)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, ")", itemStack));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block createStackedBlock (Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/item/ItemStack;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			for (Iterator<MethodInsnNode> it = nodes.iterator(); it.hasNext(); ) {
				MethodInsnNode mn = it.next();
				if (mn.owner.equals(item.name) && mn.desc.equals("()Z")) continue;
				it.remove();
			}
			if (nodes.size() == 1) {
				addMethodMapping("net/minecraft/item/Item getHasSubtypes ()Z", item.name + " " + nodes.get(0).name + " ()Z");
			}
		}
		
		
		// public Vec3 modifyAcceleration(World worldIn, BlockPos pos, Entity entityIn, Vec3 motion)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, entity, vec3, ")", vec3));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block modifyAcceleration (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/Vec3;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean canReplace(World worldIn, BlockPos pos, EnumFacing side, ItemStack stack)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, enumFacing, itemStack, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block canReplace (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/item/ItemStack;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		

		// public boolean canPlaceBlockOnSide(World worldIn, BlockPos pos, EnumFacing side)
		// public boolean canPlaceBlockAt(World worldIn, BlockPos pos)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, enumFacing, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block canPlaceBlockOnSide (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) && nodes.get(0).desc.equals(assembleDescriptor("(", world, blockPos, ")Z")))
			{
				addMethodMapping("net/minecraft/block/Block canPlaceBlockAt (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Z", 
						block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
		}
		
		
		// public void onBlockDestroyedByExplosion(World worldIn, BlockPos pos, Explosion explosionIn)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, explosion, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block onBlockDestroyedByExplosion (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/world/Explosion;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public float getExplosionResistance(Entity exploder)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", entity, ")F"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getExplosionResistance (Lnet/minecraft/entity/Entity;)F",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected void dropXpOnBlockBreak(World worldIn, BlockPos pos, int amount)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, "I)V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block dropXpOnBlockBreak (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;I)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public float getPlayerRelativeBlockHardness(IBlockState param0, EntityPlayer playerIn, World worldIn, BlockPos pos)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, entityPlayer, world, blockPos, ")F"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getPlayerRelativeBlockHardness (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)F",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			List<MethodInsnNode> getBlockHardness = new ArrayList<>();
			List<MethodInsnNode> canHarvestBlock = new ArrayList<>();
			List<MethodInsnNode> getToolDigEfficiency = new ArrayList<>();
			
			for (MethodInsnNode mn : nodes) {
				//if (mn.owner.equals(iBlockState.name) && mn.desc.equals(assembleDescriptor("(", world, blockPos, ")F"))) getBlockHardness.add(mn);
				if (mn.owner.equals(entityPlayer.name)) {
					if (mn.desc.equals(assembleDescriptor("(", block, ")Z"))) canHarvestBlock.add(mn);
					else if (mn.desc.equals(assembleDescriptor("(", iBlockState, ")F"))) getToolDigEfficiency.add(mn);
				}
			}
			
			/*if (getBlockHardness.size() == 1) {
				addMethodMapping("net/minecraft/block/state/IBlockState getBlockHardness (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)F",
						iBlockState.name + " " + getBlockHardness.get(0).name + " " + getBlockHardness.get(0).desc);
			}*/
			if (canHarvestBlock.size() == 1) {
				addMethodMapping("net/minecraft/entity/player/EntityPlayer canHarvestBlock (Lnet/minecraft/block/Block;)Z",
						entityPlayer.name + " " + canHarvestBlock.get(0).name + " " + canHarvestBlock.get(0).desc);
			}
			if (getToolDigEfficiency.size() == 2 && getToolDigEfficiency.get(0).name.equals(getToolDigEfficiency.get(1).name)) {
				addMethodMapping("net/minecraft/entity/player/EntityPlayer getToolDigEfficiency (Lnet/minecraft/block/state/IBlockState;)F",
						entityPlayer.name + " " + getToolDigEfficiency.get(0).name + " " + getToolDigEfficiency.get(0).desc);
			}			
		}
		
		
		// public Item getItemDropped(IBlockState state, Random rand, int fortune)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, "Ljava/util/Random;I)", item));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getItemDropped (Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;I)Lnet/minecraft/item/Item;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void randomTick(World worldIn, BlockPos pos, IBlockState state, Random random)
		// public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random random)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", world, blockPos, iBlockState, "Ljava/util/Random;)V"));
		if (methods.size() == 2) {
			for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
				if (getNextRealOpcode(it.next().instructions.getFirst()).getOpcode() == Opcodes.RETURN) it.remove();
			}
			if (methods.size() == 1) {
				List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
				if (nodes.size() == 1 && nodes.get(0).owner.equals(block.name) 
						&& !nodes.get(0).name.equals(methods.get(0).name) && nodes.get(0).desc.equals(methods.get(0).desc)) {
					addMethodMapping("net/minecraft/block/Block randomTick (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V",
							block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
					addMethodMapping("net/minecraft/block/Block updateTick (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V",
							block.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
				}
			}
		}
		
		
		// public void addCollisionBoxesToList(IBlockState param0, World param1, BlockPos param2, AxisAlignedBB param3, List<AxisAlignedBB> param4, Entity param5)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, world, blockPos, aabb, "Ljava/util/List;", entity, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block addCollisionBoxesToList (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			List<MethodInsnNode> getCollisionBoundingBox = new ArrayList<>();
			List<MethodInsnNode> addCollisionBoxToList = new ArrayList<>();
			for (MethodInsnNode mn : nodes) {
				if (mn.owner.equals(block.name)) {
					if (mn.desc.equals(assembleDescriptor("(", blockPos, aabb, "Ljava/util/List;", aabb, ")V"))) 
						addCollisionBoxToList.add(mn);					
				}
				else if (mn.owner.equals(iBlockState.name)) {
					if (mn.desc.equals(assembleDescriptor("(", world, blockPos, ")", aabb)))
						getCollisionBoundingBox.add(mn);
				}
			}
			
			if (getCollisionBoundingBox.size() == 1) {
				addMethodMapping("net/minecraft/block/state/IBlockWrapper getCollisionBoundingBox (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;",
						iBlockWrapper.name + " " + getCollisionBoundingBox.get(0).name + " " + getCollisionBoundingBox.get(0).desc);
			}
			if (addCollisionBoxToList.size() == 1) {
				addMethodMapping("net/minecraft/block/Block addCollisionBoxToList (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/util/AxisAlignedBB;)V",
						block.name + " " + addCollisionBoxToList.get(0).name + " " + addCollisionBoxToList.get(0).desc);
			}
		}
		
		return true;
	}
	
	
	
	
	@Mapping(provides={
			"net/minecraft/block/properties/IProperty"
			},
			providesMethods={
			"net/minecraft/block/state/BlockState getPropertyValue (Lnet/minecraft/block/Block;Lnet/minecraft/block/properties/IProperty;)Ljava/lang/String;",
			"net/minecraft/block/state/BlockState getValidStates ()Lcom/google/common/collect/ImmutableList;",
			"net/minecraft/block/state/BlockState getAllowedValues ()Ljava/util/List;",
			"net/minecraft/block/state/BlockState getBaseState ()Lnet/minecraft/block/state/IBlockState;",
			"net/minecraft/block/state/BlockState getBlock ()Lnet/minecraft/block/Block;",
			"net/minecraft/block/state/BlockState getProperties ()Ljava/util/Collection;"			
			},
			depends={
			"net/minecraft/block/state/BlockState",
			"net/minecraft/block/Block",
			"net/minecraft/block/state/IBlockState"
			})
	public boolean processBlockStateClass()
	{
		ClassNode blockState = getClassNodeFromMapping("net/minecraft/block/state/BlockState");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		if (!MeddleUtil.notNull(blockState, iBlockState, block)) return false;
		
		List<MethodNode> methods = new ArrayList<MethodNode>();
		
		
		// public BlockState(Block, IProperty...)
		String iProperty = null;
		for (MethodNode method : blockState.methods) {
			if (!method.name.equals("<init>")) continue;
			if (!method.desc.startsWith("(L" + block.name + ";[L")) continue;
			Type t = Type.getMethodType(method.desc);
			if (t.getArgumentTypes().length != 2) continue;
			if (t.getArgumentTypes()[1].getSort() != Type.ARRAY) continue;
			iProperty = t.getArgumentTypes()[1].getElementType().getClassName();			
		}
		if (iProperty != null) {
			addClassMapping("net/minecraft/block/properties/IProperty", iProperty);
		}
		
		
		// public static String getPropertyValue(Block param0, IProperty param1)
		// Note: Not an MCP name
		methods = getMatchingMethods(blockState, null, "(L" + block.name + ";L" + iProperty + ";)Ljava/lang/String;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/BlockState getPropertyValue (Lnet/minecraft/block/Block;Lnet/minecraft/block/properties/IProperty;)Ljava/lang/String;",
					blockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public com/google/common/collect/ImmutableList getValidStates()
		methods = getMatchingMethods(blockState, null, "()Lcom/google/common/collect/ImmutableList;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/BlockState getValidStates ()Lcom/google/common/collect/ImmutableList;",
					blockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// private java/util/List getAllowedValues()
		methods = getMatchingMethods(blockState, null, "()Ljava/util/List;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/BlockState getAllowedValues ()Ljava/util/List;",
					blockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public IBlockState getBaseState()
		methods = getMatchingMethods(blockState, null, "()L" + iBlockState.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/BlockState getBaseState ()Lnet/minecraft/block/state/IBlockState;",
					blockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public Block getBlock()
		methods = getMatchingMethods(blockState, null, "()L" + block.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/BlockState getBlock ()Lnet/minecraft/block/Block;",
					blockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public java/util/Collection getProperties()
		methods = getMatchingMethods(blockState, null, "()Ljava/util/Collection;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/BlockState getProperties ()Ljava/util/Collection;",
					blockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// getProperty in client mappings

		
		return true;
	}
	


	@Mapping(providesFields={
			"net/minecraft/creativetab/CreativeTabs tabBlock Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabDecorations Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabRedstone Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabTransport Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabMisc Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabAllSearch Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabFood Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabTools Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabCombat Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabBrewing Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabMaterials Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/creativetab/CreativeTabs tabInventory Lnet/minecraft/creativetab/CreativeTabs;"
	},
	depends="net/minecraft/creativetab/CreativeTabs")
	public boolean getCreativeTabs()
	{
		ClassNode creativeTabs = getClassNodeFromMapping("net/minecraft/creativetab/CreativeTabs");
		if (creativeTabs == null) return false;

		List<MethodNode> methods = getMatchingMethods(creativeTabs, "<clinit>", "()V");
		if (methods.size() != 1) return false;

		Map<String, String> tabMap = new HashMap<String, String>();

		String name = null;
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext())
		{
			if (name == null) { name = getLdcString(insn); continue; }
			if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
			FieldInsnNode fn = (FieldInsnNode)insn;
			if (fn.desc.equals("L" + creativeTabs.name + ";")) {
				tabMap.put(name,  fn.name);
				name = null;
			}
		}

		Map<String, String> tabToFieldMap = new HashMap<String, String>() {{
			put("buildingBlocks", "tabBlock");
			put("decorations", "tabDecorations");
			put("redstone", "tabRedstone");
			put("transportation", "tabTransport");
			put("misc", "tabMisc");
			put("search", "tabAllSearch");
			put("food", "tabFood");
			put("tools", "tabTools");
			put("combat", "tabCombat");
			put("brewing", "tabBrewing");
			put("materials", "tabMaterials");
			put("inventory", "tabInventory");
		}};

		for (String key : tabMap.keySet()) {
			if (tabToFieldMap.containsKey(key)) {
				String mappedField = tabToFieldMap.get(key);
				String unmappedField = tabMap.get(key);
				addFieldMapping("net/minecraft/creativetab/CreativeTabs " + mappedField + " Lnet/minecraft/creativetab/CreativeTabs;",
						creativeTabs.name + " " + unmappedField + " L" + creativeTabs.name + ";");
			}
		}


		return true;
	}



	@Mapping(provides={
			"net/minecraft/util/EnumFacing",
			"net/minecraft/util/ItemUseResult",  		// NEW
			"net/minecraft/util/ObjectActionHolder", 	// NEW
			"net/minecraft/util/MainOrOffHand", 		// NEW
			"net/minecraft/creativetab/CreativeTabs",
			"net/minecraft/util/RegistryNamespaced",
			"net/minecraft/item/state/IItemState",		// NEW
			"net/minecraft/util/MovingObjectPosition",
			"net/minecraft/util/ResourceLocation"},
			providesMethods={
			"net/minecraft/item/Item getMaxStackSize ()I",
			"net/minecraft/item/Item setMaxStackSize (I)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item setCreativeTab (Lnet/minecraft/creativetab/CreativeTabs;)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item registerItem (ILjava/lang/String;Lnet/minecraft/item/Item;)V",
			"net/minecraft/item/Item registerItem (ILnet/minecraft/util/ResourceLocation;Lnet/minecraft/item/Item;)V",
			"net/minecraft/item/Item registerItemBlock (Lnet/minecraft/block/Block;Lnet/minecraft/item/Item;)V",
			"net/minecraft/item/Item registerItemBlock (Lnet/minecraft/block/Block;)V",
			"net/minecraft/item/Item onItemRightClick (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;)Lnet/minecraft/util/ObjectActionHolder;",
			"net/minecraft/item/Item onItemUse (Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/util/EnumFacing;FFF)Lnet/minecraft/util/ItemUseResult;",
			"net/minecraft/item/Item onItemUseFinish (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/Item onBlockDestroyed (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/EntityLivingBase;)Z"
			},
			providesFields={
			"net/minecraft/item/Item maxStackSize I",
			"net/minecraft/item/Item itemRegistry Lnet/minecraft/util/RegistryNamespaced;"
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/block/Block",
			"net/minecraft/item/ItemStack",
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/entity/EntityLivingBase",
			"net/minecraft/block/state/IBlockState"
			})
	public boolean processItemClass()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		if (!MeddleUtil.notNull(item, block, itemStack, world, blockPos, entityPlayer, entityLivingBase, iBlockState)) return false;

		String mainOrOffHand = null;
		String enumFacing = null;
		String itemUseResult = null;

		ClassNode objectActionHolder = null;
		String itemState = null;
		String resourceLocation = null;

		//public ItemUseResult onItemUse(ItemStack, EntityPlayer, World, BlockPos, MainOrOffHand, EnumFacing, float, float, float)
		for (MethodNode method : item.methods) {
			if (!checkMethodParameters(method, Type.OBJECT, Type.OBJECT, Type.OBJECT, Type.OBJECT, Type.OBJECT, Type.OBJECT, Type.FLOAT, Type.FLOAT, Type.FLOAT)) continue;
			String test = "(L" + itemStack.name + ";L" + entityPlayer.name + ";L" + world.name + ";L" + blockPos.name + ";L";
			if (!method.desc.startsWith(test)) continue;

			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();

			addClassMapping("net/minecraft/util/ItemUseResult", itemUseResult = t.getReturnType().getClassName());
			addClassMapping("net/minecraft/util/MainOrOffHand", mainOrOffHand = args[4].getClassName());
			addClassMapping("net/minecraft/util/EnumFacing", enumFacing = args[5].getClassName());
			addMethodMapping("net/minecraft/item/Item onItemUse (Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/util/EnumFacing;FFF)Lnet/minecraft/util/ItemUseResult;",
					item.name + " " + method.name + " " + method.desc);
		}

		// public ObjectActionHolderThing onItemRightClick(ItemStack, World, EntityPlayer, MainOrOffHand)
		for (MethodNode method : item.methods) {
			String test = "(L" + itemStack.name + ";L" + world.name + ";L" + entityPlayer.name + ";L" + mainOrOffHand + ";)";
			if (!method.desc.startsWith(test)) continue;
			Type t = Type.getMethodType(method.desc).getReturnType();

			objectActionHolder = getClassNode(t.getClassName());
			addClassMapping("net/minecraft/util/ObjectActionHolder", objectActionHolder);
			addMethodMapping("net/minecraft/item/Item onItemRightClick (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;)Lnet/minecraft/util/ObjectActionHolder;",
					item.name + " " + method.name + " " + method.desc);
		}

		List<MethodNode> methods = new ArrayList<MethodNode>();
		
		
		// private static void registerItem(int, ResourceLocation, Item)
		// private static void registerItem(int, String, Item)
		methods.clear();
		for (MethodNode method : item.methods) {
			if ((method.access & Opcodes.ACC_STATIC) == 0) continue;			
			if (!checkMethodParameters(method, Type.INT, Type.OBJECT, Type.OBJECT)) continue;
			if (!method.desc.startsWith("(IL") || !method.desc.endsWith(";L" + item.name + ";)V")) continue;
			methods.add(method);
		}
		if (methods.size() == 2) {
			for (MethodNode method : methods) {
				String className = Type.getMethodType(method.desc).getArgumentTypes()[1].getClassName();							
			
				if (className.equals("java.lang.String")) {
					addMethodMapping("net/minecraft/item/Item registerItem (ILjava/lang/String;Lnet/minecraft/item/Item;)V",
							item.name + " " + method.name + " " + method.desc);
				}
				else {							
					if (searchConstantPoolForStrings(className, "minecraft")) {
						resourceLocation = className;
						addClassMapping("net/minecraft/util/ResourceLocation", className);
					}			
					addMethodMapping("net/minecraft/item/Item registerItem (ILnet/minecraft/util/ResourceLocation;Lnet/minecraft/item/Item;)V",
							item.name + " " + method.name + " " + method.desc);
				}
			}
		}
		
		
		// protected static void registerItemBlock(Block blockIn, Item itemIn)
		methods = getMatchingMethods(item,  null, "(L" + block.name + ";L" + item.name + ";)V");
		methods = removeMethodsWithoutFlags(methods,  Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item registerItemBlock (Lnet/minecraft/block/Block;Lnet/minecraft/item/Item;)V",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// private static void registerItemBlock(Block blockIn)
		methods = getMatchingMethods(item,  null, "(L" + block.name + ";)V");
		methods = removeMethodsWithoutFlags(methods,  Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item registerItemBlock (Lnet/minecraft/block/Block;)V",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		

		for (FieldNode field : item.fields) {
			if ((field.access & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC)) != (Opcodes.ACC_FINAL + Opcodes.ACC_STATIC)) continue;
			String className = Type.getType(field.desc).getClassName();
			if (className.contains(".")) continue;

			// Item.itemRegistry
			if (searchConstantPoolForClasses(className, "com.google.common.collect.BiMap", "com.google.common.collect.HashBiMap")) {
				addClassMapping("net/minecraft/util/RegistryNamespaced", className);
				addFieldMapping("net/minecraft/item/Item itemRegistry Lnet/minecraft/util/RegistryNamespaced;", 
						item.name + " " + field.name + " " + field.desc);
				continue;
			}
		}


		// public final void addItemState(ResourceLocation, IItemState)
		for (MethodNode method : item.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length != 2) continue;
			if (!args[0].getClassName().equals(resourceLocation)) continue;

			String className = args[1].getClassName();
			ClassNode cn = getClassNode(className);
			if (cn == null) continue;
			if ((cn.access & Opcodes.ACC_INTERFACE) == 0) continue;

			addClassMapping("net/minecraft/item/state/IItemState", className);
			break;
		}


		//protected MovingObjectPosition getMovingObjectPositionFromPlayer(World, EntityPlayer, boolean)
		for (MethodNode method : item.methods) {
			if (!method.desc.startsWith("(L" + world.name + ";L" + entityPlayer.name + ";Z)")) continue;
			Type t = Type.getMethodType(method.desc).getReturnType();
			if (t.getSort() != Type.OBJECT) continue;
			if (searchConstantPoolForStrings(t.getClassName(), "HitResult{type=")) {
				addClassMapping("net/minecraft/util/MovingObjectPosition", t.getClassName());
				break;
			}
		}



		ClassNode creativeTab = null;

		// Get net.minecraft.creativetab.CreativeTabs
		for (FieldNode field : item.fields) {
			Type t = Type.getType(field.desc);
			if (t.getSort() != Type.OBJECT) continue;
			String className = t.getClassName();
			if (className.contains(".")) continue;
			if (DynamicMappings.reverseClassMappings.containsKey(className)) continue;
			if (searchConstantPoolForStrings(className, "buildingBlocks", "decorations", "redstone")) {
				addClassMapping("net/minecraft/creativetab/CreativeTabs", className);
				creativeTab = getClassNode(className);
			}
		}


		// Get Item.setCreativeTab()
		if (creativeTab != null)
		{
			String setCreativeTabDesc = "(L" + creativeTab.name + ";)L" + item.name + ";";
			methods = getMatchingMethods(item, null, setCreativeTabDesc);
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/item/Item setCreativeTab (Lnet/minecraft/creativetab/CreativeTabs;)Lnet/minecraft/item/Item;",
						item.name + " " + methods.get(0).name + " " + methods.get(0).desc);	
			}
		}


		String maxStackSizeField = null;

		// Get maxStackSize field
		List<MethodNode> initMethod = getMatchingMethods(item, "<init>", "()V");
		if (initMethod.size() == 1) {
			for (AbstractInsnNode insn = initMethod.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (!(insn instanceof IntInsnNode)) continue;
				IntInsnNode bipush = (IntInsnNode)insn;
				if (bipush.operand != 64) continue;
				if (insn.getNext().getOpcode() != Opcodes.PUTFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn.getNext();
				if (!fn.desc.equals("I")) continue;
				maxStackSizeField = fn.name;
				addFieldMapping("net/minecraft/item/Item maxStackSize I", item.name + " " + maxStackSizeField + " I");
				break;
			}
		}


		// get getMaxStackSize() and setMaxStackSize(I) methods
		if (maxStackSizeField != null)
		{
			boolean foundGetter = false;

			List<MethodNode> intGetters = getMatchingMethods(item, null, "()I");
			for (MethodNode method : intGetters) {
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() < 0) continue;
					// First real instruction should be ALOAD_0
					if (insn.getOpcode() != Opcodes.ALOAD) break;
					insn = insn.getNext();
					// Next should be GETFIELD
					if (insn.getOpcode() != Opcodes.GETFIELD) break;

					FieldInsnNode fn = (FieldInsnNode)insn;
					if (fn.name.equals(maxStackSizeField)) {
						addMethodMapping("net/minecraft/item/Item getMaxStackSize ()I", item.name + " " + method.name + " " + method.desc);
						foundGetter = true;
						break;
					}
				}
				if (foundGetter) break;
			}

			boolean foundSetter = false;

			List<MethodNode> intSetters = getMatchingMethods(item, null, "(I)L" + item.name + ";");
			for (MethodNode method : intSetters) {
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() < 0) continue;
					if (insn.getOpcode() != Opcodes.ALOAD) break;
					insn = insn.getNext();
					if (insn.getOpcode() != Opcodes.ILOAD) break;
					insn = insn.getNext();
					if (insn.getOpcode() != Opcodes.PUTFIELD) break;

					FieldInsnNode fn = (FieldInsnNode)insn;
					if (fn.name.equals(maxStackSizeField)) {
						addMethodMapping("net/minecraft/item/Item setMaxStackSize (I)Lnet/minecraft/item/Item;", item.name + " " + method.name + " " + method.desc);
						foundSetter = true;
						break;
					}
				}
				if (foundSetter) break;
			}
		}


		// public ItemStack onItemUseFinish(ItemStack stack, World worldIn, EntityLivingBase playerIn)
		methods = getMatchingMethods(item, null, assembleDescriptor("(", itemStack, world, entityLivingBase, ")", itemStack));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item onItemUseFinish (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/item/ItemStack;",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean onBlockDestroyed(ItemStack param0, World param1, IBlockState param2, BlockPos param3, EntityLivingBase param4)
		methods = getMatchingMethods(item, null, assembleDescriptor("(", itemStack, world, iBlockState, blockPos, entityLivingBase, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item onBlockDestroyed (Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/EntityLivingBase;)Z",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}

		return true;
	}

	
	
	
	@Mapping(provides={
			"net/minecraft/util/EnumFacing$AxisDirection",
			"net/minecraft/util/EnumFacing$Axis"
			},
			providesFields={
			"net/minecraft/util/EnumFacing DOWN Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing UP Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing NORTH Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing SOUTH Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing WEST Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing EAST Lnet/minecraft/util/EnumFacing;"
			},
			depends={
			"net/minecraft/util/EnumFacing"			
			})
	public boolean processEnumFacingClass()
	{
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		if (!MeddleUtil.notNull(enumFacing)) return false;	
		
		List<FieldNode> fields = getMatchingFields(enumFacing, null, "L" + enumFacing.name + ";");
		if (fields.size() == 6) {
			
			String[] names = new String[] { "DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST" };
			
			for (int n = 0; n < 6; n++) {				
				addFieldMapping("net/minecraft/util/EnumFacing " + names[n] + " Lnet/minecraft/util/EnumFacing;",
						enumFacing.name + " " + fields.get(n).name + " " + fields.get(n).desc);
			}
		}	
				
		
		// Search fields to identify EnumFacing.AxisDirection and EnumFacing.Axis
		Set<String> innerClasses = new HashSet<>();
		for (FieldNode fn : enumFacing.fields) {
			if (fn.desc.startsWith("L" + enumFacing.name + "$")) innerClasses.add(fn.desc.substring(1, fn.desc.length() - 1));			
		}
		if (innerClasses.size() == 2) {
			String axisDirection_name = null;
			String axis_name = null;
			for (String s : innerClasses) {
				if (axisDirection_name == null && searchConstantPoolForStrings(s, "POSITIVE", "NEGATIVE")) axisDirection_name = s;
				else if (axis_name == null && searchConstantPoolForStrings(s, "X", "Y", "Z")) axis_name = s;
			}

			// If we found both classes then everything should be as expected
			if (axisDirection_name != null && axis_name != null) {
				addClassMapping("net/minecraft/util/EnumFacing$AxisDirection", axisDirection_name);
				addClassMapping("net/minecraft/util/EnumFacing$Axis", axis_name);				
			}
		}
		
		
		return true;
	}
	
	
	@Mapping(providesMethods="net/minecraft/util/EnumFacing$AxisDirection getOffset ()I",
			providesFields={
			"net/minecraft/util/EnumFacing$AxisDirection POSITIVE Lnet/minecraft/util/EnumFacing$AxisDirection;",
			"net/minecraft/util/EnumFacing$AxisDirection NEGATIVE Lnet/minecraft/util/EnumFacing$AxisDirection;",
			},
			depends={
			"net/minecraft/util/EnumFacing$AxisDirection",
			})
	public boolean processEnumFacingAxisDirectionClass()
	{
		ClassNode axisDirection = getClassNodeFromMapping("net/minecraft/util/EnumFacing$AxisDirection");
		if (!MeddleUtil.notNull(axisDirection)) return false;
	
		MethodNode clinit = getMethodNode(axisDirection, "--- <clinit> ()V");
		if (clinit != null) {
			String lastString = null;
			for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				String string = getLdcString(insn);
				if (string != null && lastString == null) {
					lastString = string;
					continue;
				}
				
				if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (!fn.owner.equals(axisDirection.name) || !fn.desc.equals("L" + axisDirection.name + ";")) continue;
				
				if (lastString.equals("POSITIVE")) {
					addFieldMapping("net/minecraft/util/EnumFacing$AxisDirection POSITIVE Lnet/minecraft/util/EnumFacing$AxisDirection;", fn);
				}
				else if (lastString.equals("NEGATIVE")) {
					addFieldMapping("net/minecraft/util/EnumFacing$AxisDirection NEGATIVE Lnet/minecraft/util/EnumFacing$AxisDirection;", fn);
				}				
				lastString = null;			
			}
		}
		
		List<MethodNode> methods = getMatchingMethods(axisDirection, null, "()I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/EnumFacing$AxisDirection getOffset ()I",
					axisDirection.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}
	
	
	public Map<String, FieldInsnNode> extractEnumFieldsWithNames(ClassNode cn)
	{
		Map<String, FieldInsnNode> enumFields = new HashMap<>();
		
		MethodNode clinit = getMethodNode(cn, "--- <clinit> ()V");
		if (clinit != null) {
			String lastString = null;
			for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				String string = getLdcString(insn);
				if (string != null && lastString == null) {
					lastString = string;
					continue;
				}
				
				if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (!fn.owner.equals(cn.name) || !fn.desc.equals("L" + cn.name + ";")) continue;
				
				enumFields.put(lastString, fn);		
				lastString = null;			
			}
		}
		
		return enumFields;
	}
	
	
	@Mapping(provides="net/minecraft/util/EnumFacing$Plane",
			providesMethods={},
			providesFields={
			"net/minecraft/util/EnumFacing$Axis X Lnet/minecraft/util/EnumFacing$Axis;",
			"net/minecraft/util/EnumFacing$Axis Y Lnet/minecraft/util/EnumFacing$Axis;",
			"net/minecraft/util/EnumFacing$Axis Z Lnet/minecraft/util/EnumFacing$Axis;"
			},
			depends={
			"net/minecraft/util/EnumFacing",
			"net/minecraft/util/EnumFacing$Axis"
			})
	public boolean processEnumFacingAxisClass()
	{
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		ClassNode axis = getClassNodeFromMapping("net/minecraft/util/EnumFacing$Axis");
		if (!MeddleUtil.notNull(enumFacing, axis)) return false;
		
		Map<String, FieldInsnNode> enumFields = extractEnumFieldsWithNames(axis);
		if (enumFields.keySet().size() == 3) {
			for (String key : enumFields.keySet()) {
				if ("X".equals(key) || "Y".equals(key) || "Z".equals(key))
					addFieldMapping("net/minecraft/util/EnumFacing$Axis " + key + " Lnet/minecraft/util/EnumFacing$Axis;",
							axis.name + " " + enumFields.get(key).name + " " + enumFields.get(key).desc); 
			}
		}
		
		List<FieldNode> fields = getMatchingFields(axis, null, null);
		for (Iterator<FieldNode> it = fields.iterator(); it.hasNext();) {
			FieldNode field = it.next();
			
			Type t = Type.getType(field.desc);
			if (t.getSort() != Type.OBJECT) continue;
			String className = t.getClassName();
			if (className.equals(axis.name)) continue;
			else if (className.equals("java.lang.String")) continue;
			else if (className.equals("java.util.Map")) continue;			
			else if (!className.startsWith(enumFacing.name)) continue;
			
			if (searchConstantPoolForStrings(className, "HORIZONTAL", "VERTICAL")) {
				addClassMapping("net/minecraft/util/EnumFacing$Plane", className);
				break;
			}
		}
		
		
		// TODO - Methods
		
		return true;
	}
	
	
	@Mapping(providesMethods={
			"net/minecraft/util/EnumFacing$Plane facings ()[Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing$Plane random (Ljava/util/Random;)Lnet/minecraft/util/EnumFacing;",
			"net/minecraft/util/EnumFacing$Plane apply (Lnet/minecraft/util/EnumFacing;)Z"
			},
			providesFields={
			"net/minecraft/util/EnumFacing$Plane HORIZONTAL Lnet/minecraft/util/EnumFacing$Plane;",
			"net/minecraft/util/EnumFacing$Plane VERTICAL Lnet/minecraft/util/EnumFacing$Plane;"
			},
			depends={
			"net/minecraft/util/EnumFacing",
			"net/minecraft/util/EnumFacing$Plane"
			})
	public boolean processEnumFacingPlaneClass()
	{
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		ClassNode plane = getClassNodeFromMapping("net/minecraft/util/EnumFacing$Plane");
		if (!MeddleUtil.notNull(enumFacing)) return false;
		
		
		// Map enum fields
		Map<String, FieldInsnNode> enumFields = extractEnumFieldsWithNames(plane);
		if (enumFields.keySet().size() == 2) {
			for (String key : enumFields.keySet()) {
				if ("HORIZONTAL".equals(key) || "VERTICAL".equals(key))
					addFieldMapping("net/minecraft/util/EnumFacing$Plane " + key + " Lnet/minecraft/util/EnumFacing$Plane;",
							plane.name + " " + enumFields.get(key).name + " " + enumFields.get(key).desc); 
			}
		}	
		
		
		// public EnumFacing[] facings()
		List<MethodNode> methods = getMatchingMethods(plane, null, "()[L" + enumFacing.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/EnumFacing$Plane facings ()[Lnet/minecraft/util/EnumFacing;",
					plane.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public EnumFacing random(Random rand)
		methods = getMatchingMethods(plane, null, "(Ljava/util/Random;)L" + enumFacing.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/EnumFacing$Plane random (Ljava/util/Random;)Lnet/minecraft/util/EnumFacing;",
					plane.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean apply(EnumFacing facing)
		methods = getMatchingMethods(plane, null, "(L" + enumFacing.name + ";)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/EnumFacing$Plane apply (Lnet/minecraft/util/EnumFacing;)Z",
					plane.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
		
	}
	
	
	@Mapping(providesFields={
			"net/minecraft/util/EnumFacing index I",
			"net/minecraft/util/EnumFacing opposite I",
			"net/minecraft/util/EnumFacing horizontalIndex I",
			"net/minecraft/util/EnumFacing name Ljava/lang/String;",
			"net/minecraft/util/EnumFacing axisDirection Lnet/minecraft/util/EnumFacing$AxisDirection;",
			"net/minecraft/util/EnumFacing axis Lnet/minecraft/util/EnumFacing$Axis;",
			"net/minecraft/util/EnumFacing directionVec Lnet/minecraft/util/Vec3i;"
			},
			providesMethods={
			"net/minecraft/util/EnumFacing getIndex ()I",
			"net/minecraft/util/EnumFacing getHorizontalIndex ()I"
			},
			depends={
			"net/minecraft/util/EnumFacing",
			"net/minecraft/util/EnumFacing$AxisDirection",
			"net/minecraft/util/EnumFacing$Axis",
			"net/minecraft/util/Vec3i"
			})
	public boolean processEnumFacingClass2()
	{
		// Using a second method to process EnumFacing since processBlockPosClass needs 
		// EnumFacing fields, but processBlockPosClass produces Vec3i, which we need to 
		// process <init>
		
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		ClassNode axis = getClassNodeFromMapping("net/minecraft/util/EnumFacing$Axis");
		ClassNode axisDirection = getClassNodeFromMapping("net/minecraft/util/EnumFacing$AxisDirection");
		ClassNode vec3i = getClassNodeFromMapping("net/minecraft/util/Vec3i");
		if (!MeddleUtil.notNull(enumFacing, vec3i, axis, axisDirection)) return false;	
		
		// Map of obfuscated to deobfuscated
		Map<String, String> fieldMap = new HashMap<>();		
		
		// private EnumFacing(int indexIn, int oppositeIn, int horizontalIndexIn, String nameIn, EnumFacing.AxisDirection axisDirectionIn, EnumFacing.Axis axisIn, Vec3i directionVecIn)
		List<MethodNode> methods = getMatchingMethods(enumFacing, "<init>", assembleDescriptor("(Ljava/lang/String;IIIILjava/lang/String;", axisDirection, axis, vec3i, ")V"));
		if (methods.size() == 1) 
		{
			MethodNode method = methods.get(0);
			List<AbstractInsnNode[]> inits = new ArrayList<>();
			
			// Extract all ALOAD_0, ALOAD/ILOAD, PUTFIELD sequences
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				AbstractInsnNode[] nodes = getInsnNodeSequenceArray(insn, VarInsnNode.class, VarInsnNode.class, FieldInsnNode.class);
				if (nodes != null) {
					FieldInsnNode fn = (FieldInsnNode)nodes[2];
					if (((VarInsnNode)nodes[0]).var == 0 && fn.getOpcode() == Opcodes.PUTFIELD && fn.owner.equals(enumFacing.name)) {
						inits.add(nodes);
						insn = nodes[2];
					}
				}
			}
			
			// <init> should be setting 7 variables
			if (inits.size() == 7) {
				for (AbstractInsnNode[] nodes : inits) {
					FieldInsnNode fn = (FieldInsnNode)nodes[2];
					int var = ((VarInsnNode)nodes[1]).var;
					
					switch (var) {
						// index
						case 3: 
							if (fn.desc.equals("I")) addFieldMapping("net/minecraft/util/EnumFacing index I", fn);
							fieldMap.put(fn.name, "index");
							break;
							
						// opposite
						case 4:
							if (fn.desc.equals("I")) addFieldMapping("net/minecraft/util/EnumFacing opposite I", fn);
							fieldMap.put(fn.name, "opposite");
							break;
							
						// horizontalIndex
						case 5:
							if (fn.desc.equals("I")) addFieldMapping("net/minecraft/util/EnumFacing horizontalIndex I", fn);
							fieldMap.put(fn.name, "horizontalIndex");
							break;
							
						// name
						case 6:
							if (fn.desc.equals("Ljava/lang/String;")) addFieldMapping("net/minecraft/util/EnumFacing name Ljava/lang/String;", fn);
							fieldMap.put(fn.name, "name");
							break;
							
						// axisDirection
						case 7:
							if (fn.desc.equals("L" + axisDirection.name + ";")) addFieldMapping("net/minecraft/util/EnumFacing axisDirection Lnet/minecraft/util/EnumFacing$AxisDirection;", fn);
							fieldMap.put(fn.name, "axisDirection");
							break;
							
						// axis
						case 8:
							if (fn.desc.equals("L" + axis.name + ";")) addFieldMapping("net/minecraft/util/EnumFacing axis Lnet/minecraft/util/EnumFacing$Axis;", fn);
							fieldMap.put(fn.name, "axis");
							break;
							
						// directionVec
						case 9:
							if (fn.desc.equals("L" + vec3i.name + ";")) addFieldMapping("net/minecraft/util/EnumFacing directionVec Lnet/minecraft/util/Vec3i;", fn);
							fieldMap.put(fn.name, "directionVec");
							break;
					}					
				}
			}
		}
		
		
		// public int getIndex()
		// public int getHorizontalIndex()
		methods = getMatchingMethods(enumFacing, null, "()I");
		for (MethodNode method : methods) {
			AbstractInsnNode[] nodes = getOpcodeSequenceArray(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.IRETURN);
			if (nodes != null) {
				if (((VarInsnNode)nodes[0]).var != 0) continue;
				FieldInsnNode fn = (FieldInsnNode)nodes[1];
				if (!fn.owner.equals(enumFacing.name) || !fn.desc.equals("I")) continue;
				
				String field = fieldMap.get(fn.name);
				if (field == null) continue;
				if (field.equals("index")) {
					addMethodMapping("net/minecraft/util/EnumFacing getIndex ()I", enumFacing.name + " " + method.name + " ()I");
				}
				else if (field.equals("horizontalIndex")) {
					addMethodMapping("net/minecraft/util/EnumFacing getHorizontalIndex ()I", enumFacing.name + " " + method.name + " ()I");
				}
			}
		}
		
		
		
		return true;
	}
	
	
	

	@Mapping(providesFields={
			"net/minecraft/world/World isRemote Z"
			},
			providesMethods={
			"net/minecraft/world/World setBlockState (Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
			"net/minecraft/world/World markBlockForUpdate (Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/state/IBlockState;I)V",
			"net/minecraft/world/World markBlockRangeForRenderUpdate (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/BlockPos;)V",
			"net/minecraft/world/World playAuxSFXAtEntity (Lnet/minecraft/entity/player/EntityPlayer;ILnet/minecraft/util/BlockPos;I)V",
			"net/minecraft/world/World addBlockEvent (Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/Block;II)V",
			"net/minecraft/block/Block onBlockEventReceived (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;II)Z",
			"net/minecraft/world/World setTileEntity (Lnet/minecraft/util/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
			"net/minecraft/world/World markChunkDirty (Lnet/minecraft/util/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
			"net/minecraft/world/World playSoundEffect (DDDLnet/minecraft/util/Sound;FF)V",
			"net/minecraft/world/World getEntitiesInAABBexcluding (Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;",
			"net/minecraft/world/World getEntitiesWithinAABBExcludingEntity (Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;",
			"net/minecraft/world/World getCollidingBoundingBoxes (Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;"
			},
			depends={
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/block/Block",
			"net/minecraft/tileentity/TileEntity",
			"net/minecraft/util/AxisAlignedBB",
			"net/minecraft/entity/Entity",
			"net/minecraft/util/Sound",
			})
	public boolean processWorldClass()
	{
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode tileEntity = getClassNodeFromMapping("net/minecraft/tileentity/TileEntity");
		ClassNode aabb = getClassNodeFromMapping("net/minecraft/util/AxisAlignedBB");
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");
		ClassNode sound = getClassNodeFromMapping("net/minecraft/util/Sound");
		if (!MeddleUtil.notNull(world, blockPos, iBlockState, entityPlayer, block, tileEntity, aabb, entity, sound)) return false;

		int count = 0;

		String isRemote = null;
		for (FieldNode fn : world.fields) {
			if ((fn.access & Opcodes.ACC_FINAL) == 0 || !fn.desc.equals("Z")) continue;
			isRemote = fn.name;
			count++;
		}
		if (count == 1) addFieldMapping("net/minecraft/world/World isRemote Z", world.name + " " + isRemote + " Z");
		else isRemote = null;


		// public boolean setBlockState(BlockPos pos, IBlockState newState, int flags)
		List<MethodNode> methods = getMatchingMethods(world, null, assembleDescriptor("(", blockPos, iBlockState, "I)Z"));
		//System.out.println(methods.size());
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/World setBlockState (Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
					world.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}	
		
		
		// public void markBlockForUpdate(BlockPos, IBlockState, IBlockState, int) 
		methods = getMatchingMethods(world, null, assembleDescriptor("(", blockPos, iBlockState, iBlockState, "I)V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/World markBlockForUpdate (Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/state/IBlockState;I)V",
					world.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		
		// public void markBlockRangeForRenderUpdate(BlockPos rangeMin, BlockPos rangeMax)
		methods = getMatchingMethods(world, null, assembleDescriptor("(", blockPos, blockPos, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/World markBlockRangeForRenderUpdate (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/BlockPos;)V",
					world.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public void playAuxSFXAtEntity(EntityPlayer player, int sfxType, BlockPos pos, int p_180498_4_)
		methods = getMatchingMethods(world, null, assembleDescriptor("(", entityPlayer, "I", blockPos, "I)V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/World playAuxSFXAtEntity (Lnet/minecraft/entity/player/EntityPlayer;ILnet/minecraft/util/BlockPos;I)V",
					world.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void addBlockEvent(BlockPos pos, Block blockIn, int eventID, int eventParam)
		methods = getMatchingMethods(world, null, assembleDescriptor("(", blockPos, block, "II)V"));
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			MethodNode method = it.next();
			// Filter out methods that are empty
			AbstractInsnNode insn = getNextRealOpcode(method.instructions.getFirst());
			if (insn != null && insn.getOpcode() == Opcodes.RETURN) it.remove();
		}
		if (methods.size() == 1) {		
			boolean found_onBlockEventReceived = false;
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			
			// public boolean onBlockEventReceived(World worldIn, BlockPos pos, IBlockState state, int eventID, int eventParam)
			String desc = assembleDescriptor("(", world, blockPos, iBlockState, "II)Z");			
			for (MethodInsnNode mn : nodes) {
				if (mn.owner.equals(block.name) && mn.desc.equals(desc)) {
					addMethodMapping("net/minecraft/block/Block onBlockEventReceived (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;II)Z",
							block.name + " " + mn.name + " " + mn.desc);
					found_onBlockEventReceived = true;
					break;
				}
			}
			
			if (found_onBlockEventReceived) {
				addMethodMapping("net/minecraft/world/World addBlockEvent (Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/Block;II)V",
						world.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		
		// public void setTileEntity(BlockPos pos, TileEntity tileEntityIn)
		// public void markChunkDirty(BlockPos pos, TileEntity unusedTileEntity)
		methods = getMatchingMethods(world, null, assembleDescriptor("(", blockPos, tileEntity, ")V"));
		if (methods.size() == 2) {
			List<VarInsnNode> vars1 = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), VarInsnNode.class);
			List<VarInsnNode> vars2 = getAllInsnNodesOfType(methods.get(1).instructions.getFirst(), VarInsnNode.class);
			
			// Remove any ALOADs that don't refer to variable 2
			for (Iterator<VarInsnNode> it = vars1.iterator(); it.hasNext();) {
				if (it.next().var != 2) it.remove();
			}
			for (Iterator<VarInsnNode> it = vars2.iterator(); it.hasNext();) {
				if (it.next().var != 2) it.remove();
			}
			
			MethodNode setTileEntity = null;
			MethodNode markChunkDirty = null;
			if (vars1.size() > 0 && vars2.size() == 0) {
				setTileEntity = methods.get(0);
				markChunkDirty = methods.get(1);				
			}
			else if (vars1.size() == 0 && vars2.size() > 0) {
				setTileEntity = methods.get(1);
				markChunkDirty = methods.get(0);				
			}			
			
			if (setTileEntity != null && markChunkDirty != null) {
				addMethodMapping("net/minecraft/world/World setTileEntity (Lnet/minecraft/util/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
						world.name + " " + setTileEntity.name + " " + setTileEntity.desc);
				addMethodMapping("net/minecraft/world/World markChunkDirty (Lnet/minecraft/util/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
						world.name + " " + markChunkDirty.name + " " + markChunkDirty.desc);
			}
		}
		
		// public void playSoundEffect(double x, double y, double z, Sound sound, float volume, float pitch)
		methods = getMatchingMethods(world, null, "(DDDL" + sound.name + ";FF)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/World playSoundEffect (DDDLnet/minecraft/util/Sound;FF)V",
					world.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		
		// public List getEntitiesInAABBexcluding(Entity entityIn, AxisAlignedBB boundingBox, Predicate predicate)
		MethodNode getEntitiesInAABBexcluding = null;
		methods = getMatchingMethods(world, null, assembleDescriptor("(", entity, aabb, "Lcom/google/common/base/Predicate;)Ljava/util/List;"));
		if (methods.size() == 1) {
			getEntitiesInAABBexcluding = methods.get(0);
			addMethodMapping("net/minecraft/world/World getEntitiesInAABBexcluding (Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;",
					world.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public List getEntitiesWithinAABBExcludingEntity(Entity entityIn, AxisAlignedBB bb)
		// public List getCollidingBoundingBoxes(Entity entityIn, AxisAlignedBB bb)
		methods = getMatchingMethods(world, null, assembleDescriptor("(", entity, aabb, ")Ljava/util/List;"));
		if (methods.size() == 2 && getEntitiesInAABBexcluding != null) {
			startLoop:
			for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
				MethodNode method = it.next();
				AbstractInsnNode[] nodes = null;
				if ((nodes = getOpcodeSequenceArray(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.GETSTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.ARETURN)) != null) {
					MethodInsnNode mn = (MethodInsnNode)nodes[4];
					if (mn.owner.equals(world.name) && mn.name.equals(getEntitiesInAABBexcluding.name) && mn.desc.equals(getEntitiesInAABBexcluding.desc)) {
						it.remove();
						addMethodMapping("net/minecraft/world/World getEntitiesWithinAABBExcludingEntity (Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;",
								world.name + " " + method.name + " " + method.desc);
						break startLoop;
					}
				}
			}
			
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/world/World getCollidingBoundingBoxes (Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;",
						world.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		
		return true;
	}



	@Mapping(provides={
			"net/minecraft/inventory/IInventory"
	},
	providesMethods={
			"net/minecraft/entity/player/EntityPlayer displayGUIChest (Lnet/minecraft/inventory/IInventory;)V"
	},
	dependsMethods={
			"net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z"
	},
	depends={
			"net/minecraft/block/BlockChest",
			"net/minecraft/entity/player/EntityPlayer"
	})
	public boolean processBlockChestClass()
	{
		ClassNode blockChest = getClassNodeFromMapping("net/minecraft/block/BlockChest");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		if (blockChest == null || entityPlayer == null) return false;

		MethodNode onBlockActivated = getMethodNodeFromMapping(blockChest, "net/minecraft/block/Block onBlockActivated (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;FFF)Z");
		if (onBlockActivated == null) return false;

		for (AbstractInsnNode insn = onBlockActivated.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode)) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;
			if (!mn.owner.equals(entityPlayer.name)) continue;
			Type t = Type.getMethodType(mn.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length < 1 || args[0].getSort() != Type.OBJECT) continue;

			String className = args[0].getClassName();
			ClassNode cn = getClassNode(className);
			if ((cn.access & Opcodes.ACC_INTERFACE) == 0) continue;

			addClassMapping("net/minecraft/inventory/IInventory", className);
			addMethodMapping("net/minecraft/entity/player/EntityPlayer displayGUIChest (Lnet/minecraft/inventory/IInventory;)V",
					entityPlayer.name + " " + mn.name + " " + mn.desc);
			break;
		}


		return true;
	}

	@Mapping(provides={
			"net/minecraft/world/IWorldNameable",
			"net/minecraft/util/IChatComponent"
	},
	providesMethods={
			"net/minecraft/world/IWorldNameable hasCustomName ()Z",
			"net/minecraft/world/IWorldNameable getName ()Ljava/lang/String;",
			"net/minecraft/world/IWorldNameable getDisplayName ()Lnet/minecraft/util/IChatComponent;"
	},
	depends={
			"net/minecraft/inventory/IInventory"
	})
	public boolean processIWorldNameable()
	{
		ClassNode inventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		if (inventory == null) return false;

		if (inventory.interfaces.size() != 1) return false;
		String className = inventory.interfaces.get(0);

		addClassMapping("net/minecraft/world/IWorldNameable", className);

		ClassNode worldNameable = getClassNode(className);
		if (worldNameable == null) return false;

		if (worldNameable.methods.size() != 3) return false;
		for (MethodNode method : worldNameable.methods) {
			Type t = Type.getMethodType(method.desc);
			Type returnType = t.getReturnType();

			if (returnType.getSort() == Type.BOOLEAN) {
				addMethodMapping("net/minecraft/world/IWorldNameable hasCustomName ()Z", worldNameable.name + " " + method.name + " ()Z");
				continue;
			}

			if (returnType.getSort() == Type.OBJECT) {
				if (returnType.getClassName().equals("java.lang.String")) {
					addMethodMapping("net/minecraft/world/IWorldNameable getName ()Ljava/lang/String;", worldNameable.name + " " + method.name + " " + method.desc);
					continue;
				}

				addClassMapping("net/minecraft/util/IChatComponent", returnType.getClassName());
				addMethodMapping("net/minecraft/world/IWorldNameable getDisplayName ()Lnet/minecraft/util/IChatComponent;", worldNameable.name + " " + method.name + " " + method.desc);
			}
		}


		return true;
	}


	@Mapping(provides="net/minecraft/util/ChatComponentText", depends="net/minecraft/server/MinecraftServer")
	public boolean getChatComponentTextClass()
	{
		ClassNode server = getClassNode("net/minecraft/server/MinecraftServer");
		if (server == null) return false;

		List<MethodNode> methods = getMatchingMethods(server, "run", "()V");
		if (methods.size() != 1) return false;

		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.NEW) continue;
			TypeInsnNode tn = (TypeInsnNode)insn;
			if (searchConstantPoolForStrings(tn.desc, "TextComponent{text=\'")) {
				addClassMapping("net/minecraft/util/ChatComponentText", tn.desc);
				return true;
			}
		}

		return false;
	}


	@Mapping(provides={
			"net/minecraft/inventory/InventoryBasic"
	},
	depends={
			"net/minecraft/entity/passive/EntityVillager",
			"net/minecraft/inventory/IInventory"
	})
	public boolean processEntityVillagerClass()
	{
		ClassNode villager = getClassNodeFromMapping("net/minecraft/entity/passive/EntityVillager");
		ClassNode inventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		if (villager == null || inventory == null) return false;

		for (MethodNode method : villager.methods) {
			if (!method.name.equals("<init>")) continue;

			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.NEW) continue;
				TypeInsnNode tn = (TypeInsnNode)insn;

				ClassNode node = getClassNode(tn.desc);
				if (node == null) continue;

				boolean isInventory = false;
				for (String iface : node.interfaces) {
					if (inventory.name.equals(iface)) isInventory = true;
				}

				if (!isInventory) continue;

				// TODO - Find more accurate way of detecting this class
				addClassMapping("net/minecraft/inventory/InventoryBasic", node.name);
				return true;
			}

		}


		return false;
	}



	@Mapping(providesFields={
			"net/minecraft/entity/player/EntityPlayer inventory Lnet/minecraft/entity/player/InventoryPlayer;"
			},
			depends={
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/entity/player/InventoryPlayer"
			})
	public boolean processEntityPlayerClass()
	{
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode inventoryPlayer = getClassNodeFromMapping("net/minecraft/entity/player/InventoryPlayer");
		if (entityPlayer == null || inventoryPlayer == null) return false;

		for (MethodNode method : entityPlayer.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length != 1 || args[0].getSort() != Type.OBJECT || t.getReturnType().getSort() != Type.VOID) continue;
			//System.out.println(method.name + " " + method.desc);
		}

		List<FieldNode> fields = getMatchingFields(entityPlayer, null, "L" + inventoryPlayer.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/entity/player/EntityPlayer inventory Lnet/minecraft/entity/player/InventoryPlayer;", 
					entityPlayer.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}


		return true;
	}



	


	@Mapping(provides={
			"net/minecraft/nbt/NBTTagCompound",
			"net/minecraft/nbt/NBTBase",
			"net/minecraft/nbt/NBTTagEnd",
			"net/minecraft/nbt/NBTTagByte",
			"net/minecraft/nbt/NBTTagShort",
			"net/minecraft/nbt/NBTTagInt",
			"net/minecraft/nbt/NBTTagLong",
			"net/minecraft/nbt/NBTTagFloat",
			"net/minecraft/nbt/NBTTagDouble",
			"net/minecraft/nbt/NBTTagByteArray",
			"net/minecraft/nbt/NBTTagString",
			"net/minecraft/nbt/NBTTagList",
			"net/minecraft/nbt/NBTTagIntArray"
	},
	providesMethods={
			"net/minecraft/item/ItemStack writeToNBT (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
			"net/minecraft/nbt/NBTBase createNewByType (B)Lnet/minecraft/nbt/NBTBase;"
	},
	depends={
			"net/minecraft/item/ItemStack"
	})
	public boolean getNBTClasses()
	{
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		if (itemStack == null) return false;

		String tagCompoundName = null;

		// Find: public NBTTagCompound writeToNBT(NBTTagCompound)
		for (MethodNode method : itemStack.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			Type returnType = t.getReturnType();

			if (args.length != 1) continue;
			if (args[0].getSort() != Type.OBJECT || returnType.getSort() != Type.OBJECT) continue;
			if (!args[0].getClassName().equals(returnType.getClassName())) continue;

			tagCompoundName = returnType.getClassName();

			if (searchConstantPoolForStrings(tagCompoundName, "Tried to read NBT tag with too high complexity, depth > 512")) {
				addClassMapping("net/minecraft/nbt/NBTTagCompound", tagCompoundName);
				addMethodMapping("net/minecraft/item/ItemStack writeToNBT (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
						itemStack.name + " " + method.name + " " + method.desc);
				break;
			}
			tagCompoundName = null;
		}
		if (tagCompoundName == null) return false;

		ClassNode tagCompound = getClassNode(tagCompoundName);
		if (tagCompound == null) return false;
		ClassNode tagBase = getClassNode(tagCompound.superName);
		if (tagBase == null) return false;

		if (!searchConstantPoolForStrings(tagBase.name, "END", "BYTE", "SHORT")) return false;

		addClassMapping("net/minecraft/nbt/NBTBase", tagBase.name);

		Map<Integer, String> nbtClassesMap = new HashMap<Integer, String>();

		// We want: protected static NBTBase createNewByType(byte id)
		for (MethodNode method : tagBase.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			Type returnType = t.getReturnType();

			if (args.length != 1) continue;
			if (args[0].getSort() != Type.BYTE) continue;
			if (!returnType.getClassName().equals(tagBase.name)) continue;

			addMethodMapping("net/minecraft/nbt/NBTBase createNewByType (B)Lnet/minecraft/nbt/NBTBase;", tagBase.name + " " + method.name + " " + method.desc);

			// Find the tableswitch to parse
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.TABLESWITCH) continue;
				TableSwitchInsnNode table = (TableSwitchInsnNode)insn;

				// There's 12 NBT types, otherwise this table isn't reliable
				if (table.labels.size() != 12) continue;

				for (int n = 0; n < 12; n++) {
					AbstractInsnNode destInsn = table.labels.get(n);
					AbstractInsnNode nextReal = getNextRealOpcode(destInsn);

					// Every jump target should be creating a new instance of an NBT type
					if (nextReal.getOpcode() != Opcodes.NEW) return false;
					nbtClassesMap.put(n, ((TypeInsnNode)nextReal).desc);
				}
			}
		}

		for (Integer key : nbtClassesMap.keySet()) {
			String className = nbtClassesMap.get(key);
			if (className == null) continue;

			switch (key) {
			case 0: addClassMapping("net/minecraft/nbt/NBTTagEnd", className);
			continue;
			case 1: addClassMapping("net/minecraft/nbt/NBTTagByte", className);
			continue;
			case 2: addClassMapping("net/minecraft/nbt/NBTTagShort", className);
			continue;
			case 3: addClassMapping("net/minecraft/nbt/NBTTagInt", className);
			continue;
			case 4: addClassMapping("net/minecraft/nbt/NBTTagLong", className);
			continue;
			case 5: addClassMapping("net/minecraft/nbt/NBTTagFloat", className);
			continue;
			case 6: addClassMapping("net/minecraft/nbt/NBTTagDouble", className);
			continue;
			case 7: addClassMapping("net/minecraft/nbt/NBTTagByteArray", className);
			continue;
			case 8: addClassMapping("net/minecraft/nbt/NBTTagString", className);
			continue;
			case 9: addClassMapping("net/minecraft/nbt/NBTTagList", className);
			continue;
			case 10: // Already added this earlier
				continue;
			case 11: addClassMapping("net/minecraft/nbt/NBTTagIntArray", className);
			continue;
			}
		}



		return true;
	}


	@Mapping(provides={
			"net/minecraft/nbt/NBTBase$NBTPrimitive"
	},
	providesMethods={
			"net/minecraft/nbt/NBTTagCompound getTagList (Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;",
			"net/minecraft/nbt/NBTBase hashCode ()I",
			"net/minecraft/nbt/NBTTagCompound tagCount ()I",
			"net/minecraft/nbt/NBTTagList tagCount ()I",
			"net/minecraft/nbt/NBTTagList getTagType ()I",
			"net/minecraft/nbt/NBTTagList getCompoundTagAt (I)Lnet/minecraft/nbt/NBTTagCompound;",
			"net/minecraft/nbt/NBTTagCompound getByte (Ljava/lang/String;)B",
			"net/minecraft/nbt/NBTTagCompound setByte (Ljava/lang/String;B)V",
			"net/minecraft/nbt/NBTTagCompound hasKey (Ljava/lang/String;I)Z",
			"net/minecraft/nbt/NBTTagCompound getTagType (Ljava/lang/String;)B",
			"net/minecraft/nbt/NBTTagList appendTag (Lnet/minecraft/nbt/NBTBase;)V",
			"net/minecraft/nbt/NBTTagCompound setTag (Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V",
			"net/minecraft/nbt/NBTTagCompound getString (Ljava/lang/String;)Ljava/lang/String;",
			"net/minecraft/nbt/NBTTagCompound setString (Ljava/lang/String;Ljava/lang/String;)V",
			"net/minecraft/nbt/NBTTagCompound getInteger (Ljava/lang/String;)I",
			"net/minecraft/nbt/NBTTagCompound setInteger (Ljava/lang/String;I)V",
			"net/minecraft/nbt/NBTTagCompound removeTag (Ljava/lang/String;)V"
	},
	providesFields={
			"net/minecraft/nbt/NBTTagList tagType B"
	},
	depends={
			"net/minecraft/nbt/NBTTagCompound",
			"net/minecraft/nbt/NBTTagList",
			"net/minecraft/nbt/NBTBase"
	})
	public boolean processNBTTagCompound()
	{
		ClassNode tagCompound = getClassNodeFromMapping("net/minecraft/nbt/NBTTagCompound");
		ClassNode tagList = getClassNodeFromMapping("net/minecraft/nbt/NBTTagList");
		ClassNode tagBase = getClassNodeFromMapping("net/minecraft/nbt/NBTBase");
		if (tagCompound == null || tagList == null || tagBase == null) return false;

		// public NBTTagList getTagList(String, int)
		List<MethodNode> methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;I)L" + tagList.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound getTagList (Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;",
					tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}

		// public int hashCode()
		String hashCode_name = null;
		methods = getMatchingMethods(tagBase, null, "()I");
		if (methods.size() == 1) {
			hashCode_name = methods.get(0).name;
			addMethodMapping("net/minecraft/nbt/NBTBase hashCode ()I", tagBase.name + " " + hashCode_name + " ()I");
		}


		// public NBTTagCompound.tagCount()I
		methods = getMatchingMethods(tagCompound, null, "()I");
		for (Iterator<MethodNode> iterator = methods.iterator(); iterator.hasNext();) {
			if (iterator.next().name.equals(hashCode_name)) iterator.remove();
		}
		if (methods.size() == 1) {		
			addMethodMapping("net/minecraft/nbt/NBTTagCompound tagCount ()I", tagCompound.name + " " +  methods.get(0).name + " ()I");
		}


		// public NBTTagList.getTagType()I
		methods = getMatchingMethods(tagList, null, "()I");
		for (Iterator<MethodNode> iterator = methods.iterator(); iterator.hasNext();) {
			MethodNode method = iterator.next();
			if (method.name.equals(hashCode_name)) { iterator.remove(); continue; }

			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.GETFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.owner.equals(tagList.name) && fn.desc.equals("B")) {
					addFieldMapping("net/minecraft/nbt/NBTTagList tagType B", tagList.name + " " + fn.name + " B");
					addMethodMapping("net/minecraft/nbt/NBTTagList getTagType ()I", tagList.name + " " + method.name + " ()I");
					iterator.remove();
					break;
				}
			}
		}

		// public NBTTagList.tagCount()I
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagList tagCount ()I", tagList.name + " " + methods.get(0).name + " ()I");
		}


		// public NBTTagList.getCompoundTagAt(I)Lnet/minecraft/nbt/NBTTagCompound;
		methods = getMatchingMethods(tagList,  null,  "(I)L" + tagCompound.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagList getCompoundTagAt (I)Lnet/minecraft/nbt/NBTTagCompound;",
					tagList.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}


		// public boolean hasKey(String key, int type)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;I)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound hasKey (Ljava/lang/String;I)Z",
					tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}



		// Find getByte and getTagType
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;)B");
		if (methods.size() == 2) 
		{
			for (MethodNode method : methods) {
				boolean hasBase = false;
				boolean hasPrimitive = false;
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() != Opcodes.CHECKCAST) continue;
					TypeInsnNode tn = (TypeInsnNode)insn;
					if (tn.desc.equals(tagBase.name)) hasBase = true;
					else if (tn.desc.startsWith(tagBase.name + "$")) {
						hasPrimitive = true;
						addClassMapping("net/minecraft/nbt/NBTBase$NBTPrimitive", tn.desc);
					}
				}
	
				// public byte getByte(String key)
				if (hasPrimitive && !hasBase) {
					addMethodMapping("net/minecraft/nbt/NBTTagCompound getByte (Ljava/lang/String;)B",
							tagCompound.name + " " + method.name + " " + method.desc);
				}
				// public byte getTagType(String key)
				else if (!hasPrimitive && hasBase) {
					addMethodMapping("net/minecraft/nbt/NBTTagCompound getTagType (Ljava/lang/String;)B",
							tagCompound.name + " " + method.name + " " + method.desc);
				}
			}
		}


		// public void setByte(String key, byte value)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;B)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound setByte (Ljava/lang/String;B)V",
					tagCompound.name + " " + methods.get(0).name + " (Ljava/lang/String;B)V");
		}


		// public void appendTag(NBTBase nbt)
		methods = getMatchingMethods(tagList, null, "(L" + tagBase.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagList appendTag (Lnet/minecraft/nbt/NBTBase;)V",
					tagList.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}


		// public void setTag(String key, NBTBase value)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;L" + tagBase.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound setTag (Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V",
					tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public String getString(String key)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;)Ljava/lang/String;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound getString (Ljava/lang/String;)Ljava/lang/String;",
					tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public void setString(String key, String value)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;Ljava/lang/String;)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound setString (Ljava/lang/String;Ljava/lang/String;)V",
					tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}

		
		// public int getInteger(String key)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;)I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound getInteger (Ljava/lang/String;)I",
					tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void setInteger(String key, int value)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;I)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound setInteger (Ljava/lang/String;I)V",
					tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void removeTag(String key)
		methods = getMatchingMethods(tagCompound, null, "(Ljava/lang/String;)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/nbt/NBTTagCompound removeTag (Ljava/lang/String;)V",
					tagCompound.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}


	@Mapping(providesFields={
			"net/minecraft/inventory/InventoryBasic slotsCount I",
			"net/minecraft/inventory/InventoryBasic inventoryTitle Ljava/lang/String;",
			"net/minecraft/inventory/InventoryBasic hasCustomName Z",
			"net/minecraft/inventory/InventoryBasic inventoryContent [Lnet/minecraft/item/ItemStack;"
	},
	providesMethods={
			"net/minecraft/inventory/IInventory getSizeInventory ()I",
			"net/minecraft/inventory/IInventory getInventoryStackLimit ()I",
			"net/minecraft/inventory/IInventory getFieldCount ()I",
			"net/minecraft/inventory/IInventory setInventorySlotContents (ILnet/minecraft/item/ItemStack;)V",
			"net/minecraft/inventory/IInventory getStackInSlot (I)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/IInventory getStackInSlotOnClosing (I)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/IInventory markDirty ()V",
			"net/minecraft/inventory/IInventory clear ()V",
			"net/minecraft/inventory/IInventory decrStackSize (II)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/IInventory isUseableByPlayer (Lnet/minecraft/entity/player/EntityPlayer;)Z",
			"net/minecraft/inventory/IInventory isItemValidForSlot (ILnet/minecraft/item/ItemStack;)Z",
			"net/minecraft/inventory/IInventory getField (I)I",
			"net/minecraft/inventory/IInventory setField (II)V"
	},
	depends={
			"net/minecraft/inventory/IInventory",
			"net/minecraft/inventory/InventoryBasic",
			"net/minecraft/item/ItemStack",
			"net/minecraft/entity/player/EntityPlayer"
	})
	public boolean processInventoryBasicClass()
	{
		ClassNode iInventory = getClassNodeFromMapping("net/minecraft/inventory/IInventory");
		ClassNode inventoryBasic = getClassNodeFromMapping("net/minecraft/inventory/InventoryBasic");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		if (!MeddleUtil.notNull(iInventory, inventoryBasic, itemStack, entityPlayer)) return false;

		String slotsCountField = null;
		String inventoryTitleField = null;
		String inventoryContentsField = null;
		String hasCustomNameField = null;

		List<FieldNode> fields = getMatchingFields(inventoryBasic, null, "I");
		if (fields.size() == 1) {
			slotsCountField = fields.get(0).name;
			addFieldMapping("net/minecraft/inventory/InventoryBasic slotsCount I",
					inventoryBasic.name + " " + slotsCountField + " I");
		}

		fields = getMatchingFields(inventoryBasic, null, "Ljava/lang/String;");
		if (fields.size() == 1) {
			inventoryTitleField = fields.get(0).name;
			addFieldMapping("net/minecraft/inventory/InventoryBasic inventoryTitle Ljava/lang/String;",
					inventoryBasic.name + " " + inventoryTitleField + " Ljava/lang/String;");
		}

		fields = getMatchingFields(inventoryBasic, null, "Z");
		if (fields.size() == 1) {
			hasCustomNameField = fields.get(0).name;
			addFieldMapping("net/minecraft/inventory/InventoryBasic hasCustomName Z",
					inventoryBasic.name + " " + hasCustomNameField + " Z");
		}

		fields = getMatchingFields(inventoryBasic, null, "[L" + itemStack.name + ";");
		if (fields.size() == 1) {
			inventoryContentsField = fields.get(0).name;
			addFieldMapping("net/minecraft/inventory/InventoryBasic inventoryContent [Lnet/minecraft/item/ItemStack;",
					inventoryBasic.name + " " + inventoryContentsField + " [L" + itemStack.name + ";");
		}


		// Parse methods that only return an int.
		// There should only be three.
		List<MethodNode> methods = getMatchingMethods(inventoryBasic, null, "()I");
		if (methods.size() != 3) return false;

		for (MethodNode method : methods)
		{
			AbstractInsnNode firstReal = getNextRealOpcode(method.instructions.getFirst());

			// Find IInventory.getSizeInventory()I
			if (firstReal.getOpcode() == Opcodes.ALOAD) {
				AbstractInsnNode nextNode = firstReal.getNext();
				if (nextNode != null && nextNode.getOpcode() == Opcodes.GETFIELD) {
					FieldInsnNode fn = (FieldInsnNode)nextNode;
					if (fn.owner.equals(inventoryBasic.name) && fn.name.equals(slotsCountField)) {
						addMethodMapping("net/minecraft/inventory/IInventory getSizeInventory ()I",
								iInventory.name + " " + method.name + " " + method.desc);
						continue;
					}
				}
			}

			// Find IInventory.getInventoryStackLimit()I
			if (firstReal instanceof IntInsnNode) {
				IntInsnNode in = (IntInsnNode)firstReal;
				if (in.operand == 64) {
					addMethodMapping("net/minecraft/inventory/IInventory getInventoryStackLimit ()I",
							iInventory.name + " " + method.name + " " + method.desc);
					continue;
				}
			}

			// Find IInventory.getFieldCount()I
			if (firstReal.getOpcode() == Opcodes.ICONST_0) {
				AbstractInsnNode nextNode = firstReal.getNext();
				if (nextNode != null && nextNode.getOpcode() == Opcodes.IRETURN) {
					addMethodMapping("net/minecraft/inventory/IInventory getFieldCount ()I",
							iInventory.name + " " + method.name + " " + method.desc);
					continue;
				}
			}
		}

		// public void setInventorySlotContents(int index, ItemStack stack)
		methods = getMatchingMethods(inventoryBasic, null, "(IL" + itemStack.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/IInventory setInventorySlotContents (ILnet/minecraft/item/ItemStack;)V",
					iInventory.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}


		// Get the two methods with decriptor (I)Lnet/minecraft/item/ItemStack;
		methods = getMatchingMethods(inventoryBasic, null, "(I)L" + itemStack.name + ";");
		if (methods.size() != 2) return false;
		for (MethodNode method : methods)
		{
			boolean foundStore = false;
			boolean foundLength = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() == Opcodes.AASTORE) foundStore = true;
				else if (insn.getOpcode() == Opcodes.ARRAYLENGTH) foundLength = true;
			}

			// public ItemStack getStackInSlot(int)
			if (foundLength && !foundStore) {
				addMethodMapping("net/minecraft/inventory/IInventory getStackInSlot (I)Lnet/minecraft/item/ItemStack;",
						iInventory.name + " " + method.name + " " + method.desc);
			}
			// ItemStack getStackInSlotOnClosing(int)
			else if (foundStore && !foundLength) {
				addMethodMapping("net/minecraft/inventory/IInventory getStackInSlotOnClosing (I)Lnet/minecraft/item/ItemStack;",
						iInventory.name + " " + method.name + " " + method.desc);
			}
		}


		methods = getMatchingMethods(inventoryBasic, null, "()V");
		if (methods.size() != 2) return false;
		for (MethodNode method : methods) {
			boolean hasList = false;
			boolean hasArray = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() == Opcodes.GETFIELD) {
					FieldInsnNode fn = (FieldInsnNode)insn;
					if (fn.owner.equals(inventoryBasic.name) && fn.desc.equals("Ljava/util/List;")) hasList = true;
					if (fn.owner.equals(inventoryBasic.name) && fn.desc.equals("[L" + itemStack.name + ";")) hasArray = true;
				}
			}

			// public void markDirty()
			if (hasList && !hasArray) {
				addMethodMapping("net/minecraft/inventory/IInventory markDirty ()V",
						iInventory.name + " " + method.name + " " + method.desc);
			}
			// public void clear()
			else if (!hasList && hasArray) {
				addMethodMapping("net/minecraft/inventory/IInventory clear ()V",
						iInventory.name + " " + method.name + " " + method.desc);
			}
		}
		
		
		// public ItemStack decrStackSize(int index, int count)
		methods = getMatchingMethods(inventoryBasic, null, "(II)L" + itemStack.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/IInventory decrStackSize (II)Lnet/minecraft/item/ItemStack;",
					iInventory.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		

		// boolean isUseableByPlayer(EntityPlayer player)
		methods = getMatchingMethods(inventoryBasic, null, "(L" + entityPlayer.name + ";)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/IInventory isUseableByPlayer (Lnet/minecraft/entity/player/EntityPlayer;)Z",
					iInventory.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// boolean isItemValidForSlot(int index, ItemStack stack)
		methods = getMatchingMethods(inventoryBasic, null, "(IL" + itemStack.name + ";)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/IInventory isItemValidForSlot (ILnet/minecraft/item/ItemStack;)Z",
					iInventory.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// int getField(int id)		
		methods = getMatchingMethods(inventoryBasic, null, "(I)I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/IInventory getField (I)I",
					iInventory.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// void setField(int id, int value)
		methods = getMatchingMethods(inventoryBasic, null, "(II)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/inventory/IInventory setField (II)V",
					iInventory.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	



	@Mapping(providesFields={
			"net/minecraft/item/Item unlocalizedName Ljava/lang/String;"
			},
			providesMethods={
			"net/minecraft/item/Item getIdFromItem (Lnet/minecraft/item/Item;)I",
			"net/minecraft/item/Item getItemById (I)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item getItemFromBlock (Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item getByNameOrId (Ljava/lang/String;)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item setUnlocalizedName (Ljava/lang/String;)Lnet/minecraft/item/Item;",
			"net/minecraft/item/Item getItemStackDisplayName (Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",
			"net/minecraft/item/Item getUnlocalizedNameInefficiently (Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",
			"net/minecraft/item/Item getUnlocalizedName (Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",
			"net/minecraft/item/Item getUnlocalizedName ()Ljava/lang/String;"
			},
			dependsMethods={
			"net/minecraft/item/ItemStack getDisplayName ()Ljava/lang/String;"
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/block/Block",
			"net/minecraft/item/ItemStack"
			})
	public boolean getItemClassMethods()
	{
		ClassNode item = getClassNode(getClassMapping("net/minecraft/item/Item"));
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode itemStack = getClassNode(getClassMapping("net/minecraft/item/ItemStack"));
		if (!MeddleUtil.notNull(item, block, itemStack)) return false;

		// Keep a local list because we'll remove them as we go to improve detection
		List<MethodNode> itemMethods = new ArrayList<MethodNode>();
		itemMethods.addAll(item.methods);

		List<MethodNode> methods;

		
		//public static int getIdFromItem(Item itemIn)
		methods = getMethodsWithDescriptor(itemMethods, "(L" + item.name + ";)I");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		//for (MethodNode mn : methods) System.out.println(mn.name + " " + mn.desc);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item getIdFromItem (Lnet/minecraft/item/Item;)I",
					item.name + " " + mn.name + " " + mn.desc);
		}
		// A hack since 15w34c duplicated the method for seemingly no reason
		else if (methods.size() == 2) {
			MethodNode mn1 = methods.get(0);
			MethodNode mn2 = methods.get(1);
			if (mn1.instructions.size() == mn2.instructions.size()) {
				int size = mn1.instructions.size();
				boolean nomatch = false;
				for (int n = 0; n < size; n++) {
					if (mn1.instructions.get(n).getOpcode() != mn2.instructions.get(n).getOpcode()) { nomatch = true; break; }
				}
				if (!nomatch) {
					// Use the second one, since it seems to be the original
					addMethodMapping("net/minecraft/item/Item getIdFromItem (Lnet/minecraft/item/Item;)I",
							item.name + " " + mn2.name + " " + mn2.desc);
					// Not going to put this in the 'provides' list in case it's removed, but we'll map it
					addMethodMapping("net/minecraft/item/Item getIdFromItem2 (Lnet/minecraft/item/Item;)I",
							item.name + " " + mn1.name + " " + mn1.desc);
				}
			}
		}


		//public static Item getItemById(int id)
		methods = getMethodsWithDescriptor(itemMethods, "(I)L" + item.name + ";");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item getItemById (I)Lnet/minecraft/item/Item;",
					item.name + " " + mn.name + " " + mn.desc);
		}


		//public static Item getItemFromBlock(Block blockIn)
		methods = getMethodsWithDescriptor(itemMethods, "(L" + block.name + ";)L" + item.name + ";");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item getItemFromBlock (Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;",
					item.name + " " + mn.name + " " + mn.desc);
		}


		//public static Item getByNameOrId(String id)
		methods = getMethodsWithDescriptor(itemMethods, "(Ljava/lang/String;)L" + item.name + ";");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item getByNameOrId (Ljava/lang/String;)Lnet/minecraft/item/Item;",
					item.name + " " + mn.name + " " + mn.desc);
		}


		// Item setUnlocalizedName(String)
		methods = getMethodsWithDescriptor(itemMethods, "(Ljava/lang/String;)L" + item.name + ";");
		methods = removeMethodsWithFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			itemMethods.remove(mn);
			addMethodMapping("net/minecraft/item/Item setUnlocalizedName (Ljava/lang/String;)Lnet/minecraft/item/Item;",
					item.name + " " + mn.name + " " + mn.desc);
			
			for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() == Opcodes.PUTFIELD) {
					FieldInsnNode fn = (FieldInsnNode)insn;
					if (fn.desc.equals("Ljava/lang/String;")) {
						addFieldMapping("net/minecraft/item/Item unlocalizedName Ljava/lang/String;", 
								item.name + " " + fn.name + " " + fn.desc);
						break;
					}
				}
			}
		}
		
		
		MethodNode itemStack_getDisplayName = getMethodNodeFromMapping(itemStack, "net/minecraft/item/ItemStack getDisplayName ()Ljava/lang/String;");
		if (itemStack_getDisplayName == null) return false;
		
		String getItemStackDisplayName_name = null;
		String getItemStackDisplayName_desc = "(L" + itemStack.name + ";)Ljava/lang/String;";
		
		// public String getItemStackDisplayName(ItemStack param0)
		for (AbstractInsnNode insn = itemStack_getDisplayName.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;
			if (mn.owner.equals(item.name) && mn.desc.equals(getItemStackDisplayName_desc)) {
				getItemStackDisplayName_name = mn.name;
				break;
			}
		}		
		if (getItemStackDisplayName_name == null) return false;
		
		MethodNode getItemStackDisplayName = getMethodNode(item, item.name + " " + getItemStackDisplayName_name + " " + getItemStackDisplayName_desc);
		if (getItemStackDisplayName == null) return false;
		
		addMethodMapping("net/minecraft/item/Item getItemStackDisplayName (Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",
				item.name + " " + getItemStackDisplayName.name + " " + getItemStackDisplayName.desc);
		
		
		
		//public String getUnlocalizedNameInefficiently(ItemStack param0)
		String getUnlocalizedNameInefficiently_name = null;		
		for (AbstractInsnNode insn = getItemStackDisplayName.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;
			if (mn.owner.equals(item.name) && mn.desc.equals(getItemStackDisplayName_desc)) {
				getUnlocalizedNameInefficiently_name = mn.name;
				break;
			}
		}	
		if (getUnlocalizedNameInefficiently_name == null) return false;
		
		addMethodMapping("net/minecraft/item/Item getUnlocalizedNameInefficiently (Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",
				item.name + " " + getUnlocalizedNameInefficiently_name + " " + getItemStackDisplayName_desc);
	
		
		
		// public String getUnlocalizedName(ItemStack param0)
		methods = getMatchingMethods(item, null, "(L" + itemStack.name + ";)Ljava/lang/String;");
		if (methods.size() == 3) {
			for (Iterator<MethodNode> iterator = methods.iterator(); iterator.hasNext();) {
				MethodNode mn = iterator.next();
				if (mn.name.equals(getItemStackDisplayName_name) || mn.name.equals(getUnlocalizedNameInefficiently_name)) {
					iterator.remove();					
				}
			}
			
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/item/Item getUnlocalizedName (Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",
						item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		
		// public String getUnlocalizedName()
		methods = getMatchingMethods(item, null, "()Ljava/lang/String;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/Item getUnlocalizedName ()Ljava/lang/String;",
					item.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		

		return true;
	}

	
	@Mapping(providesFields={
			"net/minecraft/item/ItemStack stackTagCompound Lnet/minecraft/nbt/NBTTagCompound;",
			"net/minecraft/item/ItemStack stackSize I",
			"net/minecraft/item/ItemStack itemDamage I"
			},
			providesMethods={
			"net/minecraft/item/ItemStack loadItemStackFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/ItemStack setTagCompound (Lnet/minecraft/nbt/NBTTagCompound;)V",
			"net/minecraft/item/ItemStack readFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)V",
			"net/minecraft/item/ItemStack isItemEnchanted ()Z",
			"net/minecraft/item/ItemStack hasTagCompound ()Z",
			"net/minecraft/item/ItemStack getItem ()Lnet/minecraft/item/Item;",
			"net/minecraft/item/ItemStack getTagCompound ()Lnet/minecraft/nbt/NBTTagCompound;",
			"net/minecraft/item/ItemStack getDisplayName ()Ljava/lang/String;",
			"net/minecraft/item/ItemStack getUnlocalizedName ()Ljava/lang/String;",
			"net/minecraft/item/ItemStack copy ()Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/ItemStack isItemEqual (Lnet/minecraft/item/ItemStack;)Z",
			"net/minecraft/item/ItemStack isItemStackEqual (Lnet/minecraft/item/ItemStack;)Z",
			"net/minecraft/item/ItemStack isDamageableItemEqual (Lnet/minecraft/item/ItemStack;)Z", // NEW
			"net/minecraft/item/ItemStack isItemStackDamageable ()Z",
			"net/minecraft/item/ItemStack splitStack (I)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/ItemStack setTagInfo (Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V"
			},
			depends={
			"net/minecraft/item/ItemStack",
			"net/minecraft/item/Item",
			"net/minecraft/nbt/NBTTagCompound",
			"net/minecraft/nbt/NBTBase"
			})
	public boolean processItemStackClass()
	{
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode tagCompound = getClassNodeFromMapping("net/minecraft/nbt/NBTTagCompound");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode nbtbase = getClassNodeFromMapping("net/minecraft/nbt/NBTBase");
		if (!MeddleUtil.notNull(itemStack, tagCompound, item, nbtbase)) return false;

		// public static ItemStack loadItemStackFromNBT(NBTTagCompound nbt)
		List<MethodNode> methods = getMatchingMethods(itemStack, null, "(L" + tagCompound.name + ";)L" + itemStack.name + ";");
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/item/ItemStack loadItemStackFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;",
				itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);


		String stackTagCompoundField = null;

		methods = getMatchingMethods(itemStack, null, "(L" + tagCompound.name + ";)V");
		if (methods.size() != 2) return false;
		for (MethodNode method :  methods)
		{
			// public void setTagCompound(NBTTagCompound nbt)
			AbstractInsnNode firstReal = getNextRealOpcode(method.instructions.getFirst());
			if (firstReal.getOpcode() == Opcodes.ALOAD) {
				AbstractInsnNode nextNode = firstReal.getNext();
				if (nextNode != null && nextNode.getOpcode() == Opcodes.ALOAD) {
					nextNode = nextNode.getNext();
					if (nextNode != null && nextNode.getOpcode() == Opcodes.PUTFIELD) {
						FieldInsnNode fn = (FieldInsnNode)nextNode;
						if (fn.owner.equals(itemStack.name) && fn.desc.equals("L" + tagCompound.name + ";"))
						{
							stackTagCompoundField = fn.name;
							addFieldMapping("net/minecraft/item/ItemStack stackTagCompound Lnet/minecraft/nbt/NBTTagCompound;",
									itemStack.name + " " + fn.name + " " + fn.desc);
							addMethodMapping("net/minecraft/item/ItemStack setTagCompound (Lnet/minecraft/nbt/NBTTagCompound;)V",
									itemStack.name + " " + method.name + " " + method.desc);
							continue;
						}
					}
				}
			}

			// public void readFromNBT(NBTTagCompound nbt)
			boolean isReadNBT = false;
			for (AbstractInsnNode insn = firstReal; insn != null; insn = insn.getNext()) {
				if (!isLdcWithString(insn, "id")) continue;
				isReadNBT = true;
				addMethodMapping("net/minecraft/item/ItemStack readFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)V",
						itemStack.name + " " + method.name + " " + method.desc);
				break;
			}
			
			if (isReadNBT) {
				boolean foundCount = false;
				boolean foundDamage = false;
				boolean foundstackSize = false;
				boolean founditemDamage = false;
				for (AbstractInsnNode insn = firstReal; insn != null; insn = insn.getNext()) {
					String s = getLdcString(insn);
					if (s != null) {
						foundCount = false; foundDamage = false;
						if (s.equals("Count")) foundCount = true;
						else if (s.equals("Damage")) foundDamage = true;
					}
					
					if (insn.getOpcode() != Opcodes.PUTFIELD) continue;
					FieldInsnNode fn = (FieldInsnNode)insn;
					if (!fn.owner.equals(itemStack.name)) continue;
					
					if (foundCount && !foundstackSize) {
						addFieldMapping("net/minecraft/item/ItemStack stackSize I", itemStack.name + " " + fn.name + " I");
						foundCount = false;
						foundstackSize = true;
					}
					else if (foundDamage) {
						addFieldMapping("net/minecraft/item/ItemStack itemDamage I", itemStack.name + " " + fn.name + " I");
						foundDamage = false;
						founditemDamage = true;
					}
					
					if (foundstackSize && founditemDamage) break;
				}
			}
		}

		if (stackTagCompoundField == null) return false;



		// Find isItemEnchanted()Z and hasTagCompound()Z
		methods = getMatchingMethods(itemStack, null, "()Z");
		for (Iterator<MethodNode> iterator = methods.iterator(); iterator.hasNext();)
		{
			MethodNode method = iterator.next();
			if (!matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.IFNULL)) {
				iterator.remove();
				continue;
			}

			boolean correctField = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.GETFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.owner.equals(itemStack.name) && fn.name.equals(stackTagCompoundField))
					correctField = true;
				break;
			}
			if (!correctField) { iterator.remove(); continue; }
		}

		if (methods.size() == 2) 
		{
			for (MethodNode method : methods) {
				boolean isEnchant = false;
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (isLdcWithString(insn, "ench")) { isEnchant = true; break; }
				}
				
				// public boolean isItemEnchanted()
				if (isEnchant) {
					addMethodMapping("net/minecraft/item/ItemStack isItemEnchanted ()Z", itemStack.name + " " + method.name + " ()Z");
				}
				// public boolean hasTagCompound()
				else {
					addMethodMapping("net/minecraft/item/ItemStack hasTagCompound ()Z", itemStack.name + " " + method.name + " ()Z");
				}
			}
		}


		// public Item getItem()
		methods = getMatchingMethods(itemStack, null, "()L" + item.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack getItem ()Lnet/minecraft/item/Item;",
					itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}


		// public NBTTagCompound getTagCompound()
		methods = getMatchingMethods(itemStack, null, "()L" + tagCompound.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack getTagCompound ()Lnet/minecraft/nbt/NBTTagCompound;",
					itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}



		methods = getMatchingMethods(itemStack, null, "()Ljava/lang/String;");
		if (methods.size() == 3) {
			for (MethodNode method : methods) {
				if (method.name.equals("toString")) continue;
	
				boolean isDisplayName = false;
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (isLdcWithString(insn, "display")) { isDisplayName = true; break; }
				}
	
				// public String getDisplayName()
				if (isDisplayName) {
					addMethodMapping("net/minecraft/item/ItemStack getDisplayName ()Ljava/lang/String;",
							itemStack.name + " " + method.name + " " + method.desc);
				}
				// public String getUnlocalizedName()
				else {
					addMethodMapping("net/minecraft/item/ItemStack getUnlocalizedName ()Ljava/lang/String;",
							itemStack.name + " " + method.name + " " + method.desc);
				}
			}
		}
		
		
		// public ItemStack copy()
		methods = getMatchingMethods(itemStack,  null,  "()L" + itemStack.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack copy ()Lnet/minecraft/item/ItemStack;",
					itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
				

		// public boolean isItemEqual(ItemStack other)
		methods = getMatchingMethods(itemStack, null, "(L" + itemStack.name + ";)Z");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_PUBLIC);		
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack isItemEqual (Lnet/minecraft/item/ItemStack;)Z",
					itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		// 15w36d+
		else if (methods.size() == 2) {
			int count = 0;
			MethodNode isItemEqualMethod = null;
			MethodNode invokerMethod = null;
			List<MethodInsnNode> mnodes = new ArrayList<>(); 
			
			for (MethodNode method : methods) {
				boolean invokes = false;
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) { 
						invokes = true;
						mnodes.add((MethodInsnNode)insn);
					}
				}
				if (!invokes) { count++; isItemEqualMethod = method; }
				else invokerMethod = method;
			}
			
			if (count == 1 && isItemEqualMethod != null) {
				addMethodMapping("net/minecraft/item/ItemStack isItemEqual (Lnet/minecraft/item/ItemStack;)Z",
						itemStack.name + " " + isItemEqualMethod.name + " " + isItemEqualMethod.desc);
			}
			if (count == 1 && invokerMethod != null) {
				addMethodMapping("net/minecraft/item/ItemStack isDamageableItemEqual (Lnet/minecraft/item/ItemStack;)Z",
						itemStack.name + " " + invokerMethod.name + " " + invokerMethod.desc);
				
				MethodInsnNode isItemStackDamageable = null;
				count = 0;
				for (MethodInsnNode mn : mnodes) {
					if (mn.owner.equals(itemStack.name) && mn.desc.equals("()Z")) {
						count++;
						isItemStackDamageable = mn;
					}
				}
				if (count == 1 && isItemStackDamageable != null) {
					addMethodMapping("net/minecraft/item/ItemStack isItemStackDamageable ()Z",
							itemStack.name + " " + isItemStackDamageable.name + " " + isItemStackDamageable.desc);
				}
			}
		}
		
		
		
		// private boolean isItemStackEqual(ItemStack other)
		methods = getMatchingMethods(itemStack, null, "(L" + itemStack.name + ";)Z");
		methods = removeMethodsWithoutFlags(methods, Opcodes.ACC_PRIVATE);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack isItemStackEqual (Lnet/minecraft/item/ItemStack;)Z",
					itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public ItemStack splitStack(int amount)
		methods = getMatchingMethods(itemStack, null, "(I)L" + itemStack.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack splitStack (I)Lnet/minecraft/item/ItemStack;",
					itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void setTagInfo(String key, NBTBase value)
		methods = getMatchingMethods(itemStack, null, "(Ljava/lang/String;L" + nbtbase.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack setTagInfo (Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V",
					itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}

	
	
	@Mapping(providesMethods={
			"net/minecraft/item/ItemStack getItemDamage ()I",
			"net/minecraft/item/ItemStack setItemDamage (I)V",
			"net/minecraft/item/ItemStack damageItem (ILnet/minecraft/entity/EntityLivingBase;)V",
			"net/minecraft/item/ItemStack getMaxDamage ()I"
			},
			providesFields={
			"net/minecraft/item/ItemStack item Lnet/minecraft/item/Item;"	
			},
			dependsMethods={
			"net/minecraft/item/ItemStack getMetadata ()I",
			"net/minecraft/item/Item getMaxDamage ()I"
			},
			dependsFields="net/minecraft/item/ItemStack itemDamage I",
			depends={
			"net/minecraft/item/ItemStack",
			"net/minecraft/entity/EntityLivingBase",
			"net/minecraft/item/Item"
			})	
	public boolean processItemStackClass2()
	{
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		if (!MeddleUtil.notNull(itemStack, entityLivingBase, item)) return false;
		
		FieldNode itemDamage = getFieldNodeFromMapping(itemStack, "net/minecraft/item/ItemStack itemDamage I");
		MethodNode getMetadata = getMethodNodeFromMapping(itemStack, "net/minecraft/item/ItemStack getMetadata ()I");
		if (itemDamage == null || getMetadata == null) return false;
		
		
		// public int getItemDamage()
		List<MethodNode> methods = getMatchingMethods(itemStack, null, "()I");
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			MethodNode method = it.next();
			if (!matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.IRETURN) || method.name.equals(getMetadata.name)) { 
				it.remove(); 
				continue; 
			}			
		}
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack getItemDamage ()I", itemStack.name + " " + methods.get(0).name + " ()I");
		}
		
		
		// public void setItemDamage(int meta)
		methods = getMatchingMethods(itemStack, null, "(I)V");
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			MethodNode method = it.next();
			List<FieldInsnNode> nodes = getAllInsnNodesOfType(method.instructions.getFirst(), FieldInsnNode.class);
			
			boolean usesItemDamage = false;
			for (FieldInsnNode fn : nodes) {
				if (fn.owner.equals(itemStack.name) && fn.name.equals(itemDamage.name)) usesItemDamage = true;
			}
			
			if (!usesItemDamage) it.remove();			
		}
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack setItemDamage (I)V", itemStack.name + " " + methods.get(0).name + " (I)V");
		}
		
		
		// public void damageItem(int amount, EntityLivingBase entityIn)
		methods = getMatchingMethods(itemStack, null, "(IL" + entityLivingBase.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/ItemStack damageItem (ILnet/minecraft/entity/EntityLivingBase;)V", 
					itemStack.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		MethodNode getMaxDamage = getMethodNodeFromMapping(item, "net/minecraft/item/Item getMaxDamage ()I");
		if (getMaxDamage != null) {
			methods = getMatchingMethods(itemStack, null, "()I");
			for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
				MethodNode method = it.next();
				List<MethodInsnNode> nodes = getAllInsnNodesOfType(method, MethodInsnNode.class);
				
				boolean hasIt = false;
				for (MethodInsnNode mn : nodes) {
					if (mn.owner.equals(item.name) && mn.name.equals(getMaxDamage.name) && mn.desc.equals("()I")) hasIt = true;
				}
				if (!hasIt) it.remove();
			}
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/item/ItemStack getMaxDamage ()I", itemStack.name + " " + methods.get(0).name + " ()I");
			}
		}
		
		
		List<FieldNode> fields = getMatchingFields(itemStack, null, "L" + item.name + ";");
		if (fields.size() == 1) {			
			addFieldMapping("net/minecraft/item/ItemStack item Lnet/minecraft/item/Item;", 
					itemStack.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		return true;		
	}
	
	
	

	@Mapping(providesFields={
			"net/minecraft/util/ItemUseResult SUCCESS Lnet/minecraft/util/ItemUseResult;",
			"net/minecraft/util/ItemUseResult PASS Lnet/minecraft/util/ItemUseResult;",
			"net/minecraft/util/ItemUseResult FAIL Lnet/minecraft/util/ItemUseResult;"
			},
			depends={
			"net/minecraft/util/ItemUseResult"
			})
	public boolean processItemUseResultClass()
	{
		ClassNode itemUseResult = getClassNodeFromMapping("net/minecraft/util/ItemUseResult");
		if (itemUseResult == null) return false;
		
		List<FieldNode> fields = getMatchingFields(itemUseResult, null, "L" + itemUseResult.name + ";");
		if (fields.size() == 3) {			
			addFieldMapping("net/minecraft/util/ItemUseResult SUCCESS Lnet/minecraft/util/ItemUseResult;",
					itemUseResult.name + " " + fields.get(0).name + " " + fields.get(0).desc);
			addFieldMapping("net/minecraft/util/ItemUseResult PASS Lnet/minecraft/util/ItemUseResult;",
					itemUseResult.name + " " + fields.get(1).name + " " + fields.get(1).desc);
			addFieldMapping("net/minecraft/util/ItemUseResult FAIL Lnet/minecraft/util/ItemUseResult;",
					itemUseResult.name + " " + fields.get(2).name + " " + fields.get(2).desc);			
		}
		
		
		return true;
	}


	@Mapping(provides={
			"net/minecraft/item/crafting/CraftingManager",
			"net/minecraft/entity/passive/EntityAnimal",
			"net/minecraft/item/EnumDyeColor"
			},
			depends={
			"net/minecraft/entity/passive/EntitySheep"
			})
	public boolean getCraftingManagerClass() 
	{
		ClassNode entitySheep = getClassNodeFromMapping("net/minecraft/entity/passive/EntitySheep");
		if (entitySheep == null) return false;
		
		List<MethodNode> methods = new ArrayList<MethodNode>();
		
		// Looking for: private EnumDyeColor func_175511_a(EntityAnimal, EntityAnimal)
		for (MethodNode method : entitySheep.methods) {
			if (!checkMethodParameters(method, Type.OBJECT, Type.OBJECT)) continue;
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			String className = args[0].getClassName();
			if (className.equals(args[1].getClassName()) && className.equals(entitySheep.superName)) methods.add(method);
		}
		
		MethodNode func_175511_a = null;
		if (methods.size() == 1) func_175511_a = methods.get(0);
		else return false;
		
		if (searchConstantPoolForStrings(entitySheep.superName, "InLove"))
			addClassMapping("net/minecraft/entity/passive/EntityAnimal", entitySheep.superName);
		
		String enumDyeColor_name = Type.getMethodType(func_175511_a.desc).getReturnType().getClassName();
		if (searchConstantPoolForStrings(enumDyeColor_name, "white", "lime"))
			addClassMapping("net/minecraft/item/EnumDyeColor", enumDyeColor_name);
		
		
		for (AbstractInsnNode insn = func_175511_a.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.INVOKESTATIC) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;			
			
			if (mn.desc.equals("()L" + mn.owner + ";")) {
				if (searchConstantPoolForStrings(mn.owner, "###", "AAA")) {
					addClassMapping("net/minecraft/item/crafting/CraftingManager", mn.owner);
					return true;
				}
			}			
		}		
		
		return false;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/item/crafting/ShapedRecipes",
			"net/minecraft/item/crafting/IRecipe",
			"net/minecraft/item/crafting/ShapelessRecipes"
			},
			providesMethods={
			"net/minecraft/item/crafting/CraftingManager addRecipe (Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)Lnet/minecraft/item/crafting/ShapedRecipes;",
			"net/minecraft/item/crafting/CraftingManager addShapelessRecipe (Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
			"net/minecraft/item/crafting/CraftingManager getInstance ()Lnet/minecraft/item/crafting/CraftingManager;",
			"net/minecraft/item/crafting/CraftingManager getRecipeList ()Ljava/util/List;",
			"net/minecraft/item/crafting/CraftingManager addRecipe (Lnet/minecraft/item/crafting/IRecipe;)V"
			},
			depends={
			"net/minecraft/item/crafting/CraftingManager",
			"net/minecraft/item/ItemStack"
			})
	public boolean processCraftingManagerClass() 
	{
		ClassNode craftingManager = getClassNodeFromMapping("net/minecraft/item/crafting/CraftingManager");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		if (craftingManager == null || itemStack == null) return false;
		
		List<MethodNode> methods = new ArrayList<MethodNode>();
		
		// public ShapedRecipes addRecipe(ItemStack param0, Object... param1)
		for (MethodNode method : craftingManager.methods) {
			if (method.desc.startsWith("(L" + itemStack.name + ";[Ljava/lang/Object;)L")) 
				methods.add(method);
		}		
		if (methods.size() != 1) return false;
		
		String shapedRecipes_className = Type.getMethodType(methods.get(0).desc).getReturnType().getClassName();
		addClassMapping("net/minecraft/item/crafting/ShapedRecipes", shapedRecipes_className);
		
		addMethodMapping("net/minecraft/item/crafting/CraftingManager addRecipe (Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)Lnet/minecraft/item/crafting/ShapedRecipes;",
				craftingManager.name + " " + methods.get(0).name + " " + methods.get(0).desc);	
		
		
		// public static CraftingManager getInstance()
		methods = getMatchingMethods(craftingManager, null, "()L" + craftingManager.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/crafting/CraftingManager getInstance ()Lnet/minecraft/item/crafting/CraftingManager;",
					craftingManager.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public List getRecipeList()
		methods = getMatchingMethods(craftingManager, null, "()Ljava/util/List;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/crafting/CraftingManager getRecipeList ()Ljava/util/List;",
					craftingManager.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void addRecipe(IRecipe recipe)
		// net/minecraft/item/crafting/IRecipe
		String iRecipe_name = null;
		methods.clear();
		for (MethodNode method : craftingManager.methods) {
			if (!method.desc.startsWith("(L") || !method.desc.endsWith(";)V")) continue;
			Type[] args = Type.getMethodType(method.desc).getArgumentTypes();
			if (args.length != 1) continue;
			methods.add(method);
		}
		if (methods.size() == 1) {
			MethodNode method = methods.get(0);
			iRecipe_name = Type.getMethodType(method.desc).getArgumentTypes()[0].getClassName();
			ClassNode iRecipe = getClassNode(iRecipe_name);
			if ((iRecipe.access & Opcodes.ACC_INTERFACE) != 0) { // TODO: Better detection couldn't hurt
				addClassMapping("net/minecraft/item/crafting/IRecipe", iRecipe_name);
				
				addMethodMapping("net/minecraft/item/crafting/CraftingManager addRecipe (Lnet/minecraft/item/crafting/IRecipe;)V",
						craftingManager.name + " " + method.name + " " + method.desc);
			}
			else iRecipe_name = null;
		}
		
		
		// public void addShapelessRecipe(ItemStack param0, Object... param1)
		// net/minecraft/item/crafting/ShapelessRecipes
		methods = getMatchingMethods(craftingManager, null, "(L" + itemStack.name + ";[Ljava/lang/Object;)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/crafting/CraftingManager addShapelessRecipe (Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
					craftingManager.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			if (iRecipe_name != null) {
				List<TypeInsnNode> nodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), TypeInsnNode.class);
				for (Iterator<TypeInsnNode> it = nodes.iterator(); it.hasNext();) {
					TypeInsnNode tn = it.next();
					if (tn.getOpcode() != Opcodes.NEW || tn.desc.startsWith("java/") || tn.desc.equals(itemStack.name)) {
						it.remove(); 
						continue; 
					}
					ClassNode cn = getClassNode(tn.desc);
					if (!classHasInterfaces(cn, iRecipe_name)) { it.remove(); continue; }					
				}				
				if (nodes.size() == 1) {
					addClassMapping("net/minecraft/item/crafting/ShapelessRecipes", nodes.get(0).desc);
				}				
			}
		}
		
		
		return true;
	}
	
	
	@Mapping(providesFields={
			"net/minecraft/item/crafting/ShapedRecipes recipeWidth I",
			"net/minecraft/item/crafting/ShapedRecipes recipeHeight I",
			"net/minecraft/item/crafting/ShapedRecipes recipeItems [Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/crafting/ShapedRecipes recipeOutput Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/crafting/ShapedRecipes copyIngredientNBT Z"
			},
			depends={
			"net/minecraft/item/crafting/ShapedRecipes",
			"net/minecraft/item/ItemStack"
			})
	public boolean processShapedRecipesClass()
	{
		ClassNode shapedRecipes = getClassNodeFromMapping("net/minecraft/item/crafting/ShapedRecipes");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		if (!MeddleUtil.notNull(shapedRecipes, itemStack)) return false;
		
		List<FieldNode> fields = getMatchingFields(shapedRecipes, null, "I");
		if (fields.size() == 2) {
			addFieldMapping("net/minecraft/item/crafting/ShapedRecipes recipeWidth I", shapedRecipes.name + " " + fields.get(0).name + " I");
			addFieldMapping("net/minecraft/item/crafting/ShapedRecipes recipeHeight I", shapedRecipes.name + " " + fields.get(1).name + " I");
		}
		
		
		fields = getMatchingFields(shapedRecipes, null, "[L" + itemStack.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/item/crafting/ShapedRecipes recipeItems [Lnet/minecraft/item/ItemStack;", 
					shapedRecipes.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		fields = getMatchingFields(shapedRecipes, null, "L" + itemStack.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/item/crafting/ShapedRecipes recipeOutput Lnet/minecraft/item/ItemStack;", 
					shapedRecipes.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		fields = getMatchingFields(shapedRecipes, null, "Z");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/item/crafting/ShapedRecipes copyIngredientNBT Z", 
					shapedRecipes.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		
		return true;
	}
	
	@Mapping(providesFields={
			"net/minecraft/item/crafting/ShapelessRecipes recipeOutput Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/crafting/ShapelessRecipes recipeItems Ljava/util/List;"
			},
			depends={
			"net/minecraft/item/crafting/ShapelessRecipes",
			"net/minecraft/item/ItemStack"
			})
	public boolean processShapelessRecipesClass()
	{
		ClassNode shapelessRecipes = getClassNodeFromMapping("net/minecraft/item/crafting/ShapelessRecipes");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		if (!MeddleUtil.notNull(shapelessRecipes, itemStack)) return false;
		
		// private final ItemStack recipeOutput;
		List<FieldNode> fields = getMatchingFields(shapelessRecipes, null, "L" + itemStack.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/item/crafting/ShapelessRecipes recipeOutput Lnet/minecraft/item/ItemStack;",
					shapelessRecipes.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		// private final List recipeItems;
		fields = getMatchingFields(shapelessRecipes, null, "Ljava/util/List;");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/item/crafting/ShapelessRecipes recipeItems Ljava/util/List;",
					shapelessRecipes.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		
		return true;
	}
	
	

	@Mapping(provides={
			"net/minecraft/inventory/InventoryCrafting"			
			},
			providesMethods={
			"net/minecraft/item/crafting/IRecipe getRecipeSize ()I",
			"net/minecraft/item/crafting/IRecipe getRecipeOutput ()Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/crafting/IRecipe matches (Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Z",
			"net/minecraft/item/crafting/IRecipe getCraftingResult (Lnet/minecraft/inventory/InventoryCrafting;)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/item/crafting/IRecipe getRemainingItems (Lnet/minecraft/inventory/InventoryCrafting;)[Lnet/minecraft/item/ItemStack;"
			},
			depends={
			"net/minecraft/item/crafting/IRecipe",
			"net/minecraft/item/ItemStack",
			"net/minecraft/world/World"
			})
	public boolean processIRecipeClass()
	{
		ClassNode iRecipe = getClassNodeFromMapping("net/minecraft/item/crafting/IRecipe");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		if (!MeddleUtil.notNull(iRecipe, itemStack, world)) return false;

	    // int getRecipeSize();
		List<MethodNode> methods = getMatchingMethods(iRecipe, null, "()I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/crafting/IRecipe getRecipeSize ()I", 
					iRecipe.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// ItemStack getRecipeOutput();
		methods = getMatchingMethods(iRecipe, null, "()L" + itemStack.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/crafting/IRecipe getRecipeOutput ()Lnet/minecraft/item/ItemStack;", 
					iRecipe.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// boolean matches(InventoryCrafting inv, World worldIn);
		// net/minecraft/inventory/InventoryCrafting
		methods.clear();
		for (MethodNode method : iRecipe.methods) {
			if (method.desc.startsWith("(L") && method.desc.endsWith(";L" + world.name + ";)Z") 
					&& Type.getMethodType(method.desc).getArgumentTypes().length == 2) methods.add(method);
		}
		String inventoryCrafting_name = null;
		if (methods.size() == 1) {
			MethodNode method = methods.get(0);
			Type t = Type.getMethodType(method.desc);
			inventoryCrafting_name = t.getArgumentTypes()[0].getClassName();
			if (searchConstantPoolForStrings(inventoryCrafting_name, "container.crafting")) {
				addClassMapping("net/minecraft/inventory/InventoryCrafting", inventoryCrafting_name);
				addMethodMapping("net/minecraft/item/crafting/IRecipe matches (Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Z",
						iRecipe.name + " " + method.name + " " + method.desc);
			}
			else inventoryCrafting_name = null;
		}
		
		if (inventoryCrafting_name == null) return false;
		
		
		// ItemStack getCraftingResult(InventoryCrafting inv);
		methods = getMatchingMethods(iRecipe, null, "(L" + inventoryCrafting_name + ";)L" + itemStack.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/crafting/IRecipe getCraftingResult (Lnet/minecraft/inventory/InventoryCrafting;)Lnet/minecraft/item/ItemStack;", 
					iRecipe.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
	    // ItemStack[] getRemainingItems(InventoryCrafting inv);
		methods = getMatchingMethods(iRecipe, null, "(L" + inventoryCrafting_name + ";)[L" + itemStack.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/crafting/IRecipe getRemainingItems (Lnet/minecraft/inventory/InventoryCrafting;)[Lnet/minecraft/item/ItemStack;", 
					iRecipe.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	
	
	
	
	@Mapping(provides={
			"net/minecraft/init/Items"
			},
			providesMethods={
			},
			depends={
			"net/minecraft/entity/passive/EntityVillager",
			"net/minecraft/item/Item"
			})
	public boolean getInitItemsClass() 
	{
		ClassNode entityVillager = getClassNodeFromMapping("net/minecraft/entity/passive/EntityVillager");		
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		if (entityVillager == null || item == null) return false;
		
		List<MethodNode> methods = getMatchingMethods(entityVillager, null, "(L" + item.name + ";)Z");
		if (methods.size() != 1) return false;
		
		for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.GETSTATIC) continue;
			FieldInsnNode fn = (FieldInsnNode)insn;
			if (!fn.desc.equals("L" + item.name + ";")) continue;
			
			if (searchConstantPoolForStrings(fn.owner, "Accessed Items before Bootstrap!", "coal")) {
				addClassMapping("net/minecraft/init/Items", fn.owner);
				return true;
			}
		}			
		
		return false;
	}
	
	
	@Mapping(providesFields={
			"net/minecraft/init/Items leather Lnet/minecraft/item/Item;",
			"net/minecraft/init/Items redstone Lnet/minecraft/item/Item;",
			"net/minecraft/init/Items ender_pearl Lnet/minecraft/item/Item;",
			"net/minecraft/init/Items iron_ingot Lnet/minecraft/item/Item;",
			"net/minecraft/init/Items stick Lnet/minecraft/item/Item;",
			"net/minecraft/init/Items iron_axe Lnet/minecraft/item/Item;",
			"net/minecraft/init/Items iron_pickaxe Lnet/minecraft/item/Item;"
			},
			depends={
			"net/minecraft/init/Items",
			"net/minecraft/item/Item"
			})
	public boolean discoverItemsFields()
	{
		Map<String, String> itemsClassFields = new HashMap<String, String>();
		Map<String, String> itemsFields = new HashMap<String, String>();

		ClassNode itemsClass = getClassNode(getClassMapping("net/minecraft/init/Items"));
		ClassNode itemClass = getClassNode(getClassMapping("net/minecraft/item/Item"));
		if (itemsClass == null || itemClass == null) return false;

		// Generate list
		for (MethodNode method : itemsClass.methods)
		{
			if (!method.name.equals("<clinit>")) continue;

			String lastString = null;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				String s = getLdcString(insn);
				if (s != null) lastString = s;
				// Avoid any strings that definitely aren't block names
				if (lastString == null || lastString.contains(" ")) continue;

				if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
				FieldInsnNode fieldNode = (FieldInsnNode)insn;
				// Filter out non-objects and packaged classes, just in case
				if (!fieldNode.desc.startsWith("L") || fieldNode.desc.contains("/")) continue;
				
				itemsFields.put(lastString, fieldNode.name);
				
				// Filter out generic ones extending just the block class
				if (fieldNode.desc.equals("L" + itemClass.name + ";")) continue;
				itemsClassFields.put(lastString, fieldNode.desc.substring(1, fieldNode.desc.length() - 1));
			}
		}

		String fieldName;
		String className;

		fieldName = itemsFields.get("leather");
		if (fieldName != null) addFieldMapping("net/minecraft/init/Items leather Lnet/minecraft/item/Item;", 
				itemsClass.name + " " + fieldName + " L" + itemClass.name + ";");
		
		fieldName = itemsFields.get("redstone");
		if (fieldName != null) addFieldMapping("net/minecraft/init/Items redstone Lnet/minecraft/item/Item;", 
				itemsClass.name + " " + fieldName + " L" + itemClass.name + ";");
		
		fieldName = itemsFields.get("ender_pearl");
		if (fieldName != null) addFieldMapping("net/minecraft/init/Items ender_pearl Lnet/minecraft/item/Item;", 
				itemsClass.name + " " + fieldName + " L" + itemClass.name + ";");
		
		fieldName = itemsFields.get("iron_ingot");
		if (fieldName != null) addFieldMapping("net/minecraft/init/Items iron_ingot Lnet/minecraft/item/Item;", 
				itemsClass.name + " " + fieldName + " L" + itemClass.name + ";");
		
		fieldName = itemsFields.get("stick");
		if (fieldName != null) addFieldMapping("net/minecraft/init/Items stick Lnet/minecraft/item/Item;", 
				itemsClass.name + " " + fieldName + " L" + itemClass.name + ";");
		
		fieldName = itemsFields.get("iron_axe");
		if (fieldName != null) addFieldMapping("net/minecraft/init/Items iron_axe Lnet/minecraft/item/Item;", 
				itemsClass.name + " " + fieldName + " L" + itemClass.name + ";");

		fieldName = itemsFields.get("iron_pickaxe");
		if (fieldName != null) addFieldMapping("net/minecraft/init/Items iron_pickaxe Lnet/minecraft/item/Item;", 
				itemsClass.name + " " + fieldName + " L" + itemClass.name + ";");
		
		
		// TODO - Other items

		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/command/ServerCommandManager",
			"net/minecraft/util/DataVersionManager",
			"net/minecraft/network/NetworkSystem",
			"net/minecraft/server/management/PlayerProfileCache"
			},
			providesFields={
			// No longer exists in 15w36a, uses the commandManager instead
			//"net/minecraft/server/MinecraftServer mcServer Lnet/minecraft/server/MinecraftServer;"
			},
			providesMethods={
			"net/minecraft/server/MinecraftServer createNewCommandManager ()Lnet/minecraft/command/ServerCommandManager;"
			},
			depends={
			"net/minecraft/server/MinecraftServer"
			})
	public boolean findServerCommandManagerClass()
	{
		ClassNode minecraftServer = getClassNode("net/minecraft/server/MinecraftServer");
		if (minecraftServer == null) return false;
		
		String serverCommandManager_name = null;
		
		// public MinecraftServer(File, Proxy, File, DataVersionManager)		
		for (MethodNode method : minecraftServer.methods) {
			
			if (serverCommandManager_name != null && method.desc.equals("()L" + serverCommandManager_name + ";")) {
				// TODO 
				continue;
			}
			
			if (!method.name.equals("<init>")) continue;
			//if (!method.desc.startsWith("(Ljava/io/File;Ljava/net/Proxy;Ljava/io/File;")) continue;			
			
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			
			// was 4 pre-15w36a
			if (args.length != 7) continue;
			
			// was 3 pre-15w36a
			String className = args[2].getClassName();
			if (searchConstantPoolForStrings(className, "DataVersion")) {
				addClassMapping("net/minecraft/util/DataVersionManager", className);
			}
			
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				
				if (insn instanceof MethodInsnNode) {
					MethodInsnNode mn = (MethodInsnNode)insn;
					if (mn.name.equals("<init>")) continue;
					Type mt = Type.getMethodType(mn.desc);
					Type returnType = mt.getReturnType();
					if (returnType.getSort() != Type.OBJECT || returnType.getClassName().contains(".")) continue;
					
					if (searchConstantPoolForStrings(returnType.getClassName(), "chat.type.admin", "logAdminCommands")) {
						serverCommandManager_name = returnType.getClassName();
						addClassMapping("net/minecraft/command/ServerCommandManager",  returnType.getClassName());
						addMethodMapping("net/minecraft/server/MinecraftServer createNewCommandManager ()Lnet/minecraft/command/ServerCommandManager;",
								minecraftServer.name + " " + mn.name + " " + mn.desc);
						continue;
					}					
				}
				
				if (!(insn instanceof FieldInsnNode)) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				
				// No longer exists in 15w36a, uses the commandManager var instead
				/*if (fn.getOpcode() == Opcodes.PUTSTATIC && fn.desc.equals("Lnet/minecraft/server/MinecraftServer;")) {
					addFieldMapping("net/minecraft/server/MinecraftServer mcServer Lnet/minecraft/server/MinecraftServer;",
							"net/minecraft/server/MinecraftServer " + fn.name + " " + fn.desc);
					continue;
				}*/
				
				Type ft = Type.getType(fn.desc);
				if (ft.getSort() != Type.OBJECT) continue;
				
				className = ft.getClassName();
				if (className.contains(".")) continue;
				
				if (searchConstantPoolForStrings(className, "Ticking memory connection", "Failed to handle packet for")) {
					addClassMapping("net/minecraft/network/NetworkSystem", className);
					continue;
				}
				
				if (searchConstantPoolForStrings(className, "yyyy-MM-dd HH:mm:ss Z")) {
					addClassMapping("net/minecraft/server/management/PlayerProfileCache", className);
					continue;
				}
				
				// TODO - Do other fields from constructor			
							
				//System.out.println(ft.getClassName());
			}
			
		}
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/command/CommandHandler",
			"net/minecraft/command/ICommandManager"
			},
			providesMethods={
			"net/minecraft/server/MinecraftServer getCommandManager ()Lnet/minecraft/command/ICommandManager;"
			},
			depends={
			"net/minecraft/command/ServerCommandManager",
			"net/minecraft/server/MinecraftServer"
			})
	public boolean processServerCommandManagerClass()
	{
		ClassNode serverCommandManager = getClassNodeFromMapping("net/minecraft/command/ServerCommandManager");
		ClassNode minecraftServer = getClassNodeFromMapping("net/minecraft/server/MinecraftServer");
		if (serverCommandManager == null || minecraftServer == null) return false;
		if (serverCommandManager.superName == null) return false;
		
		if (searchConstantPoolForStrings(serverCommandManager.superName, "commands.generic.notFound", "commands.generic.usage")) {
			addClassMapping("net/minecraft/command/CommandHandler", serverCommandManager.superName);
		}
		else return false;
		
		ClassNode commandHandler = getClassNode(serverCommandManager.superName);
		if (commandHandler.interfaces.size() != 1) return false;
		String iCommandManager_name = commandHandler.interfaces.get(0);
		addClassMapping("net/minecraft/command/ICommandManager", iCommandManager_name);		
		
		
		List<MethodNode> methods = getMatchingMethods(minecraftServer,  null, "()L" + iCommandManager_name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/server/MinecraftServer getCommandManager ()Lnet/minecraft/command/ICommandManager;",
					minecraftServer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/command/ICommand"
			},
			providesMethods={
			"net/minecraft/command/CommandHandler registerCommand (Lnet/minecraft/command/ICommand;)Lnet/minecraft/command/ICommand;"
			},
			depends={
			"net/minecraft/command/CommandHandler"			
			})
	public boolean processCommandHandlerClass()
	{
		ClassNode commandHandler = getClassNodeFromMapping("net/minecraft/command/CommandHandler");
		if (commandHandler == null) return false;
		
		List<MethodNode> methods = new ArrayList<MethodNode>();
		String className = null;
		
		// Find: public ICommand registerCommand(ICommand)
		for (MethodNode method : commandHandler.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			Type returnType = t.getReturnType();
			if (args.length != 1) continue;
			if (args[0].getSort() != Type.OBJECT || returnType.getSort() != Type.OBJECT) continue;
			if (!args[0].getClassName().equals(returnType.getClassName())) continue;			
			methods.add(method);
			className = returnType.getClassName();
		}
		if (methods.size() != 1 || className == null) return false;
		
		ClassNode iCommand = getClassNode(className);
		if (iCommand == null) return false;		
		if ((iCommand.access & Opcodes.ACC_INTERFACE) == 0) return false;
		
		addMethodMapping("net/minecraft/command/CommandHandler registerCommand (Lnet/minecraft/command/ICommand;)Lnet/minecraft/command/ICommand;",
				commandHandler.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		addClassMapping("net/minecraft/command/ICommand", iCommand.name);
		
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/command/ICommandSender",
			"net/minecraft/command/CommandException"
			},
			providesMethods={
			"net/minecraft/command/ICommand getCommandName ()Ljava/lang/String;",
			"net/minecraft/command/ICommand getCommandUsage (Lnet/minecraft/command/ICommandSender;)Ljava/lang/String;",
			"net/minecraft/command/ICommand getCommandAliases ()Ljava/util/List;",
			"net/minecraft/command/ICommand processCommand (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V",
			"net/minecraft/command/ICommand canCommandSenderUseCommand (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;)Z",
			"net/minecraft/command/ICommand addTabCompletionOptions (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;Lnet/minecraft/util/BlockPos;)Ljava/util/List;",
			"net/minecraft/command/ICommand isUsernameIndex ([Ljava/lang/String;I)Z"
			},
			depends={
			"net/minecraft/command/ICommand",
			"net/minecraft/util/BlockPos",
			"net/minecraft/server/MinecraftServer"
			})
	public boolean processICommandClass()
	{
		ClassNode iCommand = getClassNodeFromMapping("net/minecraft/command/ICommand");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode server = getClassNodeFromMapping("net/minecraft/server/MinecraftServer");
		if (iCommand == null || blockPos == null || server == null) return false;
	
		// String getCommandName();
		List<MethodNode> methods = getMatchingMethods(iCommand, null, "()Ljava/lang/String;");	
		if (methods.size() != 1) return false;
		addMethodMapping("net/minecraft/command/ICommand getCommandName ()Ljava/lang/String;",
				iCommand.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		
		// String getCommandUsage(ICommandSender sender);
		methods.clear();
		String iCommandSender_name = null;
		for (MethodNode method : iCommand.methods) {
			if (!method.desc.endsWith(";)Ljava/lang/String;")) continue;			
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length != 1) continue;
			if (args[0].getSort() != Type.OBJECT) continue;
			methods.add(method);
			iCommandSender_name = args[0].getClassName();
		}
		if (methods.size() != 1 || iCommandSender_name == null) return false;
		
		ClassNode iCommandSender = getClassNode(iCommandSender_name);
		if (iCommandSender == null) return false;
		if ((iCommandSender.access & Opcodes.ACC_INTERFACE) == 0) return false;
		addClassMapping("net/minecraft/command/ICommandSender", iCommandSender_name);
		addMethodMapping("net/minecraft/command/ICommand getCommandUsage (Lnet/minecraft/command/ICommandSender;)Ljava/lang/String;",
				iCommand.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		
		
		// List getCommandAliases();
		methods = getMatchingMethods(iCommand,  null,  "()Ljava/util/List;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommand getCommandAliases ()Ljava/util/List;",
					iCommand.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
	
		
		// POST-15w35c, added MinecraftServer
		// void processCommand(MinecraftServer, ICommandSender sender, String[] args) throws CommandException;
		methods = getMatchingMethods(iCommand, null, "(L" + server.name + ";L" + iCommandSender.name + ";[Ljava/lang/String;)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommand processCommand (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V",
					iCommand.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		
			if (methods.get(0).exceptions.size() == 1) {
				addClassMapping("net/minecraft/command/CommandException", methods.get(0).exceptions.get(0));
			}
		}
		
		
		// POST-15w35c, added MinecraftServer
		// boolean canCommandSenderUseCommand(MinecraftServer, ICommandSender sender);
		methods = getMatchingMethods(iCommand, null, "(L" + server.name + ";L" + iCommandSender.name + ";)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommand canCommandSenderUseCommand (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;)Z",
					iCommand.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		
		// POST-15w35c, added MinecraftServer
	    // List addTabCompletionOptions(MinecraftServer, ICommandSender sender, String[] args, BlockPos pos);		
		methods = getMatchingMethods(iCommand, null, "(L" + server.name + ";L" + iCommandSender.name + ";[Ljava/lang/String;L" + blockPos.name + ";)Ljava/util/List;");		
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommand addTabCompletionOptions (Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;Lnet/minecraft/util/BlockPos;)Ljava/util/List;",
					iCommand.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
	    //boolean isUsernameIndex(String[] args, int index);
		methods = getMatchingMethods(iCommand, null, "([Ljava/lang/String;I)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommand isUsernameIndex ([Ljava/lang/String;I)Z",
					iCommand.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/command/CommandResultStats",
			"net/minecraft/command/CommandResultStats$Type",
			},
			providesMethods={
			"net/minecraft/command/ICommandSender getCommandSenderName ()Ljava/lang/String;",
			"net/minecraft/command/ICommandSender getDisplayName ()Lnet/minecraft/util/IChatComponent;",
			"net/minecraft/command/ICommandSender addChatMessage (Lnet/minecraft/util/IChatComponent;)V",
			"net/minecraft/command/ICommandSender canCommandSenderUseCommand (ILjava/lang/String;)Z",
			"net/minecraft/command/ICommandSender getPosition ()Lnet/minecraft/util/BlockPos;",
			"net/minecraft/command/ICommandSender getPositionVector ()Lnet/minecraft/util/Vec3;",
			"net/minecraft/command/ICommandSender getEntityWorld ()Lnet/minecraft/world/World;",
			"net/minecraft/command/ICommandSender getCommandSenderEntity ()Lnet/minecraft/entity/Entity;",
			"net/minecraft/command/ICommandSender sendCommandFeedback ()Z",
			"net/minecraft/command/ICommandSender setCommandStat (Lnet/minecraft/command/CommandResultStats$Type;I)V"
			},
			depends={
			"net/minecraft/command/ICommandSender",
			"net/minecraft/util/IChatComponent",
			"net/minecraft/util/BlockPos",
			"net/minecraft/util/Vec3",
			"net/minecraft/world/World",
			"net/minecraft/entity/Entity"
			})
	public boolean processICommandSenderClass()
	{
		ClassNode iCommandSender = getClassNodeFromMapping("net/minecraft/command/ICommandSender");
		ClassNode iChatComponent = getClassNodeFromMapping("net/minecraft/util/IChatComponent");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode vec3 = getClassNodeFromMapping("net/minecraft/util/Vec3");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode entity = getClassNodeFromMapping("net/minecraft/entity/Entity");
		if (!MeddleUtil.notNull(iCommandSender, iChatComponent, blockPos, vec3, world, entity)) return false;
		
	    // String getCommandSenderName();
		List<MethodNode> methods = getMatchingMethods(iCommandSender, null, "()Ljava/lang/String;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommandSender getCommandSenderName ()Ljava/lang/String;",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		

	    // IChatComponent getDisplayName();
		methods = getMatchingMethods(iCommandSender, null, "()L" + iChatComponent.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommandSender getDisplayName ()Lnet/minecraft/util/IChatComponent;",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
	    // void addChatMessage(IChatComponent component);
		methods = getMatchingMethods(iCommandSender, null, "(L" + iChatComponent.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommandSender addChatMessage (Lnet/minecraft/util/IChatComponent;)V",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
				
	    // boolean canCommandSenderUseCommand(int permLevel, String commandName);
		methods = getMatchingMethods(iCommandSender, null, "(ILjava/lang/String;)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommandSender canCommandSenderUseCommand (ILjava/lang/String;)Z",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
	    // BlockPos getPosition();
		methods = getMatchingMethods(iCommandSender, null, "()L" + blockPos.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommandSender getPosition ()Lnet/minecraft/util/BlockPos;",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);		
		}
		
	    // Vec3 getPositionVector();
		methods = getMatchingMethods(iCommandSender, null, "()L" + vec3.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommandSender getPositionVector ()Lnet/minecraft/util/Vec3;",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
	    // World getEntityWorld();
		methods = getMatchingMethods(iCommandSender, null, "()L" + world.name + ";");
		if (methods.size() == 1) {	
			addMethodMapping("net/minecraft/command/ICommandSender getEntityWorld ()Lnet/minecraft/world/World;",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);	
		}		
		
	    // Entity getCommandSenderEntity();
		methods = getMatchingMethods(iCommandSender, null, "()L" + entity.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommandSender getCommandSenderEntity ()Lnet/minecraft/entity/Entity;",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
	    // boolean sendCommandFeedback();
		methods = getMatchingMethods(iCommandSender, null, "()Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/command/ICommandSender sendCommandFeedback ()Z",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
	    // void setCommandStat(CommandResultStats.Type type, int amount);
		String commandResultStatsType_name = null;
		methods.clear();
		for (MethodNode method : iCommandSender.methods) {
			if (!method.desc.endsWith(";I)V")) continue;
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length != 2) continue;
			commandResultStatsType_name = args[0].getClassName();
			methods.add(method);
		}		
		if (methods.size() == 1) {			
			if (commandResultStatsType_name.contains("$")) {
				String baseName = commandResultStatsType_name.substring(0, commandResultStatsType_name.indexOf('$'));
				addClassMapping("net/minecraft/command/CommandResultStats", baseName);
			}			
			addClassMapping("net/minecraft/command/CommandResultStats$Type", commandResultStatsType_name);
			addMethodMapping("net/minecraft/command/ICommandSender setCommandStat (Lnet/minecraft/command/CommandResultStats$Type;I)V",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}


	@Mapping(provides="net/minecraft/entity/player/EntityPlayerMP", depends="net/minecraft/server/MinecraftServer")
	public boolean getEntityPlayerMPClass()
	{
		ClassNode server = getClassNodeFromMapping("net/minecraft/server/MinecraftServer");
		if (server == null) return false;
		
		List<MethodNode> methods = getMatchingMethods(server, null, "()V");
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.CHECKCAST) continue;
				TypeInsnNode tn = (TypeInsnNode)insn;
				if (searchConstantPoolForStrings(tn.desc, "en_US", "playerGameType", "Player being ticked")) {
					addClassMapping("net/minecraft/entity/player/EntityPlayerMP", tn.desc);
					return true;
				}
			}
		}	
		
		return false;
	}
	
	
	@Mapping(provides={
			"net/minecraft/network/play/server/S2DPacketOpenWindow",
			"net/minecraft/network/NetHandlerPlayServer",
			"net/minecraft/inventory/ICrafting"
			},
			providesFields={
			"net/minecraft/entity/player/EntityPlayer openContainer Lnet/minecraft/inventory/Container;",
			"net/minecraft/inventory/Container windowId I"
			},
			providesMethods={
			"net/minecraft/network/NetHandlerPlayServer sendPacket (Lnet/minecraft/network/Packet;)V",
			"net/minecraft/inventory/Container onCraftGuiOpened (Lnet/minecraft/inventory/ICrafting;)V"
			},
			depends={
			"net/minecraft/entity/player/EntityPlayerMP",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/world/IInteractionObject",
			"net/minecraft/network/Packet",
			"net/minecraft/inventory/Container"
			})
	public boolean processEntityPlayerMPClass()
	{
		ClassNode playerMP = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayerMP");
		ClassNode player = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode iInteractionObject = getClassNodeFromMapping("net/minecraft/world/IInteractionObject");
		ClassNode packet = getClassNodeFromMapping("net/minecraft/network/Packet");
		ClassNode container = getClassNodeFromMapping("net/minecraft/inventory/Container");
		if (!MeddleUtil.notNull(playerMP, player, iInteractionObject, packet, container)) return false;
		
		
		String iCrafting_name = null;
		if (playerMP.interfaces.size() == 1) {
			String className = playerMP.interfaces.get(0);
			ClassNode iCrafting = getClassNode(className);
			if (iCrafting != null) {
				boolean matched = true;
				for (MethodNode method : iCrafting.methods) {
					Type t = Type.getMethodType(method.desc);
					if (t.getReturnType().getSort() != Type.VOID) matched = false;
					if (t.getArgumentTypes().length < 1) matched = false;
					if (!t.getArgumentTypes()[0].getClassName().equals(container.name)) matched = false;					
				}
				if (matched) {
					addClassMapping("net/minecraft/inventory/ICrafting", iCrafting);
					iCrafting_name = iCrafting.name;
				}
			}
		}
		
		
		List<MethodNode> methods = getMatchingMethods(playerMP, null, "(L" + iInteractionObject.name + ";)V");
		if (methods.size() == 1) {
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() == Opcodes.NEW) {
					TypeInsnNode tn = (TypeInsnNode)insn;
					ClassNode cn = getClassNode(tn.desc);
					if (cn == null) continue;
					for (String iface : cn.interfaces) {
						if (iface.equals(packet.name))
							addClassMapping("net/minecraft/network/play/server/S2DPacketOpenWindow", cn.name);
					}
				}
				
				// net/minecraft/network/NetHandlerPlayServer.sendPacket (Lnet/minecraft/network/Packet;)V
				if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
					
					MethodInsnNode mn = (MethodInsnNode)insn;
					if (mn.desc.equals("(L" + packet.name + ";)V")) {						
						if (searchConstantPoolForStrings(mn.owner, "keepAlive", "Sending packet")) {
							addClassMapping("net/minecraft/network/NetHandlerPlayServer", mn.owner);
							addMethodMapping("net/minecraft/network/NetHandlerPlayServer sendPacket (Lnet/minecraft/network/Packet;)V",
									mn.owner + " " + mn.name + " " + mn.desc);
						}
					}
					
					
					// public void Container.onCraftGuiOpened(ICrafting listener)
					if (mn.owner.equals(container.name) && mn.desc.equals("(L" + iCrafting_name + ";)V")) {
						addMethodMapping("net/minecraft/inventory/Container onCraftGuiOpened (Lnet/minecraft/inventory/ICrafting;)V",
								container.name + " " + mn.name + " " + mn.desc);
					}
				}
				
				if (insn.getOpcode() == Opcodes.PUTFIELD) {
					FieldInsnNode fn = (FieldInsnNode)insn;
					if (fn.owner.equals(playerMP.name) && fn.desc.equals("L" + container.name + ";")) {
						addFieldMapping("net/minecraft/entity/player/EntityPlayer openContainer Lnet/minecraft/inventory/Container;",
								player.name + " " + fn.name + " " + fn.desc);
					}
					if (fn.owner.equals(container.name) && fn.desc.equals("I")) {
						addFieldMapping("net/minecraft/inventory/Container windowId I",
								container.name + " " + fn.name + " " + fn.desc);
					}
				}
			}
		}		
		return true;
	}


	
	@Mapping(providesFields={
			"net/minecraft/util/AxisAlignedBB minX D",
			"net/minecraft/util/AxisAlignedBB minY D",
			"net/minecraft/util/AxisAlignedBB minZ D",
			"net/minecraft/util/AxisAlignedBB maxX D",
			"net/minecraft/util/AxisAlignedBB maxY D",
			"net/minecraft/util/AxisAlignedBB maxZ D"
			},
			providesMethods={
			"net/minecraft/util/AxisAlignedBB offset (Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;"
			},
			depends={
			"net/minecraft/util/AxisAlignedBB",
			"net/minecraft/util/BlockPos"
			})
	public boolean processAxisAlignedBBClass()
	{
		ClassNode aabb = getClassNodeFromMapping("net/minecraft/util/AxisAlignedBB");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		if (!MeddleUtil.notNull(aabb, blockPos)) return false;	
		
		MethodNode toString = DynamicMappings.getMethodNode(aabb, "- toString ()Ljava/lang/String;");
		if (toString == null) return false;
		List<FieldInsnNode> fieldInsns = DynamicMappings.getAllInsnNodesOfType(toString.instructions.getFirst(), FieldInsnNode.class);
		if (fieldInsns.size() == 6) {
			String[] fieldNames = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};
			for (int n = 0; n < 6; n++) {
				FieldInsnNode fn = fieldInsns.get(n);
				if (fn.owner.equals(aabb.name)) {
					addFieldMapping("net/minecraft/util/AxisAlignedBB " + fieldNames[n] + " D",
							fn.owner + " " + fn.name + " " + fn.desc);
				}
			}
		}
		
		// public AxisAlignedBB offset(BlockPos param0)
		List<MethodNode> methods = getMatchingMethods(aabb, null, "(L" + blockPos.name + ";)L" + aabb.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/AxisAlignedBB offset (Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;",
					aabb.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}
	
	
	@Mapping(providesMethods={
			"net/minecraft/util/RegistryNamespaced getIDForObject (Ljava/lang/Object;)I",
			"net/minecraft/util/RegistryNamespaced getObjectById (I)Ljava/lang/Object;",
			"net/minecraft/util/RegistryNamespaced containsKey (Ljava/lang/Object;)Z",
			"net/minecraft/util/RegistryNamespaced register (ILjava/lang/Object;Ljava/lang/Object;)V",
			"net/minecraft/util/RegistryNamespaced getObject (Ljava/lang/Object;)Ljava/lang/Object;",
			"net/minecraft/util/RegistryNamespaced getNameForObject (Ljava/lang/Object;)Ljava/lang/Object;"
			},
			depends={
			"net/minecraft/util/RegistryNamespaced"
			})
	public boolean processRegistryNamespacedClass()
	{
		ClassNode registry = getClassNodeFromMapping("net/minecraft/util/RegistryNamespaced");
		if (!MeddleUtil.notNull(registry)) return false;
		
		// public int getIDForObject(Object p_148757_1_)
		List<MethodNode> methods = getMatchingMethods(registry, null, "(Ljava/lang/Object;)I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/RegistryNamespaced getIDForObject (Ljava/lang/Object;)I",
					registry.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public Object getObjectById(int id)
		methods = getMatchingMethods(registry, null, "(I)Ljava/lang/Object;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/RegistryNamespaced getObjectById (I)Ljava/lang/Object;",
					registry.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public boolean containsKey(Object p_148741_1_)
		methods = getMatchingMethods(registry, null, "(Ljava/lang/Object;)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/RegistryNamespaced containsKey (Ljava/lang/Object;)Z",
					registry.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public void register(int id, Object p_177775_2_, Object p_177775_3_)		
		methods = getMatchingMethods(registry, null, "(ILjava/lang/Object;Ljava/lang/Object;)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/util/RegistryNamespaced register (ILjava/lang/Object;Ljava/lang/Object;)V",
					registry.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public Object getObject(Object p_82594_1_)
		// public Object getNameForObject(Object p_177774_1_)
		methods = getMatchingMethods(registry, null, "(Ljava/lang/Object;)Ljava/lang/Object;");
		if (methods.size() == 2) {
			MethodNode m1 = methods.get(0);
			MethodNode m2 = methods.get(1);
			
			if (matchOpcodeSequence(m1.instructions.getFirst(), Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.INVOKESPECIAL, Opcodes.ARETURN) &&
				matchOpcodeSequence(m2.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.ALOAD, Opcodes.INVOKEINTERFACE, Opcodes.ARETURN)) 
			{				
				addMethodMapping("net/minecraft/util/RegistryNamespaced getObject (Ljava/lang/Object;)Ljava/lang/Object;", registry.name + " " + m1.name + " " + m1.desc);
				addMethodMapping("net/minecraft/util/RegistryNamespaced getNameForObject (Ljava/lang/Object;)Ljava/lang/Object;", registry.name + " " + m2.name + " " + m2.desc);				
			}
		}
		
		
		return true;
	}
	

	
	@Mapping(providesFields={
			"net/minecraft/util/MainOrOffHand MAIN_HAND Lnet/minecraft/util/MainOrOffHand;",
			"net/minecraft/util/MainOrOffHand OFF_HAND Lnet/minecraft/util/MainOrOffHand;"
			},
			depends="net/minecraft/util/MainOrOffHand")
	public boolean processMainOrOffHand()
	{
		ClassNode mainOrOffHand = getClassNodeFromMapping("net/minecraft/util/MainOrOffHand");
		if (mainOrOffHand == null) return false;
		
		List<FieldNode> fields = getMatchingFields(mainOrOffHand, null, "L" + mainOrOffHand.name + ";");
		if (fields.size() == 2) {
			addFieldMapping("net/minecraft/util/MainOrOffHand MAIN_HAND Lnet/minecraft/util/MainOrOffHand;", 
					mainOrOffHand.name + " " + fields.get(0).name + " " + fields.get(0).desc);
			addFieldMapping("net/minecraft/util/MainOrOffHand OFF_HAND Lnet/minecraft/util/MainOrOffHand;", 
					mainOrOffHand.name + " " + fields.get(1).name + " " + fields.get(1).desc);
		}
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/inventory/EnumEquipSlot"
			},
			providesMethods={
			"net/minecraft/entity/EntityLivingBase getHeldItem (Lnet/minecraft/util/MainOrOffHand;)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/entity/EntityLivingBase getHeldMainHandItem ()Lnet/minecraft/item/ItemStack;",
			"net/minecraft/entity/EntityLivingBase getHeldOffHandItem ()Lnet/minecraft/item/ItemStack;",
			"net/minecraft/entity/EntityLivingBase setHeldItem (Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;)V"
			},
			depends={
			"net/minecraft/entity/EntityLivingBase",
			"net/minecraft/item/ItemStack",
			"net/minecraft/util/MainOrOffHand"
			})
	public boolean processEntityLivingBase()
	{
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode mainOrOffHand = getClassNodeFromMapping("net/minecraft/util/MainOrOffHand");
		if (!MeddleUtil.notNull(entityLivingBase, itemStack, mainOrOffHand)) return false;
		
		String enumEquipSlot_name = null;
		
		//public ItemStack getHeldItem(MainOrOffHand param0)
		List<MethodNode> methods = getMatchingMethods(entityLivingBase, null, "(L" + mainOrOffHand.name + ";)L" + itemStack.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/entity/EntityLivingBase getHeldItem (Lnet/minecraft/util/MainOrOffHand;)Lnet/minecraft/item/ItemStack;",
					entityLivingBase.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			for (MethodInsnNode mn : nodes) {
				if (!mn.owner.equals(entityLivingBase.name)) continue;
				if (!mn.desc.startsWith("(L") || !mn.desc.endsWith(")L" + itemStack.name + ";")) continue;
				Type t = Type.getMethodType(mn.desc);
				Type[] args = t.getArgumentTypes();				
				if (args.length != 1) continue;
				String className = args[0].getClassName();
				if (searchConstantPoolForStrings(className, "MAINHAND", "OFFHAND", "FEET", "LEGS")) {
					addClassMapping("net/minecraft/inventory/EnumEquipSlot", className);
					enumEquipSlot_name = className;
					break;
				}				
			}
		}		
		
		
		// ItemStack getHeldMainHandItem(), ItemStack getHeldOffHandItem()
		methods = getMatchingMethods(entityLivingBase, null, "()L" + itemStack.name + ";");
		// Remove getItemInUse() in case we're on the client
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext(); ) {
			MethodNode method = it.next();
			if (matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.ARETURN)) it.remove();
		}		
		if (methods.size() == 2 && enumEquipSlot_name != null) {
			List<FieldInsnNode> nodes1 = getAllInsnNodesOfType(methods.get(0), FieldInsnNode.class);
			List<FieldInsnNode> nodes2 = getAllInsnNodesOfType(methods.get(1), FieldInsnNode.class);
			
			if (nodes1.size() == 1 && nodes2.size() == 1) {
				FieldInsnNode fn1 = nodes1.get(0);
				FieldInsnNode fn2 = nodes2.get(0);
				
				if (fn1.owner.equals(enumEquipSlot_name) && fn2.owner.equals(enumEquipSlot_name)) {
					
					MethodNode main = null;
					MethodNode off = null;
					
					if (fn1.name.equals("a") && fn2.name.equals("b")) {
						main = methods.get(0);
						off = methods.get(1);
					}
					else if (fn1.name.equals("b") && fn2.name.equals("a")) {
						main = methods.get(1);
						off = methods.get(0);
					}
					
					if (main != null && off != null) {					
						addMethodMapping("net/minecraft/entity/EntityLivingBase getHeldMainHandItem ()Lnet/minecraft/item/ItemStack;",
							entityLivingBase.name + " " + main.name + " " + main.desc);
						addMethodMapping("net/minecraft/entity/EntityLivingBase getHeldOffHandItem ()Lnet/minecraft/item/ItemStack;",
							entityLivingBase.name + " " + off.name + " " + off.desc);
					}
					
				}
			}
		}
		
		
		// void setHeldItem(MainOrOffHand, ItemStack)
		methods = getMatchingMethods(entityLivingBase, null, "(L" + mainOrOffHand.name + ";L" + itemStack.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/entity/EntityLivingBase setHeldItem (Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/item/ItemStack;)V",
					entityLivingBase.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}
	
	
	
	@Mapping(provides="net/minecraft/block/properties/PropertyInteger",
			providesFields={
			"net/minecraft/block/BlockLiquid LEVEL Lnet/minecraft/block/properties/PropertyInteger;"
			},
			providesMethods={
			"net/minecraft/block/Block isPassable (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)Z",
			"net/minecraft/block/Block tickRate (Lnet/minecraft/world/World;)I",
			"net/minecraft/block/properties/PropertyInteger create (Ljava/lang/String;II)Lnet/minecraft/block/properties/PropertyInteger;"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/block/BlockLiquid",
			"net/minecraft/world/IBlockAccess",
			"net/minecraft/util/BlockPos",
			"net/minecraft/world/World"
			})
	public boolean processBlockLiquidClass()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode blockLiquid = getClassNodeFromMapping("net/minecraft/block/BlockLiquid");
		ClassNode iBlockAccess = getClassNodeFromMapping("net/minecraft/world/IBlockAccess");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		if (!MeddleUtil.notNull(block, blockLiquid, iBlockAccess, blockPos, world)) return false;
		
		
		MethodNode clinit = getMethodNode(blockLiquid, "--- <clinit> ()V");
		if (clinit != null) {
			List<FieldInsnNode> fieldNodes = getAllInsnNodesOfType(clinit, FieldInsnNode.class);
			List<MethodInsnNode> methodNodes = getAllInsnNodesOfType(clinit, MethodInsnNode.class);
			
			String propertyInteger = null;
			
			if (fieldNodes.size() == 1 && fieldNodes.get(0).owner.equals(blockLiquid.name)) {
				String className = Type.getType(fieldNodes.get(0).desc).getClassName();
				if (searchConstantPoolForStrings(className, "Min value of ", " must be 0 or greater")) {
					addClassMapping("net/minecraft/block/properties/PropertyInteger", className);
					propertyInteger = className;
					
					addFieldMapping("net/minecraft/block/BlockLiquid LEVEL Lnet/minecraft/block/properties/PropertyInteger;",
							blockLiquid.name + " " + fieldNodes.get(0).name + " " + fieldNodes.get(0).desc);
				}
			}
			
			if (methodNodes.size() == 1 && propertyInteger != null) {
				MethodInsnNode mn = methodNodes.get(0);
				if (mn.owner.equals(propertyInteger) && mn.desc.equals("(Ljava/lang/String;II)L" + propertyInteger + ";")) {
					addMethodMapping("net/minecraft/block/properties/PropertyInteger create (Ljava/lang/String;II)Lnet/minecraft/block/properties/PropertyInteger;",
							propertyInteger + " " + mn.name + " " + mn.desc);
				}
			}
		}
		
		
		// public boolean isPassable(IBlockAccess worldIn, BlockPos pos)
		List<MethodNode> methods = getMatchingMethods(blockLiquid, null, assembleDescriptor("(", iBlockAccess, blockPos, ")Z"));
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			MethodNode method = it.next();
			if (getMethodNode(block, "--- " + method.name + " " + method.desc) == null) it.remove();
		}
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block isPassable (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			// TODO - Get Material.lava
		}
		
		
		// public int tickRate(World worldIn)
		methods = getMatchingMethods(blockLiquid, null, assembleDescriptor("(", world, ")I"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block tickRate (Lnet/minecraft/world/World;)I",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	

	@Mapping(providesFields={
			"net/minecraft/block/Block AIR_ID Lnet/minecraft/util/ResourceLocation;"
			},
			providesMethods={
			"net/minecraft/block/state/IBlockWrapper getMaterial ()Lnet/minecraft/block/material/Material;",
			"net/minecraft/block/material/Material isSolid ()Z",
			"net/minecraft/block/Block isFullBlock (Lnet/minecraft/block/state/IBlockState;)Z",
			"net/minecraft/block/Block getUseNeighborBrightness (Lnet/minecraft/block/state/IBlockState;)Z"
			},
			dependsFields={
			"net/minecraft/block/Block fullBlock Z",
			"net/minecraft/block/Block useNeighborBrightness Z"			
			},
			dependsMethods={
			"net/minecraft/block/Block isBlockSolid (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/block/state/IBlockWrapper",
			"net/minecraft/block/material/Material",
			"net/minecraft/util/ResourceLocation"
			})	
	public boolean processBlockClass4()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode iBlockWrapper = getClassNodeFromMapping("net/minecraft/block/state/IBlockWrapper");
		ClassNode material = getClassNodeFromMapping("net/minecraft/block/material/Material");
		ClassNode resourceLocation = getClassNodeFromMapping("net/minecraft/util/ResourceLocation");
		if (!MeddleUtil.notNull(block, iBlockState, iBlockWrapper, material, resourceLocation)) return false;
		
		
		// Get Block.AIR_ID field
		String air_id_name = null;
		int count = 0;
		for (FieldNode field : block.fields) {
			if (field.desc.equals("L" + resourceLocation.name + ";")) {
				air_id_name = field.name;
				count++;
			}
		}
		if (air_id_name != null && count == 1) {
			addFieldMapping("net/minecraft/block/Block AIR_ID Lnet/minecraft/util/ResourceLocation;",
					block.name + " " + air_id_name + " L" + resourceLocation.name + ";");
		}
		
		
		MethodNode isBlockSolid = getMethodNodeFromMapping(block, "net/minecraft/block/Block isBlockSolid (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z");
		if (isBlockSolid != null) {
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(isBlockSolid, MethodInsnNode.class);
			List<MethodInsnNode> getMaterial = new ArrayList<>();
			List<MethodInsnNode> isSolid = new ArrayList<>();
			for (MethodInsnNode mn : nodes) {
				if (mn.owner.equals(iBlockState.name) && mn.desc.equals("()L" + material.name + ";")) getMaterial.add(mn);
				else if (mn.owner.equals(material.name) && mn.desc.equals("()Z")) isSolid.add(mn);
			}
			
			if (getMaterial.size() == 1) {
				addMethodMapping("net/minecraft/block/state/IBlockWrapper getMaterial ()Lnet/minecraft/block/material/Material;",
						iBlockWrapper.name + " " + getMaterial.get(0).name + " " + getMaterial.get(0).desc);
			}
			if (isSolid.size() == 1) {
				addMethodMapping("net/minecraft/block/material/Material isSolid ()Z",
						material.name + " " + isSolid.get(0).name + " " + isSolid.get(0).desc);
			}
		}
		
		
		FieldNode fullBlock = getFieldNodeFromMapping(block, "net/minecraft/block/Block fullBlock Z");
		FieldNode useNeighborBrightness = getFieldNodeFromMapping(block, "net/minecraft/block/Block useNeighborBrightness Z");
		
		List<MethodNode> methods = getMatchingMethods(block, null, "(L" + iBlockState.name + ";)Z");
		
		if (fullBlock != null && useNeighborBrightness != null) {
			List<MethodNode> isFullBlock = new ArrayList<>();
			List<MethodNode> getUseNeighborBrightness = new ArrayList<>();
			
			for (MethodNode method : methods) {
				AbstractInsnNode[] nodes = getOpcodeSequenceArray(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.IRETURN);
				if (nodes == null) continue;
				FieldInsnNode fn = (FieldInsnNode)nodes[1];
				if (fn.name.equals(fullBlock.name)) isFullBlock.add(method);
				else if (fn.name.equals(useNeighborBrightness.name)) getUseNeighborBrightness.add(method);
			}
			
			if (isFullBlock.size() == 1) {
				addMethodMapping("net/minecraft/block/Block isFullBlock (Lnet/minecraft/block/state/IBlockState;)Z",
						block.name + " " + isFullBlock.get(0).name + " " + isFullBlock.get(0).desc);
			}
			if (getUseNeighborBrightness.size() == 1) {
				addMethodMapping("net/minecraft/block/Block getUseNeighborBrightness (Lnet/minecraft/block/state/IBlockState;)Z",
						block.name + " " + getUseNeighborBrightness.get(0).name + " " + getUseNeighborBrightness.get(0).desc);
			}
		}
		
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/stats/StatBase"
			},
			depends={
			"net/minecraft/stats/StatList"
			})
	public boolean processStatListClass()
	{
		ClassNode statList = getClassNodeFromMapping("net/minecraft/stats/StatList");
		if (!MeddleUtil.notNull(statList)) return false;
		
		String statBase = null;
		
		for (FieldNode field : statList.fields) {
			// We just want arrays
			if (!field.desc.startsWith("[")) continue;
			
			// And only arrays of objects
			Type t = Type.getType(field.desc).getElementType();
			if (t.getSort() != Type.OBJECT) continue;
			
			String className = t.getClassName();
			if (searchConstantPoolForStrings(className, "Duplicate stat id: \"", "Stat{id=")) {
				addClassMapping("net/minecraft/stats/StatBase", className);
				statBase = className;
				break;
			}
		}
		
		
		
		return true;
	}
	
}


