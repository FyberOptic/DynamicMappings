package net.fybertech.dynamicmappings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fybertech.meddle.Meddle;
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



public class DynamicClientMappings
{
	
	public static void addClassMapping(String className, ClassNode cn) {
		DynamicMappings.addClassMapping(className, cn);
	}
	
	public static void addClassMapping(String className, String cn) {
		DynamicMappings.addClassMapping(className, cn);
	}
	
	public static void addMethodMapping(String deobf, String obf) {
		DynamicMappings.addMethodMapping(deobf, obf);
	}
	
	public static void addFieldMapping(String deobf, String obf) {
		DynamicMappings.addFieldMapping(deobf, obf);
	}
	
	public static ClassNode getClassNode(String className) {
		return DynamicMappings.getClassNode(className);
	}
	
	public static ClassNode getClassNodeFromMapping(String mapping) {
		return DynamicMappings.getClassNodeFromMapping(mapping);
	}
	

	@Mapping(provides="net/minecraft/client/main/Main")
	public static boolean getMainClass()
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
	public static boolean getMinecraftClass()
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
			"net/minecraft/server/integrated/IntegratedServer"
			},
			providesMethods={
			"net/minecraft/client/Minecraft getMinecraft ()Lnet/minecraft/client/Minecraft;",
			"net/minecraft/client/Minecraft getRenderItem ()Lnet/minecraft/client/renderer/entity/RenderItem;",
			"net/minecraft/client/Minecraft refreshResources ()V",
			"net/minecraft/client/Minecraft launchIntegratedServer (Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
			"net/minecraft/client/Minecraft getIntegratedServer ()Lnet/minecraft/server/integrated/IntegratedServer;"
			},
			depends={
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/renderer/entity/RenderItem"
			})
	public static boolean parseMinecraftClass()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode renderItem = getClassNodeFromMapping("net/minecraft/client/renderer/entity/RenderItem");
		if (minecraft == null || renderItem == null) return false;
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(minecraft,  null, "()L" + minecraft.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/client/Minecraft getMinecraft ()Lnet/minecraft/client/Minecraft;",
					minecraft.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
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
		
		if (integratedServer_name != null) {
			methods = DynamicMappings.getMatchingMethods(minecraft, null, "()L" + integratedServer_name + ";");
			if (methods.size() == 1) {
				addMethodMapping("net/minecraft/client/Minecraft getIntegratedServer ()Lnet/minecraft/server/integrated/IntegratedServer;",
						minecraft.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			}
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
	public static boolean parseRenderItemClass()
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
	public static boolean getRenderItemClass()
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


	@Mapping(provides="net/minecraft/client/renderer/ItemModelMesher", depends="net/minecraft/client/renderer/entity/RenderItem")
	public static boolean getItemModelMesherClass() 
	{
		ClassNode renderItem = getClassNodeFromMapping("net/minecraft/client/renderer/entity/RenderItem");
		if (renderItem == null) return false;

		// Find constructor RenderItem(TextureManager, ModelManager)
		MethodNode initMethod = null;
		int count = 0;
		for (MethodNode method : (List<MethodNode>)renderItem.methods) {
			if (!method.name.equals("<init>")) continue;
			if (!DynamicMappings.checkMethodParameters(method, Type.OBJECT, Type.OBJECT)) continue;
			count++;
			initMethod = method;
		}
		if (count != 1) return false;

		Type t = Type.getMethodType(initMethod.desc);
		Type[] args = t.getArgumentTypes();
		// TODO: Get TextureManager and ModelManager from args

		String className = null;

		count = 0;
		for (AbstractInsnNode insn = initMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.NEW) {
				TypeInsnNode tn = (TypeInsnNode)insn;
				className = tn.desc;
				count++;
			}
		}
		if (count != 1 || className == null) return false;

		// We'll assume this is it, might do more detailed confirmations later if necessary
		addClassMapping("net/minecraft/client/renderer/ItemModelMesher", getClassNode(className));
		return true;
	}


	@Mapping(provides={
			"net/minecraft/client/gui/GuiMainMenu",  
			"net/minecraft/client/gui/GuiIngame",
			"net/minecraft/client/multiplayer/GuiConnecting",
			"net/minecraft/client/renderer/RenderGlobal"},
			depends="net/minecraft/client/Minecraft")
	public static boolean getGuiMainMenuClass()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		if (minecraft == null) return false;

		List<String> postStartupClasses = new ArrayList<String>();
		List<String> startupClasses = new ArrayList<String>();

		boolean foundMethod = false;
		for (MethodNode method : (List<MethodNode>)minecraft.methods) {
			//if (!DynamicMappings.checkMethodParameters(method, Type.OBJECT)) continue;
			Type t = Type.getMethodType(method.desc);
			if (t.getReturnType().getSort() != Type.VOID) continue;
			if (t.getArgumentTypes().length != 0) continue;

			boolean foundLWJGLVersion = false;
			boolean foundPostStartup = false;
			boolean foundStartup = false;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
			{
				if (!foundLWJGLVersion && !DynamicMappings.isLdcWithString(insn, "LWJGL Version: ")) continue;
				foundLWJGLVersion = true;
				
				if (!foundStartup && !DynamicMappings.isLdcWithString(insn, "Startup")) continue;
				foundStartup = true;
				
				foundMethod = true;
				
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

			if (guiConnecting == null && DynamicMappings.searchConstantPoolForStrings(className, "Connecting to", "connect.connecting")) {
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
		for (String className : startupClasses) {
			if (renderGlobal == null && DynamicMappings.searchConstantPoolForStrings(className, "textures/environment/moon_phases.png", "Exception while adding particle", "random.click")) {
				renderGlobal = className;
				continue;
			}
		}

		if (guiMainMenu != null)
			addClassMapping("net/minecraft/client/gui/GuiMainMenu", getClassNode(guiMainMenu));
		
		if (guiIngame != null)
			addClassMapping("net/minecraft/client/gui/GuiIngame", getClassNode(guiIngame));
		
		if (guiConnecting != null)
			addClassMapping("net/minecraft/client/multiplayer/GuiConnecting", getClassNode(guiConnecting));
		
		if (renderGlobal != null)
			addClassMapping("net/minecraft/client/renderer/RenderGlobal", getClassNode(renderGlobal));
		
		return true;
	}


	@Mapping(provides="net/minecraft/client/resources/model/ModelResourceLocation",
			 depends={
			"net/minecraft/item/Item",
			"net/minecraft/client/renderer/ItemModelMesher"})
	public static boolean getModelResourceLocationClass()
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
	
	
	@Mapping(providesMethods={
			"net/minecraft/item/Item getColorFromItemStack (Lnet/minecraft/item/ItemStack;I)I",
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemStack"
			})
	public static boolean getItemClassMethods()
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
	}
	

	@Mapping(providesMethods={
			"net/minecraft/client/renderer/ItemModelMesher register (Lnet/minecraft/item/Item;ILnet/minecraft/client/resources/model/ModelResourceLocation;)V"
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/client/renderer/ItemModelMesher",
			"net/minecraft/client/resources/model/ModelResourceLocation"
			})
	public static boolean parseItemModelMesherClass()
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
	public static boolean findGuiStuff()
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
			"net/minecraft/client/gui/GuiScreen mouseReleased (III)V"
			},
			depends={
			"net/minecraft/client/gui/GuiScreen",
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/renderer/entity/RenderItem"
			})			
	public static boolean processGuiScreenClass()
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
						if (DynamicMappings.searchConstantPoolForStrings(tn.desc, "textures/gui/widgets.png", "gui.button.press")) {
							addClassMapping("net/minecraft/client/gui/GuiButton", tn.desc);
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
			"net/minecraft/client/gui/GuiMainMenu",
			"net/minecraft/client/gui/GuiScreen",
			"net/minecraft/client/gui/Gui",
			"net/minecraft/client/Minecraft",
			"net/minecraft/client/gui/FontRenderer"
			})			
	public static boolean processGuiMainMenuClass()
	{
		ClassNode guiMainMenu = getClassNodeFromMapping("net/minecraft/client/gui/GuiMainMenu");
		ClassNode guiScreen = getClassNodeFromMapping("net/minecraft/client/gui/GuiScreen");
		ClassNode gui = getClassNodeFromMapping("net/minecraft/client/gui/Gui");
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode fontRenderer = getClassNodeFromMapping("net/minecraft/client/gui/FontRenderer");
		if (!MeddleUtil.notNull(guiMainMenu, guiScreen, gui, minecraft, fontRenderer)) return false;		
	
		String drawScreenDesc = "net/minecraft/client/gui/GuiScreen drawScreen (IIF)V";
		MethodNode drawScreenMethod = DynamicMappings.getMethodNodeFromMapping(guiMainMenu, drawScreenDesc);
		if (drawScreenMethod == null) return false;
		
		String drawStringMethodsDesc = "(L" + fontRenderer.name + ";Ljava/lang/String;III)V";
		
		String getStringWidthName = null;
		String drawCenteredStringName = null;
		String drawStringName = null;
		
		for (AbstractInsnNode insn = drawScreenMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			MethodInsnNode mn = (MethodInsnNode)insn;
			
			if (getStringWidthName == null && mn.owner.equals(fontRenderer.name) && mn.desc.equals("(Ljava/lang/String;)I")) {
				getStringWidthName = mn.name;
				addMethodMapping("net/minecraft/client/gui/FontRenderer getStringWidth (Ljava/lang/String;)I",
						fontRenderer.name + " " + mn.name + " " + mn.desc);
			}
			
			if (mn.owner.equals(guiMainMenu.name)  && mn.desc.equals(drawStringMethodsDesc)) {
				if (drawCenteredStringName == null) {
					drawCenteredStringName = mn.name;
					addMethodMapping("net/minecraft/client/gui/Gui drawCenteredString (Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V",
							gui.name + " " + mn.name + " " + mn.desc);
				}
				else if (drawStringName == null) {
					drawStringName = mn.name;
					addMethodMapping("net/minecraft/client/gui/Gui drawString (Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V",
							gui.name + " " + mn.name + " " + mn.desc);
				}
			}
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
	public static boolean processBlockStateClass()
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
			depends={
			"net/minecraft/client/Minecraft",
			"net/minecraft/entity/player/EntityPlayer"
			})		
	public static boolean getEntityPlayerSPClass()
	{
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		if (minecraft == null || entityPlayer == null) return false;		
		
		String entityPlayerSP_name = null;
		
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(minecraft,  null, "(I)V");
		startloop:
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.PUTFIELD) continue;
				FieldInsnNode fn = (FieldInsnNode)insn;
				if (!fn.owner.equals(minecraft.name) || fn.desc.contains("/")) continue;
				String className = fn.desc.substring(1, fn.desc.length() - 1);				
				if (DynamicMappings.searchConstantPoolForStrings(className, "minecraft:chest", "minecraft:anvil", "portal.trigger")) {
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
	public static boolean processEntityPlayerSPClass()
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
	public static boolean getGuiChestClass()
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
			"net/minecraft/client/Minecraft thePlayer Lnet/minecraft/client/entity/EntityPlayerSP;"
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
			"net/minecraft/client/gui/GuiScreen"
			})	
	public static boolean processGuiContainerClass()
	{
		ClassNode guiContainer = getClassNodeFromMapping("net/minecraft/client/gui/inventory/GuiContainer");
		ClassNode slot = getClassNodeFromMapping("net/minecraft/inventory/Slot");
		ClassNode minecraft = getClassNodeFromMapping("net/minecraft/client/Minecraft");
		ClassNode playerSP = getClassNodeFromMapping("net/minecraft/client/entity/EntityPlayerSP");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode guiScreen = getClassNodeFromMapping("net/minecraft/client/gui/GuiScreen");
		if (!MeddleUtil.notNull(guiContainer, slot, minecraft, playerSP, itemStack, guiScreen)) return false;
		
		// protected void handleMouseClick(Slot slotIn, int slotId, int clickedButton, int clickType)
		List<MethodNode> methods = DynamicMappings.getMatchingMethods(guiContainer, null, "(L" + slot.name + ";III)V");
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
	public static boolean getNetHandlerPlayClientClass()
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
	public static boolean processNetHandlerPlayClientClass()
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
	public static boolean processS2DPacketOpenWindowClass()
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
	public static boolean getKeybindingClass()
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
			"net/minecraft/client/settings/KeyBinding isPressed ()Z"
			},
			depends={
			"net/minecraft/client/settings/KeyBinding"
			})
	public static boolean processKeybindingClass()
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
		
		
		// public static void unPressAllKeys()
		// public static void resetKeyBindingArrayAndHash()
		methods = DynamicMappings.getMatchingMethods(keyBinding, null, "()V");
		methods = DynamicMappings.removeMethodsWithoutFlags(methods, Opcodes.ACC_STATIC);
		for (Iterator<MethodNode> it = methods.iterator(); it.hasNext();) {
			if (it.next().name.contains("<")) it.remove();
		}
		if (methods.size() == 2 && unpressKey != null) 
		{
			MethodNode unPressAllKeys = null;
			MethodNode resetKeyBindingArrayAndHash = null;
			
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
	public static boolean getRenderHelperClass()
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
	public static boolean processRenderHelperClass()
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
				if (!mn.owner.equals("org/lwjgl/opengl/GL11") || !mn.name.equals("glLight")) continue;
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
	
	
	
	

	public static void generateClassMappings()
	{
		if (!MeddleUtil.isClientJar()) return;
		
		DynamicMappings.registerMappingsClass(DynamicClientMappings.class);		
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


}

