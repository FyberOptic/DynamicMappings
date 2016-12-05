package net.fybertech.dynamicmappings.mappers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fybertech.dynamicmappings.DynamicMappings;
import net.fybertech.dynamicmappings.Mapping;
import net.fybertech.dynamicmappings.MappingsClass;
import net.fybertech.meddle.MeddleUtil;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;


@MappingsClass(clientSide=true)
public class ClientMappings extends MappingsBase
{

	@Mapping(provides="net/minecraft/client/main/Main")
	public boolean getMainClass()
	{
		ClassNode main = getClassNode("net/minecraft/client/main/Main");
		if (main == null) return false;
		addClassMapping("net/minecraft/client/main/Main", main);
		return true;
	}


	@Mapping(provides={
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/main/GameConfiguration"},			
			depends="net/minecraft/client/main/Main")
	public boolean getMinecraftClass()
	{
		ClassNode main = getClassNodeFromMapping("net/minecraft/client/main/Main");
		if (main == null) return false;

		List<MethodNode> methods = DynamicMappings.getMatchingMethods(main, "main", "([Ljava/lang/String;)V");
		if (methods.size() != 1) return false;
		MethodNode mainMethod = methods.get(0);

		String minecraftClassName = null;
		String gameConfigClassName = null;
		boolean confirmed = false;

		// We're looking for these instructions:
		// NEW net/minecraft/client/Minecraft
		// INVOKESPECIAL net/minecraft/client/Minecraft.<init> (Lnet/minecraft/client/main/GameConfiguration;)V
		// INVOKEVIRTUAL net/minecraft/client/Minecraft.run ()V
		for (AbstractInsnNode insn = mainMethod.instructions.getLast(); insn != null; insn = insn.getPrevious())
		{
			if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
				MethodInsnNode mn = (MethodInsnNode)insn;
				minecraftClassName = mn.owner;
			}

			else if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
				MethodInsnNode mn = (MethodInsnNode)insn;

				// Check for something wrong
				if (minecraftClassName == null || !mn.owner.equals(minecraftClassName)) return false;

				Type t = Type.getMethodType(mn.desc);
				Type[] args = t.getArgumentTypes();
				if (args.length != 1) return false;

				// Get this while we're here
				gameConfigClassName = args[0].getClassName();
			}

			else if (insn.getOpcode() == Opcodes.NEW) {
				TypeInsnNode vn = (TypeInsnNode)insn;
				if (minecraftClassName != null && vn.desc.equals(minecraftClassName)) {
					confirmed = true;
					break;
				}
			}
		}

		if (confirmed) {
			addClassMapping("net/minecraft/client/Minecraft", getClassNode(minecraftClassName));
			addClassMapping("net/minecraft/client/main/GameConfiguration", getClassNode(gameConfigClassName));
			return true;
		}

		return false;
	}


	
	
	@Mapping(provides={
			"net/minecraft/world/WorldSettings",
			"net/minecraft/server/integrated/IntegratedServer",
			"net/minecraft/client/multiplayer/WorldClient"
			},
			providesFields={
			"net/minecraft/client/Minecraft theWorld Lnet/minecraft/client/multiplayer/WorldClient;"
			},
			providesMethods={
			"net/minecraft/client/Minecraft getMinecraft ()Lnet/minecraft/client/Minecraft;",
			"net/minecraft/client/Minecraft getRenderItem ()Lnet/minecraft/client/renderer/entity/RenderItem;",
			"net/minecraft/client/Minecraft refreshResources ()V",
			"net/minecraft/client/Minecraft launchIntegratedServer (Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
			"net/minecraft/client/Minecraft getIntegratedServer ()Lnet/minecraft/server/integrated/IntegratedServer;",
			"net/minecraft/client/Minecraft getTextureMapBlocks ()Lnet/minecraft/client/renderer/texture/TextureMap;"
			},
			depends={
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/renderer/entity/RenderItem",
			"net/minecraft/world/World",
			"net/minecraft/client/renderer/texture/TextureMap"
			})
	public boolean processMinecraftClass()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode renderItem = getClassNodeFromMapping("net/minecraft/client/renderer/entity/RenderItem");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode textureMap = getClassNodeFromMapping("net/minecraft/client/renderer/texture/TextureMap");
		if (!MeddleUtil.notNull(minecraft, renderItem, world, textureMap)) return false;
		
		
		List<FieldNode> fields;
		
		Set<String> fieldClasses = new HashSet<String>();
		for (FieldNode field : minecraft.fields) {
			if (!field.desc.startsWith("L")) continue;
			Type t = Type.getType(field.desc);			
			fieldClasses.add(t.getClassName());
		}
			
		
		
		// public static Minecraft getMinecraft()
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(minecraft,  null, "()L" + minecraft.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/Minecraft getMinecraft ()Lnet/minecraft/client/Minecraft;",
					minecraft.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public RenderItem getRenderItem()
		methods = DynamicMappings.getMatchingMethods(minecraft, null, "()L" + renderItem.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/Minecraft getRenderItem ()Lnet/minecraft/client/renderer/entity/RenderItem;",
					minecraft.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		boolean found = false;
		
		// public void refreshResources()
		methods = DynamicMappings.getMatchingMethods(minecraft, null, "()V");		
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (!DynamicMappings.isLdcWithString(insn, "Caught error stitching, removing all assigned resourcepacks")) continue;				
				addMethodMapping("net/minecraft/client/Minecraft refreshResources ()V", minecraft.name + " " + method.name + " ()V");
				found = true;				
			}
			if (found) break;
		}
		
		
		String integratedServer_name = null;
		
		// public void launchIntegratedServer(String folderName, String worldName, WorldSettings worldSettingsIn)
		// net/minecraft/world/WorldSettings
		// net/minecraft/server/integrated/IntegratedServer
		methods.clear();
		for (MethodNode method : minecraft.methods) {
			if (!DynamicMappings.checkMethodParameters(method,  Type.OBJECT, Type.OBJECT, Type.OBJECT)) continue;
			if (Type.getMethodType(method.desc).getReturnType().getSort() != Type.VOID) continue;
			if (!method.desc.startsWith("(Ljava/lang/String;Ljava/lang/String;L")) continue;
			methods.add(method);
		}
		if (methods.size() == 1) {
			MethodNode method = methods.get(0);
			Type t = Type.getMethodType(method.desc);
			
			String worldSettings = t.getArgumentTypes()[2].getClassName();
			addClassMapping("net/minecraft/world/WorldSettings", worldSettings);
			addMethodMapping("net/minecraft/client/Minecraft launchIntegratedServer (Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
					minecraft.name + " " + method.name + " " + method.desc);
			
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.NEW) continue;
				TypeInsnNode tn = (TypeInsnNode)insn;
				
				if (DynamicMappings.searchConstantPoolForStrings(tn.desc, "saves", "Generating keypair", "Saving and pausing game...")) {
					integratedServer_name = tn.desc;
					addClassMapping("net/minecraft/server/integrated/IntegratedServer", tn.desc);
					break;
				}
			}
		}
		
		// public IntegratedServer getIntegratedServer()
		if (integratedServer_name != null) {
			methods = DynamicMappings.getMatchingMethods(minecraft, null, "()L" + integratedServer_name + ";");
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/client/Minecraft getIntegratedServer ()Lnet/minecraft/server/integrated/IntegratedServer;",
						minecraft.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		ClassNode worldClient = null;
		
		methods = DynamicMappings.getMatchingMethods(minecraft, null, "(I)V");
		for (MethodNode method : methods) {
			List<FieldInsnNode> fieldNodes = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), FieldInsnNode.class);
			
			String theWorld_name = null;
			int count = 0;
			
			for (FieldInsnNode f : fieldNodes) {
				if (!f.desc.startsWith("L")) continue;
				if (!f.owner.equals(minecraft.name)) continue;
				String className = Type.getType(f.desc).getClassName();
				
				if (worldClient == null) {					
					ClassNode cn = getClassNode(className);
					if (cn == null) continue;
					if (cn.superName.equals(world.name) && DynamicMappings.searchConstantPoolForStrings(className, "MpServer", "doDaylightCycle", "Quitting")) {
						addClassMapping("net/minecraft/client/multiplayer/WorldClient", className);
						worldClient = cn;
					}
				}
				
				if (worldClient != null && worldClient.name.equals(className)) { 
					if (theWorld_name == null) { theWorld_name = f.name; count++; }
					else if (!theWorld_name.equals(f.name)) count++;
				}
			}
			
			if (count == 1) {
				addFieldMapping("net/minecraft/client/Minecraft theWorld Lnet/minecraft/client/multiplayer/WorldClient;",
						minecraft.name + " " + theWorld_name + " L" + worldClient.name + ";");
			}
		}
		
		
		// public TextureMap getTextureMapBlocks()
		methods = DynamicMappings.getMatchingMethods(minecraft, null, "()L" + textureMap.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/Minecraft getTextureMapBlocks ()Lnet/minecraft/client/renderer/texture/TextureMap;",
					minecraft.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}


	
	
	@Mapping(providesMethods={
			"net/minecraft/client/renderer/entity/RenderItem getItemModelMesher ()Lnet/minecraft/client/renderer/ItemModelMesher;"
			},
			depends={
			"net/minecraft/client/renderer/entity/RenderItem",
			"net/minecraft/client/renderer/ItemModelMesher"
			})
	public boolean parseRenderItemClass()
	{
		ClassNode renderItem = getClassNodeFromMapping("net/minecraft/client/renderer/entity/RenderItem");
		ClassNode modelMesher = getClassNodeFromMapping("net/minecraft/client/renderer/ItemModelMesher");
		if (renderItem == null || modelMesher == null) return false;
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(renderItem,  null, "()L" + modelMesher.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/renderer/entity/RenderItem getItemModelMesher ()Lnet/minecraft/client/renderer/ItemModelMesher;",
					renderItem.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	
	

	@Mapping(provides="net/minecraft/client/renderer/entity/RenderItem", depends="net/minecraft/client/Minecraft")
	public boolean getRenderItemClass()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		if (minecraft == null) return false;

		for (MethodNode method : (List<MethodNode>)minecraft.methods) {
			Type t = Type.getMethodType(method.desc);
			if (t.getArgumentTypes().length != 0) continue;
			if (t.getReturnType().getSort() != Type.OBJECT) continue;

			String className = t.getReturnType().getClassName();
			if (className.contains(".")) continue;

			if (DynamicMappings.searchConstantPoolForStrings(className, "textures/misc/enchanted_item_glint.png", "Rendering item")) {
				addClassMapping("net/minecraft/client/renderer/entity/RenderItem", getClassNode(className));
				return true;
			}

			// TODO - Use this to process other getters from Minecraft class

		}

		return false;
	}


	@Mapping(provides= {
			"net/minecraft/client/renderer/ItemModelMesher",
			"net/minecraft/client/renderer/ModelManager",
			"net/minecraft/client/renderer/texture/TextureManager",
			"net/minecraft/client/renderer/color/ItemColors",
			"net/minecraft/client/resources/IResourceManagerReloadListener",
			"net/minecraft/client/resources/IResourceManager"
			},
			providesMethods = {
			"net/minecraft/client/resources/IResourceManagerReloadListener onResourceManagerReload (Lnet/minecraft/client/resources/IResourceManager;)V"
			},
			depends={
			"net/minecraft/client/renderer/entity/RenderItem"
			}
			)
	public boolean processRenderItemClass()
	{
		ClassNode renderItem = getClassNodeFromMapping("net/minecraft/client/renderer/entity/RenderItem");
		if(!MeddleUtil.notNull(renderItem)) return false;


		List<String> interfaces = renderItem.interfaces;
		if(interfaces.size() == 1){
			ClassNode node = getClassNode(interfaces.get(0));
			if(node != null && node.methods.size() == 1){
				addClassMapping("net/minecraft/client/resources/IResourceManagerReloadListener", node);
				addClassMapping("net/minecraft/client/resources/IResourceManager", Type.getArgumentTypes(node.methods.get(0).desc)[0].getClassName());
				addMethodMapping("net/minecraft/client/resources/IResourceManagerReloadListener onResourceManagerReload (Lnet/minecraft/client/resources/IResourceManager;)V", node.name + " " + node.methods.get(0).name + " " + node.methods.get(0).desc);
			}
		}


		List<MethodNode> methods = getMatchingMethods(renderItem, Opcodes.ACC_PUBLIC, Type.VOID, Type.OBJECT, Type.OBJECT, Type.OBJECT);
		if(methods.size() == 1){
			List<ClassNode> argumentClasses = getClassNodesFromMethodArguments(methods.get(0));
			if(argumentClasses.size() == 3){
				//TextureManager
				ClassNode cn = argumentClasses.get(0);
				if(searchConstantPoolForStrings(cn.name, "Failed to load texture: {}", "Resource location being registered", "Texture object class")){
					addClassMapping("net/minecraft/client/renderer/texture/TextureManager", cn);
					cn = null;
				}
				//ModelManager
				if(cn == null){ //To be safe that the mapping for TextureManager has worked
					addClassMapping("net/minecraft/client/renderer/ModelManager", argumentClasses.get(1));
				}
				//ItemColors
				if(cn == null){ //To be safe that the mapping for TextureManager has worked. Can't take 'Colors' as String, cause it isn't in the class itself
					addClassMapping("net/minecraft/client/renderer/color/ItemColors", argumentClasses.get(2));
				}
			}
		}

		methods = getMatchingMethods(renderItem, Opcodes.ACC_PUBLIC, Type.OBJECT);
		if(methods.size() == 1){
			addClassMapping("net/minecraft/client/renderer/ItemModelMesher", Type.getReturnType(methods.get(0).desc).getClassName());
		}

		return true;
	}


	@Mapping(providesMethods={
			"net/minecraft/client/Minecraft startGame ()V",
			"net/minecraft/client/Minecraft getBlockRendererDispatcher ()Lnet/minecraft/client/renderer/BlockRendererDispatcher;"
			},
			provides={
			"net/minecraft/client/gui/GuiMainMenu",  
			"net/minecraft/client/gui/GuiIngame",
			"net/minecraft/client/multiplayer/GuiConnecting",
			"net/minecraft/client/renderer/RenderGlobal",
			"net/minecraft/client/renderer/BlockRendererDispatcher",
			"net/minecraft/client/renderer/texture/TextureMap"
			},
			depends="net/minecraft/client/Minecraft")
	public boolean processMinecraftClass2()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		if (minecraft == null) return false;

		List<String> postStartupClasses = new ArrayList<String>();
		List<String> startupClasses = new ArrayList<String>();

		List<MethodNode> methods = DynamicMappings.getMatchingMethods(minecraft, null, "()V");
		
		boolean foundMethod = false;
		for (MethodNode method : methods) {
			boolean foundLWJGLVersion = false;
			boolean foundPostStartup = false;
			boolean foundStartup = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{				
				if (!foundLWJGLVersion && !DynamicMappings.isLdcWithString(insn, "LWJGL Version: {}")) continue;
				foundLWJGLVersion = true;
				
				if (!foundStartup && !DynamicMappings.isLdcWithString(insn, "Startup")) continue;
				foundStartup = true;
				
				foundMethod = true;
				addMethodMapping("net/minecraft/client/Minecraft startGame ()V", minecraft.name + " " + method.name + " ()V");
				
				if (foundStartup && !foundPostStartup) {
					if (insn.getOpcode() == Opcodes.NEW) {
						TypeInsnNode tn = (TypeInsnNode)insn;
						startupClasses.add(tn.desc);
					}
				}
				
				if (!foundPostStartup && !DynamicMappings.isLdcWithString(insn, "Post startup")) continue;
				foundPostStartup = true;

				if (insn.getOpcode() == Opcodes.NEW) {
					TypeInsnNode tn = (TypeInsnNode)insn;
					postStartupClasses.add(tn.desc);
				}
			}

			if (foundMethod) break;
		}

		String guiIngame = null;
		String guiConnecting = null;
		String guiMainMenu = null;
		String loadingScreenRenderer = null;

		for (String className : postStartupClasses) {			
			if (guiIngame == null && DynamicMappings.searchConstantPoolForStrings(className, "textures/misc/vignette.png", "bossHealth")) {
				guiIngame = className;
				continue;
			}

			if (guiConnecting == null && DynamicMappings.searchConstantPoolForStrings(className, "Connecting to {}, {}", "connect.connecting")) {
				guiConnecting = className;
				continue;
			}

			if (guiMainMenu == null && DynamicMappings.searchConstantPoolForStrings(className, "texts/splashes.txt", "Merry X-mas!")) {
				guiMainMenu = className;
				continue;
			}

			// TODO - Figure out a way to scan for the class
			//if (loadingScreenRenderer == null
		}

		String renderGlobal = null;
		String blockRendererDispatcher = null;
		String textureMap = null;
		
		for (String className : startupClasses) {
			if (textureMap == null && DynamicMappings.searchConstantPoolForStrings(className, "missingno", "textures/atlas/blocks.png")) {
				textureMap = className;
				continue;
			}
			
			if (renderGlobal == null && DynamicMappings.searchConstantPoolForStrings(className, "textures/environment/moon_phases.png", "Exception while adding particle")) {
				renderGlobal = className;
				continue;
			}
			
			if (blockRendererDispatcher == null && DynamicMappings.searchConstantPoolForStrings(className, "Tesselating block in world", "Block being tesselated")) {
				blockRendererDispatcher = className;
				continue;
			}
		}
		
		if (textureMap != null) {
			addClassMapping("net/minecraft/client/renderer/texture/TextureMap", textureMap);
		}
		
		if (blockRendererDispatcher != null) {
			addClassMapping("net/minecraft/client/renderer/BlockRendererDispatcher", blockRendererDispatcher);
			
			// public BlockRendererDispatcher getBlockRendererDispatcher()
			methods = getMatchingMethods(minecraft, null, "()L" + blockRendererDispatcher + ";");
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/client/Minecraft getBlockRendererDispatcher ()Lnet/minecraft/client/renderer/BlockRendererDispatcher;",
						minecraft.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
			

		if (guiMainMenu != null)
			addClassMapping("net/minecraft/client/gui/GuiMainMenu", guiMainMenu);
		
		if (guiIngame != null)
			addClassMapping("net/minecraft/client/gui/GuiIngame", guiIngame);
		
		if (guiConnecting != null)
			addClassMapping("net/minecraft/client/multiplayer/GuiConnecting", guiConnecting);
		
		if (renderGlobal != null)
			addClassMapping("net/minecraft/client/renderer/RenderGlobal",renderGlobal);
		
		return true;
	}
	
	
	
	@Mapping(providesFields={
			"net/minecraft/client/Minecraft itemColors Lnet/minecraft/client/renderer/color/ItemColors;"
			},			
			provides={			
			},
			depends={
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/renderer/color/ItemColors"
			})
	public boolean processMinecraftClass3()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode itemColors = getClassNodeFromMapping("net/minecraft/client/renderer/color/ItemColors");
		if (!MeddleUtil.notNull(minecraft, itemColors)) return false;
		
		List<FieldNode> fields = getMatchingFields(minecraft, null, "L" + itemColors.name + ";");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/client/Minecraft itemColors Lnet/minecraft/client/renderer/color/ItemColors;",
				minecraft.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
		
		return true;
	}
	
	


	@Mapping(provides="net/minecraft/client/resources/model/ModelResourceLocation",
			 depends={
			"net/minecraft/item/Item",
			"net/minecraft/client/renderer/ItemModelMesher"})
	public boolean getModelResourceLocationClass()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemModelMesher = getClassNodeFromMapping("net/minecraft/client/renderer/ItemModelMesher");
		if (!MeddleUtil.notNull(item, itemModelMesher)) return false;

		for (MethodNode method : (List<MethodNode>)itemModelMesher.methods) {
			if (!DynamicMappings.checkMethodParameters(method, Type.OBJECT, Type.INT, Type.OBJECT)) continue;
			Type t = Type.getMethodType(method.desc);
			if (!t.getArgumentTypes()[0].getClassName().equals(item.name)) continue;
			
			addClassMapping("net/minecraft/client/resources/model/ModelResourceLocation", 
					getClassNode(t.getArgumentTypes()[2].getClassName()));
			return true;
		}

		return false;
	}
	
	
	/*
	 * Moved elsewhere in 16w02a 
	 * TODO - Find item color override registry
	 * 
	 * @Mapping(providesMethods={
			"net/minecraft/item/Item getColorFromItemStack (Lnet/minecraft/item/ItemStack;I)I",
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemStack"
			})
	public boolean getItemClassMethods()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		
		// public int getColorFromItemStack(ItemStack, int)
		List<MethodNode> methods = DynamicMappings.getMethodsWithDescriptor(item.methods, "(L" + itemStack.name + ";I)I");
		methods = DynamicMappings.removeMethodsWithFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			MethodNode mn = methods.get(0);
			DynamicMappings.addMethodMapping(
					"net/minecraft/item/Item getColorFromItemStack (Lnet/minecraft/item/ItemStack;I)I",
					item.name + " " + mn.name + " " + mn.desc);
		}
		
		return true;
	}*/
	

	@Mapping(providesMethods={
			"net/minecraft/client/renderer/ItemModelMesher register (Lnet/minecraft/item/Item;ILnet/minecraft/client/resources/model/ModelResourceLocation;)V"
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/client/renderer/ItemModelMesher",
			"net/minecraft/client/resources/model/ModelResourceLocation"
			})
	public boolean parseItemModelMesherClass()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode modelMesher = getClassNodeFromMapping("net/minecraft/client/renderer/ItemModelMesher");
		ClassNode modelResLoc = getClassNodeFromMapping("net/minecraft/client/resources/model/ModelResourceLocation");
		if (item == null || modelMesher == null || modelResLoc == null) return false;
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(modelMesher, null, "(L" + item.name + ";IL" + modelResLoc.name + ";)V");
		if (methods.size() == 1) {			
			addMethodMapping("net/minecraft/client/renderer/ItemModelMesher register (Lnet/minecraft/item/Item;ILnet/minecraft/client/resources/model/ModelResourceLocation;)V",
					modelMesher.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;		
	}
	
	
	@Mapping(provides={
			"net/minecraft/client/gui/Gui",
			"net/minecraft/client/gui/GuiScreen",
			"net/minecraft/client/gui/GuiYesNoCallback"
			},
			depends={
			"net/minecraft/client/gui/GuiMainMenu"
			})	
	public boolean findGuiStuff()
	{
		ClassNode guiMainMenu = getClassNodeFromMapping("net/minecraft/client/gui/GuiMainMenu");
		if (guiMainMenu == null || guiMainMenu.superName == null) return false;		
				
		ClassNode guiScreen = null;
		String guiScreenName = null;
		
		if (DynamicMappings.searchConstantPoolForStrings(guiMainMenu.superName, "Invalid Item!", "java.awt.Desktop")) {
			guiScreenName = guiMainMenu.superName;
			guiScreen = getClassNode(guiScreenName);
			addClassMapping("net/minecraft/client/gui/GuiScreen", guiScreenName);
		}		
		
		if (guiScreen == null || guiScreen.superName == null) return false;
		
		if (guiScreen.interfaces.size() == 1) {
			addClassMapping("net/minecraft/client/gui/GuiYesNoCallback", guiScreen.interfaces.get(0));
		}
		
		if (DynamicMappings.searchConstantPoolForStrings(guiScreen.superName, "textures/gui/options_background.png", "textures/gui/icons.png")) {			
			addClassMapping("net/minecraft/client/gui/Gui", guiScreen.superName);
		}
		
		return true;
	}
	
	
	
	private boolean verifyGuiButtonClass(String className)
	{
		String soundClick = getSoundFieldFull("ui.button.click");
		if (searchConstantPoolForStrings(className, "textures/gui/widgets.png") && searchConstantPoolForFields(className, soundClick))
			return true;
		else return false;
	}
	
	
	@Mapping(provides={
			"net/minecraft/client/gui/FontRenderer",
			"net/minecraft/client/gui/GuiButton"
			},
			providesFields={
			"net/minecraft/client/gui/GuiScreen mc Lnet/minecraft/client/Minecraft;",
			"net/minecraft/client/gui/GuiScreen itemRender Lnet/minecraft/client/renderer/entity/RenderItem;",
			"net/minecraft/client/gui/GuiScreen width I",
			"net/minecraft/client/gui/GuiScreen height I",
			"net/minecraft/client/gui/GuiScreen fontRendererObj Lnet/minecraft/client/gui/FontRenderer;",
			"net/minecraft/client/gui/GuiScreen buttonList Ljava/util/List;"
			},
			providesMethods={
			"net/minecraft/client/Minecraft displayGuiScreen (Lnet/minecraft/client/gui/GuiScreen;)V",
			"net/minecraft/client/gui/GuiScreen setWorldAndResolution (Lnet/minecraft/client/Minecraft;II)V",
			"net/minecraft/client/gui/GuiScreen initGui ()V",
			"net/minecraft/client/gui/GuiScreen drawScreen (IIF)V",
			"net/minecraft/client/gui/GuiScreen keyTyped (CI)V",
			"net/minecraft/client/gui/GuiScreen mouseClicked (III)V",
			"net/minecraft/client/gui/GuiScreen mouseReleased (III)V",
			"net/minecraft/client/gui/GuiScreen onResize (Lnet/minecraft/client/Minecraft;II)V",
			"net/minecraft/client/gui/GuiScreen actionPerformed (Lnet/minecraft/client/gui/GuiButton;)V",
			"net/minecraft/client/gui/GuiScreen drawDefaultBackground ()V",
			"net/minecraft/client/gui/GuiScreen drawWorldBackground (I)V"
			},
			dependsFields={
			"net/minecraft/init/Sounds ui_button_click Lnet/minecraft/util/Sound;"
			},
			depends={
			"net/minecraft/client/gui/GuiScreen",
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/renderer/entity/RenderItem"
			})			
	public boolean processGuiScreenClass()
	{
		ClassNode guiScreen = getClassNodeFromMapping("net/minecraft/client/gui/GuiScreen");
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode renderItem = getClassNodeFromMapping("net/minecraft/client/renderer/entity/RenderItem");
		if (guiScreen == null || minecraft == null || renderItem == null) return false;		
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(minecraft,  null, "(L" + guiScreen.name + ";)V");
		if (methods.size() != 1) return false;
		MethodNode displayGuiScreen = methods.get(0);
		
		addMethodMapping("net/minecraft/client/Minecraft displayGuiScreen (Lnet/minecraft/client/gui/GuiScreen;)V",
				minecraft.name + " " + displayGuiScreen.name + " " + displayGuiScreen.desc);
		
		
		String setWorldAndResolutionName = null;
		String setWorldAndResolutionDesc = "(L" + minecraft.name + ";II)V";
		
		for (AbstractInsnNode insn = displayGuiScreen.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode)) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;
			if (mn.owner.equals(guiScreen.name) && mn.desc.equals(setWorldAndResolutionDesc)) {
				setWorldAndResolutionName = mn.name;
				break;
			}
		}		
		if (setWorldAndResolutionName == null) return false;
		
		addMethodMapping("net/minecraft/client/gui/GuiScreen setWorldAndResolution (Lnet/minecraft/client/Minecraft;II)V",
				guiScreen.name + " " + setWorldAndResolutionName + " " + setWorldAndResolutionDesc);
		
		methods = DynamicMappings.getMatchingMethods(guiScreen, setWorldAndResolutionName, setWorldAndResolutionDesc);
		MethodNode setWorldAndResolution = methods.get(0);
		if (setWorldAndResolution == null) return false;
		
		AbstractInsnNode prevInsn = null;
		List<FieldInsnNode> unknownFields = new ArrayList<FieldInsnNode>();
		List<FieldInsnNode> unknownListFields = new ArrayList<FieldInsnNode>();
		List<MethodInsnNode> unknownVoidMethods = new ArrayList<MethodInsnNode>();
		
		for (AbstractInsnNode insn = setWorldAndResolution.instructions.getFirst(); insn != null; insn = insn.getNext()) 
		{
			if (insn.getOpcode() == Opcodes.PUTFIELD) {
				FieldInsnNode fn = (FieldInsnNode)insn;
				
				if (fn.desc.equals("L" + minecraft.name + ";")) {				
					addFieldMapping("net/minecraft/client/gui/GuiScreen mc Lnet/minecraft/client/Minecraft;",
							guiScreen.name + " " + fn.name + " " + fn.desc);
				}
				else if (fn.desc.equals("L" + renderItem.name + ";")) {					
					addFieldMapping("net/minecraft/client/gui/GuiScreen itemRender Lnet/minecraft/client/renderer/entity/RenderItem;",
							renderItem.name + " " + fn.name + " " + fn.desc);
				}
				else if (fn.desc.equals("I")) {
					if (prevInsn.getOpcode() == Opcodes.ILOAD) {
						VarInsnNode vn = (VarInsnNode)prevInsn;
						if (vn.var == 2) {
							addFieldMapping("net/minecraft/client/gui/GuiScreen width I",
									guiScreen.name + " " + fn.name + " I");
						}
						else if (vn.var == 3) {
							addFieldMapping("net/minecraft/client/gui/GuiScreen height I",
									guiScreen.name + " " + fn.name + " I");
						}
					}
				}
				else if (fn.desc.startsWith("L")) {
					unknownFields.add(fn);
				}
			}
			else if (insn.getOpcode() == Opcodes.GETFIELD) {
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.desc.equals("Ljava/util/List;")) unknownListFields.add(fn);
			}
			else if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (mn.desc.equals("()V")) unknownVoidMethods.add(mn);
			}
			
			prevInsn = insn;
		}
		
		// Should have only been one unknown field set in setWorldAndResolution,
		// and that should be for the FontRenderer.
		if (unknownFields.size() == 1) {
			FieldInsnNode fn = unknownFields.get(0);
			Type t = Type.getType(fn.desc);
			String fontRendererName = t.getClassName();
			if (DynamicMappings.searchConstantPoolForStrings(fontRendererName, "0123456789abcdef")) 
			{
				addFieldMapping("net/minecraft/client/gui/GuiScreen fontRendererObj Lnet/minecraft/client/gui/FontRenderer;",
						guiScreen.name + " " + fn.name + " " + fn.desc);
				addClassMapping("net/minecraft/client/gui/FontRenderer", fontRendererName);		
			}
		}
		
		if (unknownListFields.size() == 1) {
			FieldInsnNode fn = unknownListFields.get(0);
			addFieldMapping("net/minecraft/client/gui/GuiScreen buttonList Ljava/util/List;",
					guiScreen.name + " " + fn.name + " " + fn.desc);
		}
		
		if (unknownVoidMethods.size() == 1) {
			MethodInsnNode mn = unknownVoidMethods.get(0);
			addMethodMapping("net/minecraft/client/gui/GuiScreen initGui ()V", guiScreen.name + " " + mn.name + " ()V");
		}
		

		String drawScreenMethodName = null;
		methods = DynamicMappings.getMatchingMethods(guiScreen, null, "(IIF)V");
		if (methods.size() == 1) {
			drawScreenMethodName = methods.get(0).name;
			addMethodMapping("net/minecraft/client/gui/GuiScreen drawScreen (IIF)V", 
					guiScreen.name + " " + drawScreenMethodName + " (IIF)V");
		}
		if (drawScreenMethodName == null) return false;
		
		
		// protected void keyTyped(char typedChar, int keyCode) throws IOException
		methods = DynamicMappings.getMatchingMethods(guiScreen, null, "(CI)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/GuiScreen keyTyped (CI)V",
					guiScreen.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		String guiButton = null;
		
		// protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException
		// protected void mouseReleased(int mouseX, int mouseY, int state)
		methods = DynamicMappings.getMatchingMethods(guiScreen, null, "(III)V");
		if (methods.size() == 2) {
			MethodNode mouseClicked = null;
			MethodNode mouseReleased = null;
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() == Opcodes.CHECKCAST) {
						TypeInsnNode tn = (TypeInsnNode)insn;
						if (verifyGuiButtonClass(tn.desc)) {
							addClassMapping("net/minecraft/client/gui/GuiButton", tn.desc);
							guiButton = tn.desc;
						}
						continue;
					}					
					if (insn.getOpcode() != Opcodes.GETFIELD) continue;
					FieldInsnNode fn = (FieldInsnNode)insn;
					if (!fn.owner.equals(guiScreen.name)) continue;
					if (fn.desc.equals("Ljava/util/List;"))	mouseClicked = method;
					else mouseReleased = method;
				}
			}
			if (mouseClicked != null && mouseReleased != null && mouseClicked != mouseReleased) {
				addMethodMapping("net/minecraft/client/gui/GuiScreen mouseClicked (III)V",
						guiScreen.name + " " + mouseClicked.name + " " + mouseClicked.desc);
				addMethodMapping("net/minecraft/client/gui/GuiScreen mouseReleased (III)V",
						guiScreen.name + " " + mouseReleased.name + " " + mouseReleased.desc);
			}
		}
		
		// public void onResize(Minecraft mcIn, int p_175273_2_, int p_175273_3_)
		methods = getMatchingMethods(guiScreen, null, "(L" + minecraft.name + ";II)V");
		if (methods.size() == 2) {
			for (Iterator<MethodNode> it  = methods.iterator(); it.hasNext();) {				
				if (it.next().name.equals(setWorldAndResolutionName)) it.remove();
			}
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/client/gui/GuiScreen onResize (Lnet/minecraft/client/Minecraft;II)V",
						guiScreen.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		
		// protected void actionPerformed(GuiButton button) throws IOException
		methods = getMatchingMethods(guiScreen, null, "(L" + guiButton + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/GuiScreen actionPerformed (Lnet/minecraft/client/gui/GuiButton;)V",
					guiScreen.name + " " + methods.get(0).name + " " + methods.get(0).desc);			
		}
		
		
		// public void drawDefaultBackground()
		methods = getMatchingMethods(guiScreen, null, "()V");
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			MethodNode method = it.next();
			if (!matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.ICONST_0, Opcodes.INVOKEVIRTUAL, Opcodes.RETURN))
				it.remove();
		}
		MethodNode drawWorldBackground = null;
		if (methods.size() == 1) {
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			MethodInsnNode mn = nodes.get(0);
			// public void drawWorldBackground(int tint)
			if (mn.owner.equals(guiScreen.name) && mn.desc.equals("(I)V")) {
				MethodNode tempMethodNode = getMethodNode(guiScreen, mn.owner + " " + mn.name + " " + mn.desc);
				if (tempMethodNode != null) {
					boolean firstMatch = false;
					boolean secondMatch = false;
					for (AbstractInsnNode insn = tempMethodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
						if (!firstMatch && isLdcWithInteger(insn, 0xC0101010)) firstMatch = true;
						if (!secondMatch && isLdcWithInteger(insn, 0xD0101010)) secondMatch = true;
					}
					
					if (firstMatch && secondMatch) {
						addMethodMapping("net/minecraft/client/gui/GuiScreen drawDefaultBackground ()V", 
								guiScreen.name + " " + methods.get(0).name + " " + methods.get(0).desc);
						addMethodMapping("net/minecraft/client/gui/GuiScreen drawWorldBackground (I)V", 
								guiScreen.name + " " + tempMethodNode.name + " " + tempMethodNode.desc);
						drawWorldBackground = tempMethodNode;
					}
				}				
			}
		}
				
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/client/gui/GuiOptions",
			"net/minecraft/client/gui/GuiLanguage",
			"net/minecraft/client/gui/GuiSelectWorld",
			"net/minecraft/client/gui/GuiMultiplayer",
			"net/minecraft/client/gui/GuiYesNo"
			},
			dependsMethods="net/minecraft/client/gui/GuiScreen actionPerformed (Lnet/minecraft/client/gui/GuiButton;)V",
			depends={
			"net/minecraft/client/gui/GuiScreen",
			"net/minecraft/client/gui/GuiMainMenu"
			})
	public boolean discoverGuiClasses()
	{		
		ClassNode guiScreen = getClassNodeFromMapping("net/minecraft/client/gui/GuiScreen");
		ClassNode guiMainMenu = getClassNodeFromMapping("net/minecraft/client/gui/GuiMainMenu");
		if (guiScreen == null || guiMainMenu == null) return false;
		
		MethodNode actionPerformed = getMethodNodeFromMapping(guiMainMenu, "net/minecraft/client/gui/GuiScreen actionPerformed (Lnet/minecraft/client/gui/GuiButton;)V");
		if (actionPerformed == null) return false;
		
		ClassNode guiOptions = null;
		ClassNode guiLanguage = null;
		ClassNode guiSelectWorld = null;		
		ClassNode guiMultiplayer = null;
		ClassNode guiYesNo = null;
		
		List<TypeInsnNode> nodes = getAllInsnNodesOfType(actionPerformed.instructions.getFirst(), TypeInsnNode.class);
		for (TypeInsnNode node : nodes) {
			if (node.desc.startsWith("java")) continue;
			ClassNode cn = getClassNode(node.desc);
			if (cn == null) continue;
			if (!cn.superName.equals(guiScreen.name)) continue;
			
			if (guiOptions == null && searchConstantPoolForStrings(node.desc, "Options", "options.title", "options.video")) {
				addClassMapping("net/minecraft/client/gui/GuiOptions", node.desc);
				guiOptions = cn;
			}
			if (guiLanguage == null && searchConstantPoolForStrings(node.desc, "options.language", "options.languageWarning")) {
				addClassMapping("net/minecraft/client/gui/GuiLanguage", node.desc);
				guiLanguage = cn;
			}
			if (guiSelectWorld == null && searchConstantPoolForStrings(node.desc, "Select world", "selectWorld.title")) {
				addClassMapping("net/minecraft/client/gui/GuiSelectWorld", node.desc);
				guiSelectWorld = cn;
			}
			if (guiMultiplayer == null && searchConstantPoolForStrings(node.desc, "Unable to start LAN server detection: {}", "selectServer.edit")) {
				addClassMapping("net/minecraft/client/gui/GuiMultiplayer", node.desc);
				guiMultiplayer = cn;
			}
			if (guiYesNo == null && searchConstantPoolForStrings(node.desc, "gui.yes", "gui.no")) {
				addClassMapping("net/minecraft/client/gui/GuiYesNo", node.desc);
				guiYesNo = cn;
			}
		}		
		
		return true;
	}
	
	

	
	@Mapping(providesFields={
			"net/minecraft/client/gui/GuiButton id I",
			"net/minecraft/client/gui/GuiButton xPosition I",
			"net/minecraft/client/gui/GuiButton yPosition I",
			"net/minecraft/client/gui/GuiButton width I",
			"net/minecraft/client/gui/GuiButton height I",
			"net/minecraft/client/gui/GuiButton displayString Ljava/lang/String;"
			},
			depends={
			"net/minecraft/client/gui/GuiButton"
			})
	public boolean processGuiButtonClass()
	{
		ClassNode guiButton = getClassNodeFromMapping("net/minecraft/client/gui/GuiButton");
		if (guiButton == null) return false;
		
		// public GuiButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText)
		List<MethodNode> methods = getMatchingMethods(guiButton, "<init>", "(IIIIILjava/lang/String;)V");
		if (methods.size() == 1) {
			
			Map<Integer, String> fieldMap = new HashMap<>();
			
			for (AbstractInsnNode insn = getNextRealOpcode(methods.get(0).instructions.getFirst()); insn != null; insn = getNextRealOpcode(insn.getNext())) {
				@SuppressWarnings("unchecked")
				AbstractInsnNode[] nodes = getInsnNodeSequenceArray(insn, VarInsnNode.class, VarInsnNode.class, FieldInsnNode.class); 
				if (nodes == null) continue;				
				insn = nodes[2];
				
				FieldInsnNode fn = (FieldInsnNode)nodes[2];
				if (fn.owner.equals(guiButton.name)) fieldMap.put(((VarInsnNode)nodes[1]).var, fn.name);				
			}
			
			if (fieldMap.size() == 6) {
				for (int n = 1; n <= 6; n++) {
					switch (n) {
						case 1 : addFieldMapping("net/minecraft/client/gui/GuiButton id I", guiButton.name + " " + fieldMap.get(n) + " I");
								 break;
						case 2 : addFieldMapping("net/minecraft/client/gui/GuiButton xPosition I", guiButton.name + " " + fieldMap.get(n) + " I");
						 		break;
						case 3 : addFieldMapping("net/minecraft/client/gui/GuiButton yPosition I", guiButton.name + " " + fieldMap.get(n) + " I");
						 		break;
						case 4 : addFieldMapping("net/minecraft/client/gui/GuiButton width I", guiButton.name + " " + fieldMap.get(n) + " I");
						 		break;
						case 5 : addFieldMapping("net/minecraft/client/gui/GuiButton height I", guiButton.name + " " + fieldMap.get(n) + " I");
						 		break;
						case 6 : addFieldMapping("net/minecraft/client/gui/GuiButton displayString Ljava/lang/String;", guiButton.name + " " + fieldMap.get(n) + " Ljava/lang/String;");
						 		break;
					}
				}
			}
		}
		
		return true;
	}
	
	
	@Mapping(provides={			
			},
			providesFields={
			},
			providesMethods={
			"net/minecraft/client/gui/FontRenderer getStringWidth (Ljava/lang/String;)I",
			"net/minecraft/client/gui/Gui drawCenteredString (Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V",
			"net/minecraft/client/gui/Gui drawString (Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V"
			},
			depends={
			"net/minecraft/client/gui/Gui",
			"net/minecraft/client/gui/FontRenderer"
			})			
	public boolean processGuiMainMenuClass()
	{
		ClassNode gui = getClassNodeFromMapping("net/minecraft/client/gui/Gui");
		ClassNode fontRenderer = getClassNodeFromMapping("net/minecraft/client/gui/FontRenderer");
		if (!MeddleUtil.notNull(gui,fontRenderer)) return false;

		//TODO move to other methods because of the name

		List<MethodNode> methods = getMatchingMethods(gui, null, assembleDescriptor("(", fontRenderer, "Ljava/lang/String;III)V"));
		if(methods.size() == 2){
			//Gui drawCenteredString
			addMethodMapping("net/minecraft/client/gui/Gui drawCenteredString (Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V",
					gui.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			//Gui drawString
			addMethodMapping("net/minecraft/client/gui/Gui drawString (Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V",
					gui.name + " " + methods.get(1).name + " " + methods.get(1).desc);
		}

		methods = getMatchingMethods(fontRenderer, null, "(Ljava/lang/String;)I");
		if(methods.size() == 1){
			//FontRenderer getStringWidth
			addMethodMapping("net/minecraft/client/gui/FontRenderer getStringWidth (Ljava/lang/String;)I",
					fontRenderer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}

		return true;
	}
	
	
	
	@Mapping(
			providesMethods={
			"net/minecraft/block/state/BlockState getProperty (Ljava/lang/String;)Lnet/minecraft/block/properties/IProperty;"
			},
			depends={
			"net/minecraft/block/state/BlockState",
			"net/minecraft/block/properties/IProperty"
			})
	public boolean processBlockStateClass()
	{
		ClassNode blockState = getClassNodeFromMapping("net/minecraft/block/state/BlockState");
		ClassNode iProperty = getClassNodeFromMapping("net/minecraft/block/properties/IProperty");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		if (!MeddleUtil.notNull(blockState, iProperty, block)) return false;
		
		List<MethodNode> methods = new ArrayList<MethodNode>();
		
		
		// public IProperty getProperty(String param0)
		// Note: Not an MCP name
		methods = DynamicMappings.getMatchingMethods(blockState, null, "(Ljava/lang/String;)L" + iProperty.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/state/BlockState getProperty (Ljava/lang/String;)Lnet/minecraft/block/properties/IProperty;",
					blockState.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}

	
	@Mapping(provides={
			"net/minecraft/client/entity/EntityPlayerSP",
			"net/minecraft/client/entity/AbstractClientPlayer"
			},
			dependsFields={
			"net/minecraft/init/Sounds block_portal_trigger Lnet/minecraft/util/Sound;"
			},
			depends={
			"net/minecraft/client/Minecraft",
			"net/minecraft/entity/player/EntityPlayer"			
			})		
	public boolean getEntityPlayerSPClass()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		if (minecraft == null || entityPlayer == null) return false;		
		
		String entityPlayerSP_name = null;
		
		String portalSound = getSoundFieldFull("block.portal.trigger");
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(minecraft,  null, "(I)V");
		startloop:
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.PUTFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (!fn.owner.equals(minecraft.name) || fn.desc.contains("/")) continue;
				String className = fn.desc.substring(1, fn.desc.length() - 1);				
				if (DynamicMappings.searchConstantPoolForStrings(className, "minecraft:chest", "minecraft:anvil") &&
					searchConstantPoolForFields(className, portalSound)) {
					entityPlayerSP_name = className;
					break startloop;
				}
			}
		}
		
		if (entityPlayerSP_name == null) return false;		
		ClassNode entityPlayerSP = getClassNode(entityPlayerSP_name);
		if (entityPlayerSP == null) return false;
		
		// Confirm parents
		if (DynamicMappings.searchConstantPoolForStrings(entityPlayerSP.superName, "http://skins.minecraft.net/MinecraftSkins/%s.png")) {
			ClassNode abstractClientPlayer = getClassNode(entityPlayerSP.superName);
			if (abstractClientPlayer == null) return false;			
			if (!abstractClientPlayer.superName.equals(entityPlayer.name)) return false;
		}
		
		addClassMapping("net/minecraft/client/entity/EntityPlayerSP", entityPlayerSP);
		addClassMapping("net/minecraft/client/entity/AbstractClientPlayer", entityPlayerSP.superName);
		
		return true;
	}
	
	
	
	@Mapping(provides={			
			},
			depends={			
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/client/entity/EntityPlayerSP",
			"net/minecraft/world/IInteractionObject"
			})		
	public boolean processEntityPlayerSPClass()
	{		
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode entityPlayerSP = getClassNodeFromMapping("net/minecraft/client/entity/EntityPlayerSP");
		ClassNode iInteractionObject = getClassNodeFromMapping("net/minecraft/world/IInteractionObject");
		if (!MeddleUtil.notNull(entityPlayer, entityPlayerSP, iInteractionObject)) return false;
		
		// TODO		
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/client/gui/inventory/GuiChest",
			"net/minecraft/client/gui/inventory/GuiContainer"
			},
			dependsMethods={
			"net/minecraft/entity/player/EntityPlayer displayGUIChest (Lnet/minecraft/inventory/IInventory;)V"
			},
			depends={
			"net/minecraft/client/gui/GuiScreen",
			"net/minecraft/client/entity/EntityPlayerSP"
			})
	public boolean getGuiChestClass()
	{
		ClassNode guiScreen = getClassNodeFromMapping("net/minecraft/client/gui/GuiScreen");
		ClassNode entityPlayerSP = getClassNodeFromMapping("net/minecraft/client/entity/EntityPlayerSP");		
		if (!MeddleUtil.notNull(guiScreen, entityPlayerSP)) return false;
		
		MethodNode displayGuiChest = DynamicMappings.getMethodNodeFromMapping(entityPlayerSP, "net/minecraft/entity/player/EntityPlayer displayGUIChest (Lnet/minecraft/inventory/IInventory;)V");
		if (displayGuiChest == null) return false;
		
		Map<String, String> guiMap = new HashMap<>();
		
		String lastString = null;
		for (AbstractInsnNode insn = displayGuiChest.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			String s = DynamicMappings.getLdcString(insn);
			if (s != null) { lastString = s; continue; }
			
			if (insn.getOpcode() != Opcodes.NEW) continue;
			TypeInsnNode tn = (TypeInsnNode)insn;
			if (lastString != null) guiMap.put(lastString, tn.desc);			
		}
		
		String className = guiMap.get("minecraft:chest");
		if (className != null) {
			if (!DynamicMappings.searchConstantPoolForStrings(className, "textures/gui/container/generic_54.png")) return false;
			
			ClassNode guiChest = getClassNode(className);
			if (guiChest == null) return false;
			
			ClassNode guiContainer = getClassNode(guiChest.superName);
			if (!DynamicMappings.searchConstantPoolForStrings(guiContainer.name, "textures/gui/container/inventory.png")) return false;
			if (!guiContainer.superName.equals(guiScreen.name)) return false;
			
			addClassMapping("net/minecraft/client/gui/inventory/GuiChest", guiChest);
			addClassMapping("net/minecraft/client/gui/inventory/GuiContainer", guiContainer);			
		}
		
		return true;
	}
	
	
	
	
	@Mapping(provides={
			"net/minecraft/client/settings/GameSettings"
			},
			providesFields={
			"net/minecraft/client/Minecraft thePlayer Lnet/minecraft/client/entity/EntityPlayerSP;",
			"net/minecraft/client/gui/inventory/GuiContainer guiLeft I",
			"net/minecraft/client/gui/inventory/GuiContainer guiTop I"
			},
			providesMethods={
			"net/minecraft/client/gui/inventory/GuiContainer handleMouseClick (Lnet/minecraft/inventory/Slot;III)V",
			"net/minecraft/client/gui/inventory/GuiContainer checkHotbarKeys (I)Z",
			"net/minecraft/client/gui/inventory/GuiContainer drawItemStack (Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
			"net/minecraft/client/gui/inventory/GuiContainer drawGuiContainerForegroundLayer (II)V",
			"net/minecraft/client/gui/inventory/GuiContainer drawGuiContainerBackgroundLayer (FII)V",
			"net/minecraft/client/gui/inventory/GuiContainer drawSlot (Lnet/minecraft/inventory/Slot;)V",
			"net/minecraft/client/gui/inventory/GuiContainer getSlotAtPosition (II)Lnet/minecraft/inventory/Slot;",
			"net/minecraft/client/gui/GuiScreen mouseClickMove (IIIJ)V",
			"net/minecraft/client/gui/inventory/GuiContainer isMouseOverSlot (Lnet/minecraft/inventory/Slot;II)Z",
			"net/minecraft/client/gui/inventory/GuiContainer isPointInRegion (IIIIII)Z",
			"net/minecraft/client/gui/GuiScreen doesGuiPauseGame ()Z",
			"net/minecraft/client/gui/GuiScreen onGuiClosed ()V",
			"net/minecraft/client/gui/GuiScreen updateScreen ()V",
			"net/minecraft/client/gui/inventory/GuiContainer updateDragSplitting ()V"
			},
			dependsMethods={
			"net/minecraft/inventory/Container onContainerClosed (Lnet/minecraft/entity/player/EntityPlayer;)V",
			"net/minecraft/client/gui/GuiScreen initGui ()V"
			},
			depends={
			"net/minecraft/client/gui/inventory/GuiContainer",
			"net/minecraft/inventory/Slot",
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/entity/EntityPlayerSP",
			"net/minecraft/item/ItemStack",
			"net/minecraft/client/gui/GuiScreen",
			"net/minecraft/inventory/EnumContainerAction"
			})	
	public boolean processGuiContainerClass()
	{
		ClassNode guiContainer = getClassNodeFromMapping("net/minecraft/client/gui/inventory/GuiContainer");
		ClassNode slot = getClassNodeFromMapping("net/minecraft/inventory/Slot");
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode playerSP = getClassNodeFromMapping("net/minecraft/client/entity/EntityPlayerSP");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode guiScreen = getClassNodeFromMapping("net/minecraft/client/gui/GuiScreen");
		ClassNode containerAction = getClassNodeFromMapping("net/minecraft/inventory/EnumContainerAction");
		if (!MeddleUtil.notNull(guiContainer, slot, minecraft, playerSP, itemStack, guiScreen, containerAction)) return false;
		
		// protected void handleMouseClick(Slot slotIn, int slotId, int clickedButton, int clickType)
		// As of 15w44a: protected void handleMouseClick(Slot slotIn, int slotId, int clickedButton, EnumContainerAction clickType)
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(L" + slot.name + ";IIL" + containerAction.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer handleMouseClick (Lnet/minecraft/inventory/Slot;III)V",
					guiContainer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected boolean checkHotbarKeys(int keyCode)
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(I)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer checkHotbarKeys (I)Z",
					guiContainer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			// Find Minecraft.thePlayer and Minecraft.gameSettings
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.GETFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (!fn.owner.equals(minecraft.name)) continue;
				Type t = Type.getType(fn.desc);				
				if (t.getSort() != Type.OBJECT) continue;
				if (t.getClassName().equals(playerSP.name)) {
					addFieldMapping("net/minecraft/client/Minecraft thePlayer Lnet/minecraft/client/entity/EntityPlayerSP;",
							minecraft.name + " " + fn.name + " " + fn.desc);
				}
				else if (DynamicMappings.searchConstantPoolForStrings(t.getClassName(), "options.particles.all", "key.forward", "enableVsync:")) {					
					addClassMapping("net/minecraft/client/settings/GameSettings", t.getClassName());
					addFieldMapping("net/minecraft/client/Minecraft gameSettings Lnet/minecraft/client/entity/EntityPlayerSP;",
							minecraft.name + " " + fn.name + " " + fn.desc);					
				}
			}
		}
		
		
		// private void drawItemStack(ItemStack stack, int x, int y, String altText)
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(L" + itemStack.name + ";IILjava/lang/String;)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer drawItemStack (Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
					guiContainer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {}
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(II)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer drawGuiContainerForegroundLayer (II)V",
					guiContainer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}

		
	    // protected abstract void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY);
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(FII)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer drawGuiContainerBackgroundLayer (FII)V",
					guiContainer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// private void drawSlot(Slot slotIn)
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(L" + slot.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer drawSlot (Lnet/minecraft/inventory/Slot;)V",
					guiContainer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		// private Slot getSlotAtPosition(int x, int y)
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(II)L" + slot.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer getSlotAtPosition (II)Lnet/minecraft/inventory/Slot;",
					guiContainer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		// protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick)
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(IIIJ)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/GuiScreen mouseClickMove (IIIJ)V",
					guiScreen.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// private boolean isMouseOverSlot(Slot slotIn, int mouseX, int mouseY)
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(L" + slot.name + ";II)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer isMouseOverSlot (Lnet/minecraft/inventory/Slot;II)Z",
					guiContainer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// protected boolean isPointInRegion(int left, int top, int right, int bottom, int pointX, int pointY)
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(IIIIII)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer isPointInRegion (IIIIII)Z",
					guiContainer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			List<String> fields = new ArrayList<>();
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.GETFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.owner.equals(guiContainer.name) && fn.desc.equals("I")) fields.add(fn.name);				
			}
			
			if (fields.size() == 2) {
				addFieldMapping("net/minecraft/client/gui/inventory/GuiContainer guiLeft I", guiContainer.name + " " + fields.get(0) + " I");
				addFieldMapping("net/minecraft/client/gui/inventory/GuiContainer guiTop I", guiContainer.name + " " + fields.get(1) + " I");
			}
		}
		
		
		// public boolean doesGuiPauseGame()
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "()Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/GuiScreen doesGuiPauseGame ()Z",
					guiScreen.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		
		String onContainerClosed = DynamicMappings.getMethodMapping("net/minecraft/inventory/Container onContainerClosed (Lnet/minecraft/entity/player/EntityPlayer;)V");
		String initGui = DynamicMappings.getMethodMapping("net/minecraft/client/gui/GuiScreen initGui ()V");		
		
		// public void onGuiClosed()
		// public void updateScreen()
		methods = DynamicMappings.getMatchingMethods(guiContainer, null, "()V");
		if (methods.size() == 5 && onContainerClosed != null && initGui != null) 
		{
			MethodNode onGuiClosed = null;
			MethodNode updateScreen = null;
			MethodNode updateDragSplitting = null;
			
			for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
				MethodNode method = it.next();
				if (method.name.contains("<")) { it.remove(); continue; }
				if (initGui.endsWith(" " + method.name + " " + method.desc)) { it.remove(); continue; }
				
				for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) 
				{				
					if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
					MethodInsnNode mn = (MethodInsnNode)insn;
					if (onContainerClosed.equals(mn.owner + " " + mn.name + " " + mn.desc)) {
						onGuiClosed = method;
						it.remove();
						break;
					}
				}
			}
			if (methods.size() == 2) {
				for (MethodNode method : methods) {
					if ((method.access & Opcodes.ACC_PRIVATE) > 0) updateDragSplitting = method;
					else updateScreen = method;
				}
			}
			
			if (MeddleUtil.notNull(onGuiClosed, updateScreen, updateDragSplitting)) {
				addMethodMapping("net/minecraft/client/gui/GuiScreen onGuiClosed ()V",
						guiScreen.name + " " + onGuiClosed.name + " " + onGuiClosed.desc);
				addMethodMapping("net/minecraft/client/gui/GuiScreen updateScreen ()V",
						guiScreen.name + " " + updateScreen.name + " " + updateScreen.desc);
				addMethodMapping("net/minecraft/client/gui/inventory/GuiContainer updateDragSplitting ()V",
						guiContainer.name + " " + updateDragSplitting.name + " " + updateDragSplitting.desc);
			}
		}
		
		
		
		return true;
	}
	
	
	
	
	
	
	@Mapping(provides="net/minecraft/client/network/NetHandlerPlayClient",
			 depends="net/minecraft/client/entity/EntityPlayerSP")
	public boolean getNetHandlerPlayClientClass()
	{
		ClassNode playerSP = getClassNodeFromMapping("net/minecraft/client/entity/EntityPlayerSP");		
		if (playerSP == null) return false;
		
		for (FieldNode field : playerSP.fields) {
			Type t = Type.getType(field.desc);
			if (t.getSort() != Type.OBJECT) continue;
			if (DynamicMappings.searchConstantPoolForStrings(t.getClassName(), "MC|Brand", "disconnect.lost", "minecraft:container"))
			{
				addClassMapping("net/minecraft/client/network/NetHandlerPlayClient", t.getClassName());
				return true;
			}
		}
		
		return false;
	}
	
	
	@Mapping(provides={
			},
			providesMethods={
			"net/minecraft/client/network/NetHandlerPlayClient handleOpenWindow (Lnet/minecraft/network/play/server/S2DPacketOpenWindow;)V"
			},
			depends={
			"net/minecraft/client/network/NetHandlerPlayClient",
			"net/minecraft/network/play/server/S2DPacketOpenWindow"
			})
	public boolean processNetHandlerPlayClientClass()
	{
		ClassNode clientHandler = getClassNodeFromMapping("net/minecraft/client/network/NetHandlerPlayClient");
		ClassNode openWindow = getClassNodeFromMapping("net/minecraft/network/play/server/S2DPacketOpenWindow");
		if (!MeddleUtil.notNull(clientHandler, openWindow)) return false;
		
		// public void handleOpenWindow(S2DPacketOpenWindow packetIn)
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(clientHandler, null, "(L" + openWindow.name + ";)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/network/NetHandlerPlayClient handleOpenWindow (Lnet/minecraft/network/play/server/S2DPacketOpenWindow;)V",
					clientHandler.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
				
		return true;
	}
	
	@Mapping(providesFields={
			"net/minecraft/network/play/server/S2DPacketOpenWindow windowId I",
			"net/minecraft/network/play/server/S2DPacketOpenWindow inventoryType Ljava/lang/String",
			"net/minecraft/network/play/server/S2DPacketOpenWindow windowTitle Lnet/minecraft/util/IChatComponent;",
			"net/minecraft/network/play/server/S2DPacketOpenWindow slotCount I"
			},
			providesMethods={
			"net/minecraft/network/play/server/S2DPacketOpenWindow getGuiId ()Ljava/lang/String;",
			"net/minecraft/network/play/server/S2DPacketOpenWindow getWindowTitle ()Lnet/minecraft/util/IChatComponent;",
			"net/minecraft/network/play/server/S2DPacketOpenWindow getWindowId ()I",
			"net/minecraft/network/play/server/S2DPacketOpenWindow getSlotCount ()I",
			"net/minecraft/network/play/server/S2DPacketOpenWindow hasSlots ()Z"
			},
			depends={
			"net/minecraft/network/play/server/S2DPacketOpenWindow",
			"net/minecraft/util/IChatComponent"
			})
	public boolean processS2DPacketOpenWindowClass()
	{
		ClassNode packet = getClassNodeFromMapping("net/minecraft/network/play/server/S2DPacketOpenWindow");
		ClassNode iChatComponent = getClassNodeFromMapping("net/minecraft/util/IChatComponent");
		if (packet == null || iChatComponent == null) return false;
		
		String windowId = null;
		String inventoryType = null;
		String windowTitle = null;
		String slotCount = null;
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(packet, "<init>", "(ILjava/lang/String;L" + iChatComponent.name + ";I)V");
		if (methods.size() == 1) {			
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {				
				if (insn.getOpcode() != Opcodes.PUTFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;				
				if (!fn.owner.equals(packet.name)) continue;				
				if (windowId == null && fn.desc.equals("I")) { windowId = fn.name; continue; }
				if (windowId != null && inventoryType == null && fn.desc.equals("Ljava/lang/String;")) { inventoryType = fn.name; continue; }
				if (windowId != null && inventoryType != null && windowTitle == null && fn.desc.equals("L" + iChatComponent.name + ";")) { windowTitle = fn.name; continue; }
				if (windowId != null && inventoryType != null && windowTitle != null && slotCount == null && fn.desc.equals("I")) { slotCount = fn.name; continue; }
			}
		}

		if (MeddleUtil.notNull(windowId, inventoryType, windowTitle, slotCount)) {
			addFieldMapping("net/minecraft/network/play/server/S2DPacketOpenWindow windowId I",	packet.name + " " + windowId + " I");
			addFieldMapping("net/minecraft/network/play/server/S2DPacketOpenWindow inventoryType Ljava/lang/String", packet.name + " " + inventoryType + " Ljava/lang/String;");
			addFieldMapping("net/minecraft/network/play/server/S2DPacketOpenWindow windowTitle Lnet/minecraft/util/IChatComponent;", packet.name + " " + windowTitle + " L" + iChatComponent.name + ";");
			addFieldMapping("net/minecraft/network/play/server/S2DPacketOpenWindow slotCount I",	packet.name + " " + slotCount + " I");
		}
		
		// public String getGuiId()
		methods = DynamicMappings.getMatchingMethods(packet, null, "()Ljava/lang/String;");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/network/play/server/S2DPacketOpenWindow getGuiId ()Ljava/lang/String;",
					packet.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public IChatComponent getWindowTitle()
		methods = DynamicMappings.getMatchingMethods(packet, null, "()L" + iChatComponent.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/network/play/server/S2DPacketOpenWindow getWindowTitle ()Lnet/minecraft/util/IChatComponent;",
					packet.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public int getWindowId() - windowId
		// public int getSlotCount() - slotCount
		// public int getEntityId() - TODO
		methods = DynamicMappings.getMatchingMethods(packet, null, "()I");
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {				
				if (insn.getOpcode() != Opcodes.GETFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (!fn.owner.equals(packet.name)) continue;
				
				if (fn.name.equals(windowId)) {
					addMethodMapping("net/minecraft/network/play/server/S2DPacketOpenWindow getWindowId ()I",
							packet.name + " " + method.name + " " + method.desc);
					break;
				}
				
				if (fn.name.equals(slotCount)) {
					addMethodMapping("net/minecraft/network/play/server/S2DPacketOpenWindow getSlotCount ()I",
							packet.name + " " + method.name + " " + method.desc);
					break;
				}				
			}			
		}
		
		
		// public boolean hasSlots()
		methods = DynamicMappings.getMatchingMethods(packet, null, "()Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/network/play/server/S2DPacketOpenWindow hasSlots ()Z",
					packet.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		return true;
	}
	
	
	
	public static class TallyKeeper<T>
	{
		private Map<T, Integer> counts = new HashMap<>();
		
		public void put(T t)
		{
			Integer count = counts.get(t);
			if (count == null) count = new Integer(0);
			count++;
			counts.put(t, count);
		}
		
		public T getHighestObj()
		{
			int maxCount = -1;
			T maxKey = null;
			for (T key : counts.keySet()) {
				int count = counts.get(key);
				if (count > maxCount) { maxCount = count; maxKey = key; }
			}
			return maxKey;
		}
		
		public int getHighestCount()
		{
			int maxCount = -1;
			for (T key : counts.keySet()) {
				int count = counts.get(key);
				if (count > maxCount) maxCount = count;
			}
			return maxCount;
		}
	}
	
	
	@Mapping(provides={
			"net/minecraft/client/settings/KeyBinding"
			},
			depends={
			"net/minecraft/client/settings/GameSettings",
			"net/minecraft/client/Minecraft"
			})
	public boolean getKeybindingClass()
	{
		ClassNode gameSettings = getClassNodeFromMapping("net/minecraft/client/settings/GameSettings");
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		if (gameSettings == null || minecraft == null) return false;
		
		// Bit of a roundabout way to locate and confirm KeyBinding
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(gameSettings, "<init>", "(L" + minecraft.name + ";Ljava/io/File;)V");
		if (methods.size() == 1) 
		{
			TallyKeeper<String> tallies = new TallyKeeper<>();
			
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.NEW) continue;
				TypeInsnNode tn = (TypeInsnNode)insn;
				tallies.put(tn.desc);
			}
			
			String keyBinding_name = tallies.getHighestObj();
			ClassNode keyBinding = getClassNode(keyBinding_name);
			
			boolean comparable = false;
			for (String iface : keyBinding.interfaces) {
				if (iface.equals("java/lang/Comparable")) comparable = true;
			}
			if (comparable) {
				addClassMapping("net/minecraft/client/settings/KeyBinding", keyBinding_name);
				return true;
			}
		}		
		
		return false;
	}
	
	
	@Mapping(providesFields={
			"net/minecraft/client/settings/KeyBinding keyDescription Ljava/lang/String;",
			"net/minecraft/client/settings/KeyBinding keyCode I",
			"net/minecraft/client/settings/KeyBinding keyCodeDefault I",
			"net/minecraft/client/settings/KeyBinding keyCategory Ljava/lang/String;",
			"net/minecraft/client/settings/KeyBinding pressed Z",
			"net/minecraft/client/settings/KeyBinding pressTime I"
			},
			providesMethods={
			"net/minecraft/client/settings/KeyBinding onTick (I)V",
			"net/minecraft/client/settings/KeyBinding setKeyBindState (IZ)V",
			"net/minecraft/client/settings/KeyBinding getKeybinds ()Ljava/util/Set;",
			"net/minecraft/client/settings/KeyBinding unpressKey ()V",
			"net/minecraft/client/settings/KeyBinding unPressAllKeys ()V",
			"net/minecraft/client/settings/KeyBinding resetKeyBindingArrayAndHash ()V",
			"net/minecraft/client/settings/KeyBinding setKeyCode (I)V",
			"net/minecraft/client/settings/KeyBinding isKeyDown ()Z",
			"net/minecraft/client/settings/KeyBinding isPressed ()Z",
			"net/minecraft/client/settings/KeyBinding updateAllKeys ()V"
			},
			depends={
			"net/minecraft/client/settings/KeyBinding"
			})
	public boolean processKeybindingClass()
	{
		ClassNode keyBinding = getClassNodeFromMapping("net/minecraft/client/settings/KeyBinding");
		if (keyBinding == null) return false;
		
		String keyDescription = null;
		String keyCode = null;
		String keyCodeDefault = null;
		String keyCategory = null;
		
		// public KeyBinding(String description, int keyCode, String category)
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(keyBinding, "<init>", "(Ljava/lang/String;ILjava/lang/String;)V");
		if (methods.size() == 1) {
			List<FieldInsnNode> fields = new ArrayList<>();
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.PUTFIELD) continue;
				fields.add((FieldInsnNode)insn);
			}
			if (fields.size() == 4) {
				if (fields.get(0).desc.equals("Ljava/lang/String;")) {
					keyDescription = fields.get(0).name;
					addFieldMapping("net/minecraft/client/settings/KeyBinding keyDescription Ljava/lang/String;",
							keyBinding.name + " " + fields.get(0).name + " Ljava/lang/String;");
				}
				if (fields.get(1).desc.equals("I") && fields.get(2).desc.equals("I")) {
					keyCode = fields.get(1).name;
					keyCodeDefault = fields.get(2).name;
					addFieldMapping("net/minecraft/client/settings/KeyBinding keyCode I",
							keyBinding.name + " " + fields.get(1).name + " I");
					addFieldMapping("net/minecraft/client/settings/KeyBinding keyCodeDefault I",
							keyBinding.name + " " + fields.get(2).name + " I");
				}
				if (fields.get(3).desc.equals("Ljava/lang/String;")) {
					keyCategory = fields.get(3).name;
					addFieldMapping("net/minecraft/client/settings/KeyBinding keyCategory Ljava/lang/String;",
							keyBinding.name + " " + fields.get(3).name + " Ljava/lang/String;");
				}
			}
		}	
				
		
		// public static void onTick(int keyCode)
		methods = DynamicMappings.getMatchingMethods(keyBinding, null, "(I)V");
		methods = DynamicMappings.removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/settings/KeyBinding onTick (I)V",
					keyBinding.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public static void setKeyBindState(int keyCode, boolean pressed)
		methods = DynamicMappings.getMatchingMethods(keyBinding, null, "(IZ)V");
		methods = DynamicMappings.removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/settings/KeyBinding setKeyBindState (IZ)V",
					keyBinding.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// public static Set getKeybinds()	
		methods = DynamicMappings.getMatchingMethods(keyBinding, null, "()Ljava/util/Set;");
		methods = DynamicMappings.removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/settings/KeyBinding getKeybinds ()Ljava/util/Set;",
					keyBinding.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// private void unpressKey()
		String unpressKey = null;
		methods = DynamicMappings.getMatchingMethods(keyBinding, null, "()V");
		methods = DynamicMappings.removeMethodsWithFlags(methods, Opcodes.ACC_STATIC);
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			if (it.next().name.contains("<")) it.remove();
		}
		if (methods.size() == 1) {
			unpressKey = methods.get(0).name;
			addMethodMapping("net/minecraft/client/settings/KeyBinding unpressKey ()V",
					keyBinding.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			String pressTime = null;
			int count = 0;
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.PUTFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (fn.owner.equals(keyBinding.name) && fn.desc.equals("I")) {
					count++;
					pressTime = fn.name;
				}
			}
			
			if (count == 1 && pressTime != null) {
				addFieldMapping("net/minecraft/client/settings/KeyBinding pressTime I",
						keyBinding.name + " " + pressTime + " I");
			}
		}
		
		
		// public static void updateAllKeys() - NEW in 15w37a
		// public static void unPressAllKeys()
		// public static void resetKeyBindingArrayAndHash()		
		methods = DynamicMappings.getMatchingMethods(keyBinding, null, "()V");
		methods = DynamicMappings.removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			if (it.next().name.contains("<")) it.remove();
		}		
		if (methods.size() == 3 && unpressKey != null) 
		{
			MethodNode updateAllKeys = null;
			MethodNode unPressAllKeys = null;
			MethodNode resetKeyBindingArrayAndHash = null;
			
			outerloop:
			for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
				MethodNode method = it.next();
				List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(),  MethodInsnNode.class);
				for (MethodInsnNode mn : list) {
					if (mn.owner.equals("org/lwjgl/input/Keyboard") && mn.name.equals("isKeyDown")) {
						addMethodMapping("net/minecraft/client/settings/KeyBinding updateAllKeys ()V",
								keyBinding.name + " " + method.name + " " + method.desc);
						it.remove();
						break outerloop;
					}
				}
			}
			
			if (methods.size() == 2) {
				for (MethodNode method : methods) {
					boolean is_unpressAllKeys = false;
					for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
						if (insn.getOpcode() != Opcodes.INVOKESPECIAL) continue;
						MethodInsnNode mn = (MethodInsnNode)insn;
						if (mn.owner.equals(keyBinding.name) && mn.name.equals(unpressKey) && mn.desc.equals("()V")) {
							unPressAllKeys = method;
							is_unpressAllKeys = true;
							break;
						}
					}
					if (!is_unpressAllKeys) resetKeyBindingArrayAndHash = method;
				}
			}
			
			if (unPressAllKeys != null && resetKeyBindingArrayAndHash != null) {		
				addMethodMapping("net/minecraft/client/settings/KeyBinding unPressAllKeys ()V",
						keyBinding.name + " " + unPressAllKeys.name + " " + unPressAllKeys.desc);
				addMethodMapping("net/minecraft/client/settings/KeyBinding resetKeyBindingArrayAndHash ()V",
						keyBinding.name + " " + resetKeyBindingArrayAndHash.name + " " + resetKeyBindingArrayAndHash.desc);
			}
		}
		
		
		// public void setKeyCode(int keyCode)
		methods = DynamicMappings.getMatchingMethods(keyBinding, null, "(I)V");
		methods = DynamicMappings.removeMethodsWithFlags(methods, Opcodes.ACC_STATIC);
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/settings/KeyBinding setKeyCode (I)V",
					keyBinding.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public boolean isKeyDown()
		// public boolean isPressed()
		methods = DynamicMappings.getMatchingMethods(keyBinding, null, "()Z");
		if (methods.size() == 2) {
			boolean isGetter0 = DynamicMappings.matchOpcodeSequence(methods.get(0).instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.IRETURN);
			boolean isGetter1 = DynamicMappings.matchOpcodeSequence(methods.get(1).instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.IRETURN);			
			MethodNode isKeyDown = null;
			MethodNode isPressed = null;
			if (isGetter0 && !isGetter1) { isKeyDown = methods.get(0); isPressed = methods.get(1); }
			if (!isGetter0 && isGetter1) { isKeyDown = methods.get(1); isPressed = methods.get(0); }
			
			if (isKeyDown != null && isPressed != null) {
				addMethodMapping("net/minecraft/client/settings/KeyBinding isKeyDown ()Z",
						keyBinding.name + " " + isKeyDown.name + " " + isKeyDown.desc);
				addMethodMapping("net/minecraft/client/settings/KeyBinding isPressed ()Z",
						keyBinding.name + " " + isPressed.name + " " + isPressed.desc);
				
				for (AbstractInsnNode insn = isKeyDown.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() != Opcodes.GETFIELD) continue;
					FieldInsnNode fn = (FieldInsnNode)insn;
					if (fn.owner.equals(keyBinding.name) && fn.desc.equals("Z")) {
						addFieldMapping("net/minecraft/client/settings/KeyBinding pressed Z",
								keyBinding.name + " " + fn.name + " " + fn.desc);
					}
				}
			}
		}
		
		
		// TODO
		
		// public String getKeyCategory()
		// public String getKeyDescription()	
		
		// public int getKeyCodeDefault()
		// public int getKeyCode()	
		
		// public int compareTo(KeyBinding p_compareTo_1_)

		
		return true;
	}

	
	@Mapping(provides={
			"net/minecraft/client/renderer/RenderHelper"
			},
			dependsMethods={
			"net/minecraft/client/gui/GuiScreen drawScreen (IIF)V"
			},
			depends={
			"net/minecraft/client/gui/inventory/GuiContainer",
			"net/minecraft/util/Vec3"
			})
	public boolean getRenderHelperClass()
	{
		ClassNode guiContainer = getClassNodeFromMapping("net/minecraft/client/gui/inventory/GuiContainer");
		ClassNode vec3 = getClassNodeFromMapping("net/minecraft/util/Vec3");
		if (guiContainer == null || vec3 == null) return false;
		
		MethodNode drawScreen = DynamicMappings.getMethodNodeFromMapping(guiContainer, "net/minecraft/client/gui/GuiScreen drawScreen (IIF)V");
		if (drawScreen == null) return false;
		
		String renderHelper = null;
		int count = 0;
		
		for (AbstractInsnNode insn = drawScreen.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.INVOKESTATIC) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;
			if (DynamicMappings.searchConstantPoolForClasses(mn.owner, vec3.name, "java/nio/FloatBuffer"))
			{
				if (!mn.owner.equals(renderHelper)) {
					renderHelper = mn.owner;
					count++;
				}
			}				
		}
		
		if (count == 1) {
			addClassMapping("net/minecraft/client/renderer/RenderHelper", renderHelper);
		}
		
		return true;
	}
	
	
	@Mapping(providesMethods={
			"net/minecraft/client/renderer/RenderHelper enableStandardItemLighting ()V",
			"net/minecraft/client/renderer/RenderHelper enableGUIStandardItemLighting ()V",
			"net/minecraft/client/renderer/RenderHelper disableStandardItemLighting ()V"
			},
			depends={
			"net/minecraft/client/renderer/RenderHelper"
			})
	public boolean processRenderHelperClass()
	{
		ClassNode renderHelper = getClassNodeFromMapping("net/minecraft/client/renderer/RenderHelper");		
		if (renderHelper == null) return false;
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(renderHelper, null, "()V");
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			MethodNode method = it.next();
			if ((method.access & Opcodes.ACC_STATIC) == 0) it.remove();
			if ((method.access & Opcodes.ACC_PRIVATE) > 0) it.remove();
			if (method.name.contains("<")) it.remove();
		}
		
		if (methods.size() != 3) return false;

		MethodNode enableStandardItemLighting = null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.INVOKESTATIC) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;				
				// Changed in snapshot prior to 16w02a
				//if (!mn.owner.equals("org/lwjgl/opengl/GL11") || !mn.name.equals("glLight")) continue;
				if (!mn.desc.equals("(IILjava/nio/FloatBuffer;)V")) continue;
				if (enableStandardItemLighting != null) return false;
				enableStandardItemLighting = method; 
				break;
			}
		}
		if (enableStandardItemLighting == null) return false;
		
		addMethodMapping("net/minecraft/client/renderer/RenderHelper enableStandardItemLighting ()V",
				renderHelper.name + " " + enableStandardItemLighting.name + " ()V");
		methods.remove(enableStandardItemLighting);
		
		
		
		MethodNode enableGUIStandardItemLighting = null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.INVOKESTATIC) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (!mn.owner.equals(renderHelper.name) || !mn.name.equals(enableStandardItemLighting.name)) continue;
				if (enableGUIStandardItemLighting != null) return false;
				enableGUIStandardItemLighting = method; 
				break;
			}
		}
		if (enableGUIStandardItemLighting == null) return false;
		
		addMethodMapping("net/minecraft/client/renderer/RenderHelper enableGUIStandardItemLighting ()V",
				renderHelper.name + " " + enableGUIStandardItemLighting.name + " ()V");
		methods.remove(enableGUIStandardItemLighting);
		
		
		addMethodMapping("net/minecraft/client/renderer/RenderHelper disableStandardItemLighting ()V",
				renderHelper.name + " " + methods.get(0).name + " ()V");	
		
		
		return true;
	}	
	
	
	
	@Mapping(providesMethods={
			/*"net/minecraft/block/Block getRenderType ()I"*/
			},
			depends={
			"net/minecraft/block/BlockLiquid",
			"net/minecraft/block/Block"
			})
	public boolean processBlockDynamicLiquidClass()
	{
		ClassNode liquid = getClassNodeFromMapping("net/minecraft/block/BlockLiquid");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		if (!MeddleUtil.notNull(liquid, block)) return false;
		
		
		// TODO - This changed in 15w37a, stopped being needed for MeddleAPI
		// public int getRenderType()
		/*
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(liquid, null, "()I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getRenderType ()I", block.name + " " + methods.get(0).name + " ()I");
		}*/
		
		
		return true;
	}
	
	
	
	@Mapping(provides={
			"net/minecraft/client/renderer/WorldRenderer",
			"net/minecraft/client/renderer/BlockModelShapes"
			},
			providesFields={
			"net/minecraft/client/renderer/BlockRendererDispatcher blockModelShapes Lnet/minecraft/client/renderer/BlockModelShapes;"
			},
			providesMethods={
			"net/minecraft/client/renderer/BlockRendererDispatcher renderBlock (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/WorldRenderer;)Z",
			"net/minecraft/client/renderer/BlockRendererDispatcher getBlockModelShapes ()Lnet/minecraft/client/renderer/BlockModelShapes;"
			},
			depends={
			"net/minecraft/client/renderer/BlockRendererDispatcher",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/util/BlockPos",
			"net/minecraft/world/IBlockAccess"
			})
	public boolean processBlockRendererDispatcherClass()
	{
		ClassNode blockRenderer = getClassNodeFromMapping("net/minecraft/client/renderer/BlockRendererDispatcher");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode iBlockAccess = getClassNodeFromMapping("net/minecraft/world/IBlockAccess");		
		if (!MeddleUtil.notNull(blockRenderer, iBlockState, blockPos, iBlockAccess)) return false;
		
		String blockModelShapes_name = null;
		
		for (FieldNode field : blockRenderer.fields) {
			Type t = Type.getType(field.desc);
			if (t.getSort() != Type.OBJECT) continue;			
			String className = t.getClassName();
			
			if (blockModelShapes_name == null && searchConstantPoolForStrings(className, "minecraft:blocks/planks_oak", "minecraft:items/barrier")) {
				addClassMapping("net/minecraft/client/renderer/BlockModelShapes", className);
				blockModelShapes_name = className;
				
				if (countFieldsWithDesc(blockRenderer, "L" + blockModelShapes_name + ";") == 1) {
					addFieldMapping("net/minecraft/client/renderer/BlockRendererDispatcher blockModelShapes Lnet/minecraft/client/renderer/BlockModelShapes;",
							blockRenderer.name + " " + field.name + " " + field.desc);
				}
			}
		}
		
		
		// public BlockModelShapes getBlockModelShapes()
		List<MethodNode> methods = getMatchingMethods(blockRenderer, null, "()L" + blockModelShapes_name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/renderer/BlockRendererDispatcher getBlockModelShapes ()Lnet/minecraft/client/renderer/BlockModelShapes;",
					blockRenderer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		
		String worldRenderer_name = null;
		
		// public boolean renderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, WorldRenderer worldRendererIn)
		String desc_front = DynamicMappings.assembleDescriptor("(", iBlockState, blockPos, iBlockAccess);
		methods.clear();
		for (MethodNode method : blockRenderer.methods) {
			if (method.desc.startsWith(desc_front) && method.desc.endsWith(";)Z")) {
				methods.add(method);
			}
		}
		if (methods.size() == 1) {
			Type t = Type.getMethodType(methods.get(0).desc);
			Type[] args = t.getArgumentTypes();
			
			worldRenderer_name = args[3].getClassName();
			if (DynamicMappings.searchConstantPoolForStrings(worldRenderer_name, "Already building!", "Not building!")) {
				addClassMapping("net/minecraft/client/renderer/WorldRenderer", worldRenderer_name);
				
				addMethodMapping("net/minecraft/client/renderer/BlockRendererDispatcher renderBlock (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/WorldRenderer;)Z",
						blockRenderer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
			else worldRenderer_name = null;
		}
		
		
		return true;
	}
	
	
	
	@Mapping(provides={			
			"net/minecraft/client/renderer/vertex/VertexFormat"
			},
			providesMethods={
			"net/minecraft/client/renderer/WorldRenderer startDrawing (ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
			"net/minecraft/client/renderer/WorldRenderer finishDrawing ()V"
			},
			depends={
			"net/minecraft/client/renderer/WorldRenderer"
			})
	public boolean processWorldRendererClass()
	{
		ClassNode worldRenderer = getClassNodeFromMapping("net/minecraft/client/renderer/WorldRenderer");
		if (!MeddleUtil.notNull(worldRenderer)) return false;
		
		List<MethodNode> methods = new ArrayList<>();
		
		String vertexFormat_name = null;
		
		// public void startDrawing(int mode, VertexFormat format)		
		for (MethodNode method : worldRenderer.methods) {
			Type t = Type.getMethodType(method.desc);
			if (t.getReturnType().getSort() != Type.VOID) continue;
			Type[] args = t.getArgumentTypes();
			if (args.length != 2) continue;
			if (args[0].getSort() != Type.INT || args[1].getSort() != Type.OBJECT) continue;
			methods.add(method);
			vertexFormat_name = args[1].getClassName();
		}
		if (methods.size() == 1 && DynamicMappings.searchConstantPoolForStrings(vertexFormat_name, "VertexFormat error: Trying to add a position VertexFormatElement when one already exists, ignoring.")) {
			addClassMapping("net/minecraft/client/renderer/vertex/VertexFormat", vertexFormat_name);
			addMethodMapping("net/minecraft/client/renderer/WorldRenderer startDrawing (ILnet/minecraft/client/renderer/vertex/VertexFormat;)V", 
					worldRenderer.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		else vertexFormat_name = null;
		
		// public void finishDrawing()
		methods = DynamicMappings.getMatchingMethods(worldRenderer, null, "()V");
		int count = 0;
		String finishDrawing = null;
		
		for (MethodNode method : methods) {			
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (DynamicMappings.isLdcWithString(insn, "Not building!")) {
					finishDrawing = method.name;
					count++;
				}
			}
		}
		
		if (count == 1 && finishDrawing != null) {
			addMethodMapping("net/minecraft/client/renderer/WorldRenderer finishDrawing ()V", worldRenderer.name + " " + finishDrawing + " ()V");
		}
		
		
		return true;
	}
	
	
	@Mapping(providesMethods={
			"net/minecraft/client/renderer/WorldRenderer sortQuads (FFF)V",
			"net/minecraft/client/renderer/WorldRenderer putColor4 (I)V",
			"net/minecraft/client/renderer/WorldRenderer getByteBuffer ()Ljava/nio/ByteBuffer;",
			"net/minecraft/client/renderer/WorldRenderer postNormal (FFF)V",
			"net/minecraft/client/renderer/WorldRenderer getDrawMode ()I",
			"net/minecraft/client/renderer/WorldRenderer setTranslation (DDD)V",
			"net/minecraft/client/renderer/WorldRenderer endVertex ()V",
			"net/minecraft/client/renderer/WorldRenderer setNormal (FFF)Lnet/minecraft/client/renderer/WorldRenderer;",
			"net/minecraft/client/renderer/WorldRenderer setColorRGBA (IIII)Lnet/minecraft/client/renderer/WorldRenderer;",
			"net/minecraft/client/renderer/WorldRenderer setColorRGBA_F (FFFF)Lnet/minecraft/client/renderer/WorldRenderer;",
			"net/minecraft/client/renderer/WorldRenderer setBrightness (II)Lnet/minecraft/client/renderer/WorldRenderer;",
			"net/minecraft/client/renderer/WorldRenderer setTextureUV (DD)Lnet/minecraft/client/renderer/WorldRenderer;",
			"net/minecraft/client/renderer/WorldRenderer addVertex (DDD)Lnet/minecraft/client/renderer/WorldRenderer;",
			"net/minecraft/client/renderer/WorldRenderer putBrightness4 (IIII)V",
			"net/minecraft/client/renderer/WorldRenderer putPosition (DDD)V",
			"net/minecraft/client/renderer/WorldRenderer putColorRGB_F (FFFI)V",
			"net/minecraft/client/renderer/WorldRenderer putColorMultiplier (FFFI)V",
			"net/minecraft/client/renderer/WorldRenderer putBulkData ([I)V",
			"net/minecraft/client/renderer/WorldRenderer getVertexCount ()I",
			"net/minecraft/client/renderer/WorldRenderer reset ()V",
			"net/minecraft/client/renderer/WorldRenderer putColorRGB_F4 (FFF)V",
			"net/minecraft/client/renderer/WorldRenderer markDirty ()V"
			},
			depends={
			"net/minecraft/client/renderer/WorldRenderer",
			"net/minecraft/client/renderer/vertex/VertexFormat"
			})
	public boolean processRealmsBufferBuilderClass()
	{
		ClassNode bufferBuilder = getClassNode("net/minecraft/realms/RealmsBufferBuilder");
		ClassNode worldRenderer = getClassNodeFromMapping("net/minecraft/client/renderer/WorldRenderer");
		ClassNode vertexFormat = getClassNodeFromMapping("net/minecraft/client/renderer/vertex/VertexFormat");
		if (!MeddleUtil.notNull(bufferBuilder, worldRenderer, vertexFormat)) return false;	
		
		MethodNode method = null;
		
		// public void sortQuads(float param0, float param1, float param2)
		method = getMethodNode(bufferBuilder, "- sortQuads (FFF)V");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(FFF)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer sortQuads (FFF)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}		
		
		//  public void putColor4(int param0)
		method = getMethodNode(bufferBuilder, "- fixupQuadColor (I)V");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(I)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer putColor4 (I)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}		
		
		// public ByteBuffer getByteBuffer() 
		method = getMethodNode(bufferBuilder, "- getBuffer ()Ljava/nio/ByteBuffer;");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("()Ljava/nio/ByteBuffer;")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer getByteBuffer ()Ljava/nio/ByteBuffer;", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}		
		
		// public void postNormal(float param0, float param1, float param2)
		method = getMethodNode(bufferBuilder, "- postNormal (FFF)V");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(FFF)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer postNormal (FFF)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}		
		
		// public int getDrawMode()
		method = getMethodNode(bufferBuilder, "- getDrawMode ()I");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("()I")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer getDrawMode ()I", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}		
		
		// public void setTranslation(double param0, double param1, double param2)
		method = getMethodNode(bufferBuilder, "- offset (DDD)V");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(DDD)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer setTranslation (DDD)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public void endVertex()
		method = getMethodNode(bufferBuilder, "- endVertex ()V"); 
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("()V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer endVertex ()V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public WorldRenderer setNormal(float param0, float param1, float param2)
		method = getMethodNode(bufferBuilder, "- normal (FFF)Lnet/minecraft/realms/RealmsBufferBuilder;");	
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(FFF)L" + worldRenderer.name + ";")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer setNormal (FFF)Lnet/minecraft/client/renderer/WorldRenderer;", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public WorldRenderer setColorRGBA(int param0, int param1, int param2, int param3)	
		method = getMethodNode(bufferBuilder, "- color (IIII)Lnet/minecraft/realms/RealmsBufferBuilder;");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(IIII)L" + worldRenderer.name + ";")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer setColorRGBA (IIII)Lnet/minecraft/client/renderer/WorldRenderer;", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public WorldRenderer setColorRGBA_F(float param0, float param1, float param2, float param3)
		method = getMethodNode(bufferBuilder, "- color (FFFF)Lnet/minecraft/realms/RealmsBufferBuilder;");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(FFFF)L" + worldRenderer.name + ";")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer setColorRGBA_F (FFFF)Lnet/minecraft/client/renderer/WorldRenderer;", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public WorldRenderer setBrightness(int param0, int param1)
		method = getMethodNode(bufferBuilder, "- tex2 (II)Lnet/minecraft/realms/RealmsBufferBuilder;");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(II)L" + worldRenderer.name + ";")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer setBrightness (II)Lnet/minecraft/client/renderer/WorldRenderer;", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		//  public WorldRenderer setTextureUV(double param0, double param1)
		method = getMethodNode(bufferBuilder, "- tex (DD)Lnet/minecraft/realms/RealmsBufferBuilder;");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(DD)L" + worldRenderer.name + ";")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer setTextureUV (DD)Lnet/minecraft/client/renderer/WorldRenderer;", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public WorldRenderer addVertex(double param0, double param1, double param2)
		method = getMethodNode(bufferBuilder, "- vertex (DDD)Lnet/minecraft/realms/RealmsBufferBuilder;");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(DDD)L" + worldRenderer.name + ";")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer addVertex (DDD)Lnet/minecraft/client/renderer/WorldRenderer;", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public void putBrightness4(int param0, int param1, int param2, int param3)		
		method = getMethodNode(bufferBuilder, "- faceTex2 (IIII)V");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(IIII)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer putBrightness4 (IIII)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public void putPosition(double param0, double param1, double param2)
		method = getMethodNode(bufferBuilder, "- postProcessFacePosition (DDD)V");		
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(DDD)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer putPosition (DDD)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public void putColorRGB_F(float param0, float param1, float param2, int param3)
		method = getMethodNode(bufferBuilder, "- fixupVertexColor (FFFI)V");	
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(FFFI)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer putColorRGB_F (FFFI)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public void putColorMultiplier(float param0, float param1, float param2, int param3)
		method = getMethodNode(bufferBuilder, "- faceTint (FFFI)V");	
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(FFFI)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer putColorMultiplier (FFFI)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		//  public void putBulkData(int[] param0)
		method = getMethodNode(bufferBuilder, "- putBulkData ([I)V");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("([I)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer putBulkData ([I)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public int getVertexCount()
		method = getMethodNode(bufferBuilder, "- getVertexCount ()I");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("()I")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer getVertexCount ()I", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public void reset()
		method = getMethodNode(bufferBuilder, "- clear ()V");	
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("()V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer reset ()V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public void putColorRGB_F4(float param0, float param1, float param2)
		method = getMethodNode(bufferBuilder, "- fixupQuadColor (FFF)V");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("(FFF)V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer putColorRGB_F4 (FFF)V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// public void markDirty()
		method = getMethodNode(bufferBuilder, "- noColor ()V");
		if (method != null) {
			List<MethodInsnNode> list = DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : list) {
				if (mn.owner.equals(worldRenderer.name) && mn.desc.equals("()V")) {
					addMethodMapping("net/minecraft/client/renderer/WorldRenderer markDirty ()V", worldRenderer.name + " " + mn.name + " " + mn.desc);
					break;
				}
			}
		}	
		
		// TODO
		// restoreState (WorldRenderer.State param0)V
		// public RealmsVertexFormat getVertexFormat()
		 
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/util/EnumWorldBlockLayer"
			},
			providesMethods={
			"net/minecraft/client/renderer/RenderGlobal renderBlockLayer (Lnet/minecraft/util/EnumWorldBlockLayer;)V"
			},
			depends={
			"net/minecraft/client/renderer/RenderGlobal",
			"net/minecraft/client/renderer/vertex/VertexFormat"
			})
	public boolean processRenderGlobalClass()
	{
		ClassNode renderGlobal = getClassNodeFromMapping("net/minecraft/client/renderer/RenderGlobal");
		ClassNode vertexFormat = getClassNodeFromMapping("net/minecraft/client/renderer/vertex/VertexFormat");
		if (!MeddleUtil.notNull(renderGlobal, vertexFormat)) return false;		
		
		String renderBlockLayer_name = null;
		String enumWorldBlockLayer_name = null;
		MethodNode renderBlockLayer = null;
		
		// private void renderBlockLayer(EnumWorldBlockLayer param0)
		// net/minecraft/util/EnumWorldBlockLayer
		Set<String> matches = new HashSet<>();
		for (MethodNode method : renderGlobal.methods) {
			Type t = Type.getMethodType(method.desc);
			if (t.getReturnType().getSort() != Type.VOID) continue;
			Type[] args = t.getArgumentTypes();
			if (args.length != 1 || args[0].getSort() != Type.OBJECT) continue;
			matches.add(args[0].getClassName());
		}
		for (String className : matches) {
			if (DynamicMappings.searchConstantPoolForStrings(className, "Solid", "Mipped Cutout", "Cutout", "Translucent")) {
				addClassMapping("net/minecraft/util/EnumWorldBlockLayer", className);
				enumWorldBlockLayer_name = className;
				break;
			}
		}
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(renderGlobal, null, "(L" + enumWorldBlockLayer_name + ";)V");
		if (methods.size() == 1) {
			renderBlockLayer = methods.get(0);
			addMethodMapping("net/minecraft/client/renderer/RenderGlobal renderBlockLayer (Lnet/minecraft/util/EnumWorldBlockLayer;)V",
					renderGlobal.name + " " + methods.get(0).name + " "+ methods.get(0).desc);
		}
		
		
		
		return true;
	}
	
	@Mapping(provides={
			"net/minecraft/client/renderer/texture/TextureAtlasSprite"
			},
			providesMethods={
			"net/minecraft/client/renderer/texture/TextureMap getAtlasSprite (Ljava/lang/String;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
			},
			depends={
			"net/minecraft/client/renderer/texture/TextureMap"
			})
	public boolean processTextureMapClass()
	{
		ClassNode textureMap = getClassNodeFromMapping("net/minecraft/client/renderer/texture/TextureMap");
		if (!MeddleUtil.notNull(textureMap)) return false;
		
		List<MethodNode> methods = new ArrayList<>();
		String textureAtlasSprite_name = null;
		
		// public TextureAtlasSprite getAtlasSprite(String iconName)
		// net/minecraft/client/renderer/texture/TextureAtlasSprite
		for (MethodNode method : textureMap.methods) {
			if (!method.desc.startsWith("(Ljava/lang/String;)L")) continue;
			Type t = Type.getMethodType(method.desc);
			String className = t.getReturnType().getClassName();
			if (textureAtlasSprite_name == null && DynamicMappings.searchConstantPoolForStrings(className, "TextureAtlasSprite{name=\'", "Generating mipmaps for frame", "broken aspect ratio and not an animation")) {
				addClassMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite", className);
				methods.add(method);
				continue;
			}
			else if (textureAtlasSprite_name != null && className.equals(textureAtlasSprite_name)) {
				methods.add(method);
				continue;
			}			
		}
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/renderer/texture/TextureMap getAtlasSprite (Ljava/lang/String;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
					textureMap.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}
	

	
	
	@Mapping(provides={			
			},
			providesFields={
			"net/minecraft/client/renderer/texture/TextureAtlasSprite iconName Ljava/lang/String;",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite framesTextureData Ljava/util/List;",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite rotated Z",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite originX I",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite originY I",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite height I",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite width I",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite minU F",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite maxU F",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite minV F",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite maxV F"		
			},
			providesMethods={			
			"net/minecraft/client/renderer/texture/TextureAtlasSprite getIconName ()Ljava/lang/String;",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite getMinU ()F",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite getMinV ()F",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite getMaxU ()F",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite getMaxV ()F",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite getInterpolatedU (D)F",
			"net/minecraft/client/renderer/texture/TextureAtlasSprite getInterpolatedV (D)F"
			},
			depends={
			"net/minecraft/client/renderer/texture/TextureAtlasSprite"
			})
	public boolean processTextureAtlasSpriteClass()
	{
		ClassNode sprite = getClassNodeFromMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite");
		if (!MeddleUtil.notNull(sprite)) return false;
		
		MethodNode toString = DynamicMappings.getMethodNode(sprite, "- toString ()Ljava/lang/String;");
		if (toString == null) return false;		
		
		String iconName = null;
		String framesTextureData = null;
		String rotated = null;
		String originX = null;
		String originY = null;
		String height = null;
		String width = null;
		String minU = null;
		String maxU = null;
		String minV = null;
		String maxV = null;
		
		for (AbstractInsnNode insn = toString.instructions.getFirst(); insn != null; insn = insn.getNext()) 
		{			
			if (iconName == null && DynamicMappings.isLdcWithString(insn, "TextureAtlasSprite{name=\'")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("Ljava/lang/String;")) continue;
				iconName = fn.name;
				insn = fn;
				continue;
			}
			if (framesTextureData == null && DynamicMappings.isLdcWithString(insn, ", frameCount=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("Ljava/util/List;")) continue;
				framesTextureData = fn.name;
				insn = fn;
				continue;
			}
			if (rotated == null && DynamicMappings.isLdcWithString(insn, ", rotated=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("Z")) continue;
				rotated = fn.name;
				insn = fn;
				continue;
			}
			if (originX == null && DynamicMappings.isLdcWithString(insn, ", x=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("I")) continue;
				originX = fn.name;
				insn = fn;
				continue;
			}
			if (originY == null && DynamicMappings.isLdcWithString(insn, ", y=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("I")) continue;
				originY = fn.name;
				insn = fn;
				continue;
			}
			if (height == null && DynamicMappings.isLdcWithString(insn, ", height=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("I")) continue;
				height = fn.name;
				insn = fn;
				continue;
			}
			if (width == null && DynamicMappings.isLdcWithString(insn, ", width=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("I")) continue;
				width = fn.name;
				insn = fn;
				continue;
			}
			if (minU == null && DynamicMappings.isLdcWithString(insn, ", u0=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("F")) continue;
				minU = fn.name;
				insn = fn;
				continue;
			}
			if (maxU == null && DynamicMappings.isLdcWithString(insn, ", u1=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("F")) continue;
				maxU = fn.name;
				insn = fn;
				continue;
			}
			if (minV == null && DynamicMappings.isLdcWithString(insn, ", v0=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("F")) continue;
				minV = fn.name;
				insn = fn;
				continue;
			}
			if (maxV == null && DynamicMappings.isLdcWithString(insn, ", v1=")) {
				FieldInsnNode fn = DynamicMappings.getNextInsnNodeOfType(insn, FieldInsnNode.class);
				if (fn == null || !fn.owner.equals(sprite.name) || !fn.desc.equals("F")) continue;
				maxV = fn.name;
				insn = fn;
				continue;
			}
		}
		
		if (iconName != null) {	
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite iconName Ljava/lang/String;",
					sprite.name + " " + iconName + " Ljava/lang/String;");
		}
		
		if (framesTextureData != null) {
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite framesTextureData Ljava/util/List;",
					sprite.name + " " + framesTextureData + " Ljava/util/List;");
		}
		
		if (rotated != null) {	
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite rotated Z",
					sprite.name + " " + rotated + " Z");
		}
		
		if (originX != null) {
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite originX I",
					sprite.name + " " + originX + " I");
		}
		
		if (originY != null) {
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite originY I",
					sprite.name + " " + originY + " I");
		}
		
		if (height != null) {
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite height I",
					sprite.name + " " + height + " I");
		}
		
		if (width != null) {
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite width I",
					sprite.name + " " + width + " I");
		}
		
		if (minU != null) {
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite minU F",
					sprite.name + " " + minU + " F");
		}
		
		if (maxU != null) {
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite maxU F",
					sprite.name + " " + maxU + " F");
		}
		
		if (minV != null) {
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite minV F",
					sprite.name + " " + minV + " F");
		}
		
		if (maxV != null) {
			addFieldMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite maxV F",
					sprite.name + " " + maxV + " F");
		}
		
		
		MethodNode method;
		
		// public String getIconName()
		if ((method = findGetterMethod(sprite, iconName, "Ljava/lang/String;")) != null) {
			addMethodMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite getIconName ()Ljava/lang/String;", 
					sprite.name + " " + method.name + " " + method.desc);
		}
		
		// public float getMinU()
		if ((method = findGetterMethod(sprite, minU, "F")) != null) {
			addMethodMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite getMinU ()F", 
					sprite.name + " " + method.name + " " + method.desc);
		}
		
		// public float getMinV()
		if ((method = findGetterMethod(sprite, minV, "F")) != null) {
			addMethodMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite getMinV ()F", 
					sprite.name + " " + method.name + " " + method.desc);
		}
		
		// public float getMaxU()
		if ((method = findGetterMethod(sprite, maxU, "F")) != null) {
			addMethodMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite getMaxU ()F", 
					sprite.name + " " + method.name + " " + method.desc);
		}
				
		// public float getMaxV()
		if ((method = findGetterMethod(sprite, maxV, "F")) != null) {
			addMethodMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite getMaxV ()F", 
					sprite.name + " " + method.name + " " + method.desc);
		}
		
		// TODO - Other getters
		
		
		// public float getInterpolatedU(double u)
		// public float getInterpolatedV(double v)		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(sprite, null, "(D)F");
		if (methods.size() == 2) {
			FieldInsnNode fn1 = DynamicMappings.getNextInsnNodeOfType(methods.get(0).instructions.getFirst(), FieldInsnNode.class);
			FieldInsnNode fn2 = DynamicMappings.getNextInsnNodeOfType(methods.get(1).instructions.getFirst(), FieldInsnNode.class);
			if (fn1.owner.equals(sprite.name) && fn2.owner.equals(sprite.name)) {
				MethodNode getInterpolatedU = null;
				MethodNode getInterpolatedV = null;
				if (fn1.name.equals(maxU) && fn2.name.equals(maxV)) { getInterpolatedU = methods.get(0); getInterpolatedV = methods.get(1); }
				else if (fn1.name.equals(maxV) && fn2.name.equals(maxU)) { getInterpolatedU = methods.get(1); getInterpolatedV = methods.get(0); }
				
				if (getInterpolatedU != null && getInterpolatedV != null) {
					addMethodMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite getInterpolatedU (D)F", sprite.name + " " + getInterpolatedU.name + " (D)F");
					addMethodMapping("net/minecraft/client/renderer/texture/TextureAtlasSprite getInterpolatedV (D)F", sprite.name + " " + getInterpolatedV.name + " (D)F");
				}
			}
		}

		
		return true;
	}
	
	
	
	@Mapping(providesMethods={
			"net/minecraft/block/Block getMixedBrightnessForBlock (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)I",
			"net/minecraft/block/Block getSelectedBoundingBox (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;",
			"net/minecraft/block/Block shouldSideBeRendered (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z",
			"net/minecraft/block/Block randomDisplayTick (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Ljava/util/Random;)V",
			"net/minecraft/block/Block getSubBlocks (Lnet/minecraft/item/Item;Lnet/minecraft/creativetab/CreativeTabs;Lnet/minecraft/util/MCList;)V",
			"net/minecraft/block/Block getCreativeTabToDisplayOn ()Lnet/minecraft/creativetab/CreativeTabs;",
			"net/minecraft/block/Block getBlockLayer ()Lnet/minecraft/util/EnumWorldBlockLayer;",
			//"net/minecraft/block/Block colorMultiplier (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;I)I"
			"net/minecraft/block/Block isTranslucent (Lnet/minecraft/block/state/IBlockState;)Z",
			"net/minecraft/block/Block getAmbientOcclusionLightValue (Lnet/minecraft/block/state/IBlockState;)F"
			},
			dependsMethods={
			"net/minecraft/block/Block getCollisionBoundingBox (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;"
			},
			dependsFields={
			"net/minecraft/block/Block translucent Z"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/world/IBlockAccess",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/world/World",
			"net/minecraft/util/AxisAlignedBB",
			"net/minecraft/util/EnumFacing",
			"net/minecraft/item/Item",
			"net/minecraft/creativetab/CreativeTabs",
			"net/minecraft/util/EnumWorldBlockLayer",
			"net/minecraft/util/MCList"
			})
	public boolean processBlockClass()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode iBlockAccess = getClassNodeFromMapping("net/minecraft/world/IBlockAccess");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode aabb = getClassNodeFromMapping("net/minecraft/util/AxisAlignedBB");
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode creativeTabs = getClassNodeFromMapping("net/minecraft/creativetab/CreativeTabs");
		ClassNode enumWorldBlockLayer = getClassNodeFromMapping("net/minecraft/util/EnumWorldBlockLayer");
		ClassNode mcList = getClassNodeFromMapping("net/minecraft/util/MCList");
		if (!MeddleUtil.notNull(block, iBlockAccess, blockPos, world, aabb, enumFacing, item, creativeTabs,
				enumWorldBlockLayer, mcList)) return false;
		
		// public int getMixedBrightnessForBlock(IBlockState state, IBlockAccess worldIn, BlockPos pos)
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(block, null, DynamicMappings.assembleDescriptor("(", iBlockState, iBlockAccess, blockPos, ")I"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getMixedBrightnessForBlock (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)I",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		//MethodNode getCollisionBoundingBox = getMethodNodeFromMapping(block, "net/minecraft/block/Block getCollisionBoundingBox (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;");
		
		// public AxisAlignedBB getSelectedBoundingBox(IBlockState state, World worldIn, BlockPos pos)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, world, blockPos, ")", aabb));
		if (methods.size() == 1) { // && getCollisionBoundingBox != null) {
			/*for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
				MethodNode method = it.next();
				if (method.name.equals(getCollisionBoundingBox.name)) it.remove();
			}*/
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/block/Block getSelectedBoundingBox (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/util/AxisAlignedBB;",
						block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}
		
		
		// public boolean shouldSideBeRendered(IBlockState param0, IBlockAccess param1, BlockPos param2, EnumFacing param3)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, iBlockAccess, blockPos, enumFacing, ")Z"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block shouldSideBeRendered (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void randomDisplayTick(IBlockState param0, World param1, BlockPos param2, Random param3)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, world, blockPos, "Ljava/util/Random;)V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block randomDisplayTick (Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Ljava/util/Random;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public void getSubBlocks(Item itemIn, CreativeTabs tab, List list)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", item, creativeTabs, mcList, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getSubBlocks (Lnet/minecraft/item/Item;Lnet/minecraft/creativetab/CreativeTabs;Lnet/minecraft/util/MCList;)V",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public CreativeTabs getCreativeTabToDisplayOn()
		methods = getMatchingMethods(block, null, assembleDescriptor("()", creativeTabs));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getCreativeTabToDisplayOn ()Lnet/minecraft/creativetab/CreativeTabs;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public EnumWorldBlockLayer getBlockLayer()
		methods = getMatchingMethods(block, null, "()L" + enumWorldBlockLayer.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getBlockLayer ()Lnet/minecraft/util/EnumWorldBlockLayer;",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// Removed at 16w02a, moved to separate color override registry
		// public int colorMultiplier(IBlockAccess worldIn, BlockPos pos, int renderPass)
		/*methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockAccess, blockPos, "I)I"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block colorMultiplier (Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;I)I",
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}*/
		
		
		// public boolean isTranslucent(IBlockState state)
		FieldNode translucent_field = getFieldNodeFromMapping(block, "net/minecraft/block/Block translucent Z");
		if (translucent_field != null) {
			methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, ")Z"));
			methods = filterMethodsUsingField(methods, block.name, translucent_field.name, translucent_field.desc);
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/block/Block isTranslucent (Lnet/minecraft/block/state/IBlockState;)Z",
						block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}	
		

		// public float getAmbientOcclusionLightValue(IBlockState state)
		methods = getMatchingMethods(block, null, assembleDescriptor("(", iBlockState, ")F"));
		if (methods.size() == 1) {
			List<Float> floats = getFloatsFromMethod(methods.get(0));			
			if (floats.size() == 1 && floats.get(0) == 0.2F) {
				addMethodMapping("net/minecraft/block/Block getAmbientOcclusionLightValue (Lnet/minecraft/block/state/IBlockState;)F",
						block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
		}		
				
		
		return true;		
	}
	
	
	/*
	 * Methods removed for 16w02a, all are moved out of the Block class
	 * TODO - Locate ColorizerGrass again elsewhere, and identify new color override registry 
	 * 
	@Mapping(provides="net/minecraft/world/ColorizerGrass",
			providesMethods={
			"net/minecraft/block/Block getBlockColor ()I",
			"net/minecraft/block/Block getRenderColor (Lnet/minecraft/block/state/IBlockState;)I",
			"net/minecraft/world/ColorizerGrass getGrassColor (DD)I"
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/block/BlockGrass",
			"net/minecraft/block/state/IBlockState"
			})
	public boolean processBlockGrassClass()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode grass = getClassNodeFromMapping("net/minecraft/block/BlockGrass");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		if (!MeddleUtil.notNull(block, grass, iBlockState)) return false;
		
		
		// public int getBlockColor()
		List<MethodNode> methods = getMatchingMethods(grass, null, "()I");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getBlockColor ()I", 
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			// ColorizerGrass.getGrassColor(D, D);
			List<MethodInsnNode> nodes = getAllInsnNodesOfType(methods.get(0), MethodInsnNode.class);
			if (nodes.size() == 1 && nodes.get(0).getOpcode() == Opcodes.INVOKESTATIC && nodes.get(0).desc.equals("(DD)I")) {
				addClassMapping("net/minecraft/world/ColorizerGrass", nodes.get(0).owner);
				addMethodMapping("net/minecraft/world/ColorizerGrass getGrassColor (DD)I", 
						nodes.get(0).owner + " " + nodes.get(0).name + " (DD)I");
			}
		}
		
		
		// public int getRenderColor(IBlockState state)
		methods = getMatchingMethods(grass, null, "(L" + iBlockState.name + ";)I");		
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			MethodNode method = it.next();
			// Strip out getMetaFromState
			if (matchOpcodeSequence(method.instructions.getFirst(), Opcodes.ICONST_0, Opcodes.IRETURN)) it.remove();
		}
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/block/Block getRenderColor (Lnet/minecraft/block/state/IBlockState;)I", 
					block.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}		
		
		return true;
	}
	*/
	
	
	@Mapping(provides={
			"net/minecraft/client/gui/GuiSlot",
			"net/minecraft/client/gui/GuiListExtended",
			"net/minecraft/client/gui/ServerSelectionList",
			"net/minecraft/client/multiplayer/ServerList"
			},
			dependsMethods={
			"net/minecraft/client/gui/GuiScreen initGui ()V"
			},
			depends={
			"net/minecraft/client/gui/GuiMultiplayer",
			"net/minecraft/client/Minecraft"
			})
	public boolean processGuiMultiplayerClass()
	{
		ClassNode guiMultiplayer = getClassNodeFromMapping("net/minecraft/client/gui/GuiMultiplayer");
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		if (!MeddleUtil.notNull(guiMultiplayer, minecraft)) return false;
		
		MethodNode initGui = getMethodNodeFromMapping(guiMultiplayer, "net/minecraft/client/gui/GuiScreen initGui ()V");
		if (initGui == null) return false;
		
		String serverList = null;
		
		List<TypeInsnNode> nodes = getAllInsnNodesOfType(initGui.instructions.getFirst(), TypeInsnNode.class);
		for (TypeInsnNode node : nodes) {
			if (node.desc.startsWith("java/")) continue;			
			if (serverList == null && searchConstantPoolForStrings(node.desc, "servers.dat", "servers", "Couldn\'t load server list")) {
				addClassMapping("net/minecraft/client/multiplayer/ServerList", node.desc);
				serverList = node.desc;
				continue;
			}		
		}
		
		List<MethodInsnNode> miNodes = getAllInsnNodesOfType(initGui.instructions.getFirst(), MethodInsnNode.class);
		for (Iterator<MethodInsnNode> it = miNodes.iterator(); it.hasNext(); ) {
			MethodInsnNode mn = it.next();		
			if (!mn.name.equals("<init>") || !mn.desc.equals("(L" + guiMultiplayer.name + ";L" + minecraft.name + ";IIIII)V"))
				it.remove();
		}
		ClassNode serverSelectionList = null;
		if (miNodes.size() == 1) {
			serverSelectionList = getClassNode(miNodes.get(0).owner);
			if (serverSelectionList.superName.equals("java/lang/Object")) return false;
			
			ClassNode guiListExtended = getClassNode(serverSelectionList.superName);
			if (guiListExtended.superName.equals("java/lang/Object")) return false;
			if ((guiListExtended.access & Opcodes.ACC_ABSTRACT) == 0) return false;
			
			ClassNode guiSlot = getClassNode(guiListExtended.superName);
			if ((guiSlot.access & Opcodes.ACC_ABSTRACT) == 0) return false;
			
			if (guiSlot.superName.equals("java/lang/Object")) {
				addClassMapping("net/minecraft/client/gui/ServerSelectionList", serverSelectionList.name);
				addClassMapping("net/minecraft/client/gui/GuiListExtended", guiListExtended.name);
				addClassMapping("net/minecraft/client/gui/GuiSlot", guiSlot.name);
			}
			else {
				serverSelectionList = null;
				guiListExtended = null;
				guiSlot = null;
			}			
		}
		
		return true;
	}
	
	
	@Mapping(provides={
			"net/minecraft/client/gui/GuiListExtended$IGuiListEntry"
			},
			providesMethods={
			"net/minecraft/client/gui/GuiSlot elementClicked (IZII)V",
			"net/minecraft/client/gui/GuiSlot isSelected (I)Z",
			"net/minecraft/client/gui/GuiSlot drawBackground ()V",
			"net/minecraft/client/gui/GuiSlot drawSlot (IIIIII)V",
			"net/minecraft/client/gui/GuiSlot func_178040_a (III)V",
			"net/minecraft/client/gui/GuiListExtended getListEntry (I)Lnet/minecraft/client/gui/GuiListExtended$IGuiListEntry;",
			"net/minecraft/client/gui/GuiListExtended mouseClicked (III)Z",
			"net/minecraft/client/gui/GuiListExtended mouseReleased (III)Z"
			},
			depends={
			"net/minecraft/client/gui/GuiListExtended",
			"net/minecraft/client/gui/GuiSlot"
			})
	public boolean processGuiListExtendedClass()
	{
		ClassNode guiListExtended = getClassNodeFromMapping("net/minecraft/client/gui/GuiListExtended");
		ClassNode guiSlot = getClassNodeFromMapping("net/minecraft/client/gui/GuiSlot");
		if (!MeddleUtil.notNull(guiListExtended, guiSlot)) return false;
		
		// protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY)
		List<MethodNode> methods = getMatchingMethods(guiListExtended, null, "(IZII)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/GuiSlot elementClicked (IZII)V",
					guiSlot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		// protected boolean isSelected(int slotIndex)
		methods = getMatchingMethods(guiListExtended, null, "(I)Z");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/GuiSlot isSelected (I)Z",
					guiSlot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected void drawBackground()
		methods = getMatchingMethods(guiListExtended, null, "()V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/GuiSlot drawBackground ()V",
					guiSlot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected void drawSlot(int entryID, int p_180791_2_, int p_180791_3_, int p_180791_4_, int mouseXIn, int mouseYIn)
		methods = getMatchingMethods(guiListExtended, null, "(IIIIII)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/GuiSlot drawSlot (IIIIII)V",
					guiSlot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// protected void func_178040_a(int p_178040_1_, int p_178040_2_, int p_178040_3_)
		methods = getMatchingMethods(guiListExtended, null, "(III)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/gui/GuiSlot func_178040_a (III)V",
					guiSlot.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public abstract GuiListExtended.IGuiListEntry getListEntry(int p_148180_1_);
		methods.clear();
		for (MethodNode mn : guiListExtended.methods) {
			if (mn.desc.startsWith("(I)L")) methods.add(mn);
		}
		ClassNode iGuiListEntry = null;
		if (methods.size() == 1) {
			Type t = Type.getMethodType(methods.get(0).desc);
			Type returnType = t.getReturnType();
			iGuiListEntry = getClassNode(returnType.getClassName());
			if (iGuiListEntry != null && (iGuiListEntry.access & Opcodes.ACC_INTERFACE) != 0) {
				addClassMapping("net/minecraft/client/gui/GuiListExtended$IGuiListEntry", iGuiListEntry.name);
				addMethodMapping("net/minecraft/client/gui/GuiListExtended getListEntry (I)Lnet/minecraft/client/gui/GuiListExtended$IGuiListEntry;",
						guiListExtended.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
			else iGuiListEntry = null;
		}
		
		
		// public boolean mouseClicked(int mouseX, int mouseY, int mouseEvent)
		// public boolean mouseReleased(int p_148181_1_, int p_148181_2_, int p_148181_3_)
		methods = getMatchingMethods(guiListExtended, null, "(III)Z");
		if (methods.size() == 2) {
			List<String> mousePressed = new ArrayList<>();
			List<String> mouseReleased = new ArrayList<>();
			
			List<MethodInsnNode> mnList1 = getAllInsnNodesOfType(methods.get(0).instructions.getFirst(), MethodInsnNode.class);
			List<MethodInsnNode> mnList2 = getAllInsnNodesOfType(methods.get(1).instructions.getFirst(), MethodInsnNode.class);
			for (MethodInsnNode mn : mnList1) {
				if (mn.desc.equals("(IIIIII)Z")) { mousePressed.add(methods.get(0).name); continue; }
				else if (mn.desc.equals("(IIIIII)V")) { mouseReleased.add(methods.get(0).name); continue; }
			}
			for (MethodInsnNode mn : mnList2) {
				if (mn.desc.equals("(IIIIII)Z")) { mousePressed.add(methods.get(1).name); continue; }
				else if (mn.desc.equals("(IIIIII)V")) { mouseReleased.add(methods.get(1).name); continue; }
			}
			
			if (mousePressed.size() == 1 && mouseReleased.size() == 1) {
				addMethodMapping("net/minecraft/client/gui/GuiListExtended mouseClicked (III)Z", guiListExtended.name + " " + mousePressed.get(0) + " (III)Z");
				addMethodMapping("net/minecraft/client/gui/GuiListExtended mouseReleased (III)Z", guiListExtended.name + " " + mouseReleased.get(0) + " (III)Z");
			}
		}
		
		
		
		
		return true;
	}
	
	
	
	
	
	
	
	
	
	public static MethodNode findGetterMethod(ClassNode cn, String fieldName, String fieldDesc)
	{
		int returnOpcode = Opcodes.ARETURN;		
		if (fieldDesc.equals("I")) returnOpcode = Opcodes.IRETURN;
		else if (fieldDesc.equals("F")) returnOpcode = Opcodes.FRETURN;
		else if (fieldDesc.equals("D")) returnOpcode = Opcodes.DRETURN;
		else if (fieldDesc.equals("J")) returnOpcode = Opcodes.LRETURN;	
		
		List<MethodNode> methods = new ArrayList<>();
		
		for (MethodNode method : cn.methods) {			
			AbstractInsnNode[] list = DynamicMappings.getOpcodeSequenceArray(method.instructions.getFirst(), Opcodes.ALOAD, Opcodes.GETFIELD, returnOpcode);
			if (list == null) continue;
			FieldInsnNode fn = (FieldInsnNode)list[1];
			if (fn.owner.equals(cn.name) && fn.name.equals(fieldName)) methods.add(method);
		}
		
		if (methods.size() == 1) return methods.get(0);
		else return null;
	}
	
		

	public static void main(String[] args)
	{
		DynamicMappings.main(args);
		
		/*DynamicMappings.generateClassMappings();
		generateClassMappings();

		String[] sortedKeys = DynamicMappings.classMappings.keySet().toArray(new String[0]);
		Arrays.sort(sortedKeys);
		for (String key : sortedKeys) {
			String className = DynamicMappings.getClassMapping(key);
			System.out.println(key + " -> " + (className != null ? className : "[MISSING]"));
		}*/
	}


	@Mapping(providesFields={
			"net/minecraft/util/EnumWorldBlockLayer SOLID Lnet/minecraft/util/EnumWorldBlockLayer;",
			"net/minecraft/util/EnumWorldBlockLayer CUTOUT_MIPPED Lnet/minecraft/util/EnumWorldBlockLayer;",
			"net/minecraft/util/EnumWorldBlockLayer CUTOUT Lnet/minecraft/util/EnumWorldBlockLayer;",
			"net/minecraft/util/EnumWorldBlockLayer TRANSLUCENT Lnet/minecraft/util/EnumWorldBlockLayer;"
			},
			depends={
			"net/minecraft/util/EnumWorldBlockLayer"
			})
	public boolean processEnumWorldBlockLayerClass()
	{
		ClassNode blockLayer = getClassNodeFromMapping("net/minecraft/util/EnumWorldBlockLayer");
		if (blockLayer == null) return false;
		
		MethodNode clinit = getMethodNode(blockLayer, "--- <clinit> ()V");
		if (clinit != null) {
			String name = null;
			for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				String temp = getLdcString(insn);
				if (name == null && temp != null) { name = temp; continue; }
				
				if (name != null && insn.getOpcode() == Opcodes.PUTSTATIC) {
					FieldInsnNode fn = (FieldInsnNode)insn;
					if (!fn.desc.equals("L" + blockLayer.name + ";")) continue;
					addFieldMapping("net/minecraft/util/EnumWorldBlockLayer " + name + " Lnet/minecraft/util/EnumWorldBlockLayer;",
							blockLayer.name + " " + fn.name + " " + fn.desc);
					name = null;
				}
			}
		}
		
		return true;
	}

	@Mapping(depends={
			"net/minecraft/client/renderer/color/ItemColors",
			"net/minecraft/item/ItemStack",
			"net/minecraft/item/Item",
			"net/minecraft/block/Block"
			},
			provides = {
			"net/minecraft/client/renderer/color/IItemColor"
			},
			providesMethods={
			"net/minecraft/client/renderer/color/ItemColors getItemColor (Lnet/minecraft/item/ItemStack;I)I",
			"net/minecraft/client/renderer/color/ItemColors registerBlockColor (Lnet/minecraft/client/renderer/color/IItemColor;[Lnet/minecraft/block/Block;)V",
			"net/minecraft/client/renderer/color/ItemColors registerItemColor (Lnet/minecraft/client/renderer/color/IItemColor;[Lnet/minecraft/item/Item;)V",
			"net/minecraft/client/renderer/color/IItemColor getItemColor (Lnet/minecraft/item/ItemStack;I)I"
			}			
	)
	public boolean processItemColorClass()
	{
		ClassNode itemColors = getClassNodeFromMapping("net/minecraft/client/renderer/color/ItemColors");
		ClassNode iitemColor = null;
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		if (!MeddleUtil.notNull(itemColors, itemStack, item, block)) return false;

		List<MethodNode> methods = getMatchingMethods(itemColors, null, assembleDescriptor("(", itemStack, "I)I"));
		if (methods.size() == 1) { 
			addMethodMapping("net/minecraft/client/renderer/color/ItemColors getItemColor (Lnet/minecraft/item/ItemStack;I)I",
					itemColors.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}

		methods = getMatchingMethods(itemColors, Opcodes.ACC_PUBLIC, Type.VOID, Type.OBJECT, Type.ARRAY);
		if(methods.size() == 2){
			addClassMapping("net/minecraft/client/renderer/color/IItemColor", Type.getArgumentTypes(methods.get(0).desc)[0].getClassName());
			iitemColor = getClassNodeFromMapping("net/minecraft/client/renderer/color/IItemColor");
			addMethodMapping("net/minecraft/client/renderer/color/IItemColor getItemColor (Lnet/minecraft/item/ItemStack;I)I",
					iitemColor.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		methods = getMatchingMethods(itemColors, null, assembleDescriptor("(", iitemColor, "[", block, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/renderer/color/ItemColors registerBlockColor (Lnet/minecraft/client/renderer/color/IItemColor;[Lnet/minecraft/block/Block;)V",
					itemColors.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		methods = getMatchingMethods(itemColors, null, assembleDescriptor("(", iitemColor, "[", item, ")V"));
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/renderer/color/ItemColors registerItemColor (Lnet/minecraft/client/renderer/color/IItemColor;[Lnet/minecraft/item/Item;)V",
					itemColors.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}


	@Mapping(depends = "net/minecraft/client/gui/GuiIngame",
			provides = "net/minecraft/client/gui/ScaledResolution",
	        providesMethods = "net/minecraft/client/gui/GuiIngame renderPotionEffects (Lnet/minecraft/client/gui/ScaledResolution;)V")
	public boolean processGuiIngameClass()
	{
		ClassNode node = getClassNodeFromMapping("net/minecraft/client/gui/GuiIngame");
		if(!MeddleUtil.notNull(node)) return false;

		//ScaledResolution
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(node, Opcodes.ACC_PROTECTED, Type.VOID, Type.OBJECT);
		if(methods.size() == 1){
			String scaledResolution = Type.getArgumentTypes(methods.get(0).desc)[0].getClassName();
			addClassMapping("net/minecraft/client/gui/ScaledResolution", scaledResolution);
			addMethodMapping("net/minecraft/client/gui/GuiIngame renderPotionEffects (Lnet/minecraft/client/gui/ScaledResolution;)V", node.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}

		return true;
	}

}

