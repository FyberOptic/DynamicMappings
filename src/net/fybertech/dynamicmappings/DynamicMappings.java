package net.fybertech.dynamicmappings;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.fybertech.meddle.MeddleUtil;


public class DynamicMappings
{
	public static final Logger LOGGER = LogManager.getLogger("Meddle");

	// The deobfuscated -> obfuscated name.
	public static final Map<String, String> classMappings = new HashMap<String, String>();
	// The obfuscated -> deobfuscated name.
	public static final Map<String, String> reverseClassMappings = new HashMap<String, String>();

	public static final Map<String, String> fieldMappings = new HashMap<String, String>();
	public static final Map<String, String> reverseFieldMappings = new HashMap<String, String>();

	public static final Map<String, String> methodMappings = new HashMap<String, String>();
	public static final Map<String, String> reverseMethodMappings = new HashMap<String, String>();

	// Used by getClassNode to avoid reloading the classes over and over
	private static Map<String, ClassNode> cachedClassNodes = new HashMap<String, ClassNode>();


	
	// Load a ClassNode from class name.  This is for loading the original
	// obfuscated classes.
	//
	// Note: *Do not* edit classes you get from this.  They're cached and used by
	// anyone doing analysis of vanilla class files.
	public static ClassNode getClassNode(String className)
	{
		if (className == null) return null;

		className = className.replace(".", "/");
		if (cachedClassNodes.containsKey(className)) return cachedClassNodes.get(className);

		//InputStream stream = Launch.classLoader.getResourceAsStream(className + ".class");
		InputStream stream = DynamicMappings.class.getClassLoader().getResourceAsStream(className + ".class");
		if (stream == null) return null;

		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) { return null; }

		ClassNode cn = new ClassNode();
		reader.accept(cn, 0);

		cachedClassNodes.put(className, cn);

		return cn;
	}


	// Get constant pool string that an LDC is loading
	public static String getLdcString(AbstractInsnNode node)
	{
		if (!(node instanceof LdcInsnNode)) return null;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof String)) return null;
		return new String((String)ldc.cst);
	}


	// Get constant pool class that an LDC is referencing
	public static String getLdcClass(AbstractInsnNode node)
	{
		if (!(node instanceof LdcInsnNode)) return null;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof Type)) return null;
		return ((Type)ldc.cst).getClassName();
	}


	// Check if LDC is loading the specified string
	public static boolean isLdcWithString(AbstractInsnNode node, String string)
	{
		String s = getLdcString(node);
		return (s != null && string.equals(s));
	}


	// Check if LDC is loading specified int
	public static boolean isLdcWithInteger(AbstractInsnNode node, int val)
	{
		if (!(node instanceof LdcInsnNode)) return false;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof Integer)) return false;
		return ((Integer)ldc.cst) == val;
	}
	
	// Check if LDC is loading specified int
	public static boolean isLdcWithFloat(AbstractInsnNode node, float val)
	{
		if (!(node instanceof LdcInsnNode)) return false;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof Float)) return false;
		return ((Float)ldc.cst) == val;
	}


	// Get the description of the specified field from the class
	public static String getFieldDesc(ClassNode cn, String fieldName)
	{
		for (FieldNode field : cn.fields)
		{
			if (field.name.equals(fieldName)) return field.desc;
		}
		return null;
	}


	// Get the specified field node from the class
	public static FieldNode getFieldByName(ClassNode cn, String fieldName)
	{
		for (FieldNode field : cn.fields)
		{
			if (field.name.equals(fieldName)) return field;
		}
		return null;
	}


	// Search a class's constant pool for the list of strings.
	// NOTE: Strings are trimmed!  Take into account when matching.
	public static boolean searchConstantPoolForStrings(String className, String... matchStrings)
	{
		if (className == null) return false;
		className = className.replace(".", "/");
		//InputStream stream = Launch.classLoader.getResourceAsStream(className + ".class");
		InputStream stream = DynamicMappings.class.getClassLoader().getResourceAsStream(className + ".class");
		if (stream == null) return false;

		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) { return false; }

		int itemCount = reader.getItemCount();
		char[] buffer = new char[reader.getMaxStringLength()];

		int matches = 0;

		for (int n = 1; n < itemCount; n++)	{
			int pos = reader.getItem(n);
			if (pos == 0 || reader.b[pos - 1] != 8) continue;

			Arrays.fill(buffer, (char)0);
			reader.readUTF8(pos,  buffer);
			String string = (new String(buffer)).trim();

			for (int n2 = 0; n2 < matchStrings.length; n2++) {
				if (string.equals(matchStrings[n2])) { matches++; break; }
			}
		}

		return (matches == matchStrings.length);
	}

	
	// Checks if the specified class name is referenced in the class's constant pool
	public static boolean searchConstantPoolForClasses(String className, String... matchStrings)
	{
		className = className.replace(".", "/");
		InputStream stream = DynamicMappings.class.getClassLoader().getResourceAsStream(className + ".class");
		if (stream == null) return false;

		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) { return false; }

		int itemCount = reader.getItemCount();
		char[] buffer = new char[reader.getMaxStringLength()];

		int matches = 0;

		for (int n = 1; n < itemCount; n++)	{
			int pos = reader.getItem(n);
			if (pos == 0 || reader.b[pos - 1] != 7) continue;

			Arrays.fill(buffer, (char)0);
			reader.readUTF8(pos,  buffer);
			String string = (new String(buffer)).trim();

			for (int n2 = 0; n2 < matchStrings.length; n2++) {
				if (string.equals(matchStrings[n2].replace(".", "/"))) { matches++; break; }
			}
		}

		return (matches == matchStrings.length);
	}


	// Returns a list of the String types from the class's constant pool
	public static List<String> getConstantPoolStrings(String className)
	{
		List<String> strings = new ArrayList<String>();

		className = className.replace(".", "/");
		//InputStream stream = Launch.classLoader.getResourceAsStream(className + ".class");
		InputStream stream = DynamicMappings.class.getClassLoader().getResourceAsStream(className + ".class");
		if (stream == null) return null;

		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) { return null; }

		int itemCount = reader.getItemCount();
		char[] buffer = new char[reader.getMaxStringLength()];

		for (int n = 1; n < itemCount; n++)	{
			int pos = reader.getItem(n);
			if (pos == 0 || reader.b[pos - 1] != 8) continue;

			Arrays.fill(buffer, (char)0);
			reader.readUTF8(pos,  buffer);
			String string = (new String(buffer)).trim();

			strings.add(string);
		}

		return strings;
	}


	// Confirm the parameter types of a method.
	// Uses org.objectweb.asm.Type for values.
	public static boolean checkMethodParameters(MethodNode method, int ... types)
	{
		Type t = Type.getMethodType(method.desc);
		Type[] args = t.getArgumentTypes();
		if (args.length != types.length) return false;

		int len = args.length;
		for (int n = 0; n < len; n++) {
			if (args[n].getSort() != types[n]) return false;
		}

		return true;
	}


	// Finds all methods matching the specified name and/or description.
	// Both are optional.
	public static List<MethodNode> getMatchingMethods(ClassNode cn, String name, String desc)
	{
		List<MethodNode> output = new ArrayList<MethodNode>();

		for (MethodNode method : cn.methods) {
			if ((name == null || (name != null && method.name.equals(name))) &&
					(desc == null || (desc != null && method.desc.equals(desc)))) output.add(method);
		}

		return output;
	}


	// Finds all fields matching the specified name and/or description.
	// Both are optional.
	public static List<FieldNode> getMatchingFields(ClassNode cn, String name, String desc)
	{
		List<FieldNode> output = new ArrayList<FieldNode>();

		for (FieldNode field : cn.fields) {
			if ((name == null || (name != null && field.name.equals(name))) &&
					(desc == null || (desc != null && field.desc.equals(desc)))) output.add(field);
		}

		return output;
	}


	// Gets a ClassNode of the deobfuscated class name specified
	public static ClassNode getClassNodeFromMapping(String deobfClass)
	{
		return getClassNode(getClassMapping(deobfClass));
	}


	// Checks if the sequence of opcodes exists.
	// Ignores synthetic opcodes added by ASM, such as labels.
	public static boolean matchOpcodeSequence(AbstractInsnNode insn, int...opcodes)
	{
		for (int opcode : opcodes) {
			insn = getNextRealOpcode(insn);
			if (insn == null) return false;
			if (opcode != insn.getOpcode()) return false;
			insn = insn.getNext();
		}

		return true;
	}

	// Extracst the opcode sequence as an array
	// Ignores synthetic opcodes added by ASM, such as labels.
	public static AbstractInsnNode[] getOpcodeSequenceArray(AbstractInsnNode insn, int...opcodes)
	{
		AbstractInsnNode[] outNodes = new AbstractInsnNode[opcodes.length];
		int pos = 0;
		
		for (int opcode : opcodes) {
			insn = getNextRealOpcode(insn);
			if (insn == null) return null;
			if (opcode != insn.getOpcode()) return null;
			outNodes[pos++] = insn;
			insn = insn.getNext();
		}

		return outNodes;
	}
	
	
	@SuppressWarnings("unchecked")
	public static <T> List<T> getAllInsnNodesOfType(AbstractInsnNode startInsn, Class<T> classType)
	{				
		List<T> list = new ArrayList<>();
		
		for (AbstractInsnNode insn = startInsn; insn != null; insn = insn.getNext()) {
			if (insn.getClass() == classType) list.add((T)insn);
		}

		return list;
	}
	
	
	@SuppressWarnings("unchecked")
	public static <T> T getNextInsnNodeOfType(AbstractInsnNode startInsn, Class<T> classType)
	{		
		for (AbstractInsnNode insn = startInsn; insn != null; insn = insn.getNext()) {
			if (insn.getClass() == classType) return (T) insn;
		}

		return null;
	}
	


	// Still here in case anything still references it
	@Deprecated
	public static void generateMethodMappings()
	{

	}


	// Gets the obfuscated class name from the deobfuscated input
	public static String getClassMapping(String deobfClassName)
	{
		return classMappings.get(deobfClassName.replace(".",  "/"));
	}
	

	// Gets the deobfuscated class name from the obfuscated input
	public static String getReverseClassMapping(String obfClassName)
	{
		return reverseClassMappings.get(obfClassName.replace(".",  "/"));
	}


	public static void addClassMapping(String deobfClassName, ClassNode node)
	{
		if (deobfClassName == null) return;
		deobfClassName = deobfClassName.replace(".", "/");
		addClassMapping(deobfClassName, node.name);
	}

	
	public static void addClassMapping(String deobfClassName, String obfClassName)
	{
		deobfClassName = deobfClassName.replace(".", "/");
		obfClassName = obfClassName.replace(".", "/");
		classMappings.put(deobfClassName, obfClassName);
		reverseClassMappings.put(obfClassName, deobfClassName);
	}

	
	// Both inputs in the format of "class_name method_name method_desc"
	public static void addMethodMapping(String deobfMethodDesc, String obfMethodDesc)
	{
		methodMappings.put(deobfMethodDesc, obfMethodDesc);
		reverseMethodMappings.put(obfMethodDesc, deobfMethodDesc);
	}
	

	// Both inputs in the format of "class_name field_name field_desc"
	public static void addFieldMapping(String deobfFieldDesc, String obfFieldDesc)
	{
		fieldMappings.put(deobfFieldDesc, obfFieldDesc);
		reverseFieldMappings.put(obfFieldDesc, deobfFieldDesc);
	}
	

	public static String getMethodMapping(String className, String methodName, String methodDesc)
	{
		return methodMappings.get(className + " " + methodName + " " + methodDesc);
	}

	
	public static String getMethodMapping(String mapping)
	{
		return methodMappings.get(mapping);
	}
	
	public static String getReverseMethodMapping(String obfMethod) 
	{
		return reverseMethodMappings.get(obfMethod);
	}	
	
	
	public static String getFieldMapping(String mapping)
	{
		return fieldMappings.get(mapping);
	}

	
	public static String getReverseFieldMapping(String obfField)
	{
		return reverseFieldMappings.get(obfField);
	}
	
	
	public static FieldNode getFieldNodeFromMapping(ClassNode cn, String deobfMapping)
	{
		String mapping = getFieldMapping(deobfMapping);
		if (cn == null || mapping == null) return null;

		String[] split = mapping.split(" ");
		if (split.length < 3) return null;

		for (FieldNode field : cn.fields) {
			if (field.name.equals(split[1]) && field.desc.equals(split[2])) {
				return field;
			}
		}

		return null;
	}
	
	
	// Returns just the obfuscated name of a method matching the deobfuscated input
	public static String getMethodMappingName(String className, String methodName, String methodDesc)
	{
		String mapping = getMethodMapping(className, methodName, methodDesc);
		if (mapping == null) return null;
		String [] split = mapping.split(" ");
		return split.length >= 3 ? split[1] : null;
	}
	
	
	public static MethodNode getMethodNode(ClassNode cn, String obfMapping)
	{		
		if (cn == null || obfMapping == null) return null;

		String[] split = obfMapping.split(" ");
		if (split.length < 3) return null;

		for (MethodNode method : cn.methods) {
			if (method.name.equals(split[1]) && method.desc.equals(split[2])) {
				return method;
			}
		}

		return null;
	}
	
	
	public static MethodNode getMethodNodeFromMapping(ClassNode cn, String deobfMapping)
	{
		String mapping = getMethodMapping(deobfMapping);
		if (cn == null || mapping == null) return null;

		String[] split = mapping.split(" ");
		if (split.length < 3) return null;

		for (MethodNode method : cn.methods) {
			if (method.name.equals(split[1]) && method.desc.equals(split[2])) {
				return method;
			}
		}

		return null;
	}



	private static class MappingMethod
	{
		final Method method;
		final String[] provides;
		final String[] depends;

		final String[] providesMethods;
		final String[] dependsMethods;

		final String[] providesFields;
		final String[] dependsFields;

		public MappingMethod(Method m, Mapping mapping)
		{
			method = m;
			provides = mapping.provides();
			depends = mapping.depends();
			providesMethods = mapping.providesMethods();
			dependsMethods = mapping.dependsMethods();
			providesFields = mapping.providesFields();
			dependsFields = mapping.dependsFields();
		}
	}

	
	
	public static boolean simulatedMappings = false;

	// Parses a class for dynamic mappings.
	// Methods must be static and implement the Mapping annotation.
	public static boolean registerMappingsClass(Class<? extends Object> mappingsClass)
	{
		List<MappingMethod> mappingMethods = new ArrayList<MappingMethod>();

		for (Method method : mappingsClass.getMethods())
		{
			if (!method.isAnnotationPresent(Mapping.class)) continue;
			Mapping mapping = method.getAnnotation(Mapping.class);
			mappingMethods.add(new MappingMethod(method, mapping));
		}

		while (true)
		{
			int startSize = mappingMethods.size();
			for (Iterator<MappingMethod> it = mappingMethods.iterator(); it.hasNext();)
			{
				MappingMethod mm = it.next();

				boolean hasDepends = true;
				for (String depend : mm.depends) {
					if (!classMappings.keySet().contains(depend)) hasDepends = false;
				}
				for (String depend : mm.dependsFields) {
					if (!fieldMappings.keySet().contains(depend)) hasDepends = false;
				}
				for (String depend : mm.dependsMethods) {
					if (!methodMappings.keySet().contains(depend)) hasDepends = false;
				}
				if (!hasDepends) continue;
				
				if (!simulatedMappings) {
					try {
						mm.method.invoke(null);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				else {
					for (String s : mm.provides) classMappings.put(s, "---");
					for (String s : mm.providesFields) fieldMappings.put(s, "--- --- ---");
					for (String s : mm.providesMethods) methodMappings.put(s, "--- --- ---");
				}

				for (String provider : mm.provides)
				{
					if (!classMappings.keySet().contains(provider))
						System.out.println(mm.method.getName() + " didn't provide mapping for class " + provider);
				}

				for (String provider : mm.providesFields)
				{
					if (!fieldMappings.keySet().contains(provider))
						System.out.println(mm.method.getName() + " didn't provide mapping for field " + provider);
				}

				for (String provider : mm.providesMethods)
				{
					if (!methodMappings.keySet().contains(provider))
						System.out.println(mm.method.getName() + " didn't provide mapping for method " + provider);
				}

				it.remove();
			}

			if (mappingMethods.size() == 0) return true;

			if (startSize == mappingMethods.size())
			{
				System.out.println("Unmet mapping dependencies in " + mappingsClass.getName() + "!");
				for (MappingMethod mm : mappingMethods) {
					System.out.println("  Method: " + mm.method.getName());
					for (String depend : mm.depends) {
						if (!classMappings.keySet().contains(depend)) System.out.println("    " + depend);
					}
				}
				return false;
			}
		}
	}


	// Used for debugging purposes
	public static void main(String[] args)
	{
		boolean showMappings = false;
		
		generateClassMappings();
		

		System.out.println("Minecraft version: " + MeddleUtil.findMinecraftVersion());
		System.out.println("Minecraft jar type: " + (MeddleUtil.isClientJar() ? "client" : "server"));

		if (!showMappings) return;
		
		System.out.println("\nCLASSES:");	
		
		List<String> sorted = new ArrayList<String>();
		sorted.addAll(classMappings.keySet());
		Collections.sort(sorted);
		for (String s : sorted) {
			System.out.println(s + " -> " + classMappings.get(s));
		}

		System.out.println("\nFIELDS:");

		sorted.clear();
		sorted.addAll(fieldMappings.keySet());
		Collections.sort(sorted);
		for (String s : sorted) {
			System.out.println(s + " -> " + fieldMappings.get(s));
		}

		System.out.println("\nMETHODS:");

		sorted.clear();
		sorted.addAll(methodMappings.keySet());
		Collections.sort(sorted);
		for (String s : sorted) {
			System.out.println(s + " -> " + methodMappings.get(s));
		}
	}

	
	public static JarFile getMinecraftJar()
	{
		URL url = MeddleUtil.class.getClassLoader().getResource("net/minecraft/server/MinecraftServer.class");	
		if (url == null) return null;		
		
		JarFile jar = null;
		
		if ("jar".equals(url.getProtocol())) {
			JarURLConnection connection = null;
			try {
				connection = (JarURLConnection) url.openConnection();
				jar = connection.getJarFile();
			} catch (IOException e) {}
		}
		
		return jar;
	}	


	
	public static boolean isSubclassOf(String className, String superClassName)
	{
		InputStream stream = DynamicMappings.class.getClassLoader().getResourceAsStream(className + ".class");
		if (stream == null) return false;	
		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) {}
		if (reader == null) return false;
		
		String superName = reader.getSuperName();
		if (superName.equals(superClassName)) return true;
		if (superName.equals("java/lang/Object")) return false;
		return isSubclassOf(superName, superClassName);
	}
	
	
	public static void generateClassMappings()
	{		
		DynamicMappings.registerMappingsClass(DynamicMappings.class);
		if (simulatedMappings || MeddleUtil.isClientJar()) DynamicClientMappings.generateClassMappings();
		
	}


	// Use untyped list in case someone compiles without debug version of ASM.
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static List<MethodNode> getMethodsWithDescriptor(List methods, String desc)
	{
		List<MethodNode> list = new ArrayList<MethodNode>();
		for (MethodNode method : (List<MethodNode>)methods) {
			if (method.desc.equals(desc)) list.add(method);
		}
		return list;
	}

	
	public static List<MethodNode> removeMethodsWithFlags(List<MethodNode> methods, int accFlags)
	{
		List<MethodNode> outList = new ArrayList<MethodNode>();
		for (MethodNode mn : methods) {
			if ((mn.access & accFlags) == 0) outList.add(mn);
		}
		return outList;
	}

	
	public static List<MethodNode> removeMethodsWithoutFlags(List<MethodNode> methods, int accFlags)
	{
		List<MethodNode> outList = new ArrayList<MethodNode>();
		for (MethodNode mn : methods) {
			if ((mn.access & accFlags) != 0) outList.add(mn);
		}
		return outList;
	}
	
	
	public static AbstractInsnNode findNextOpcodeNum(AbstractInsnNode insn, int opcode)
	{
		while (insn != null) {
			if (insn.getOpcode() == opcode) break;
			insn = insn.getNext();
		}
		return insn;
	}
	

	
	
	
	
	
	
	
	
	
	
	
	
	
	//////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////	
	//////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	// The starting point
	@Mapping(provides="net/minecraft/server/MinecraftServer")
	public static boolean getMinecraftServerClass()
	{
		ClassNode cn = getClassNode("net/minecraft/server/MinecraftServer");
		if (cn == null) return false;
		addClassMapping("net/minecraft/server/MinecraftServer", cn);
		return true;
	}


	@Mapping(provides="net/minecraft/world/World", depends="net/minecraft/server/MinecraftServer")
	public static boolean getWorldClass()
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


	@Mapping(provides="net/minecraft/init/Blocks", depends="net/minecraft/world/World")
	public static boolean getBlocksClass()
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
	public static boolean getBlockClass()
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


	@Mapping(provides="net/minecraft/block/state/IBlockState", depends="net/minecraft/block/Block")
	public static boolean getIBlockStateClass()
	{
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (block == null) return false;

		Map<String, Integer> counts = new HashMap<String, Integer>();

		// Count the times a class is used as the third parameter of a method
		for (MethodNode method : block.methods) {
			Type t = Type.getMethodType(method.desc);
			Type[] args = t.getArgumentTypes();
			if (args.length < 3) continue;

			String name = args[2].getClassName();
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

		// It should be over 15
		if (max < 10) return false;

		ClassNode classBlockState = getClassNode(maxClass);
		addClassMapping("net/minecraft/block/state/IBlockState",  classBlockState);

		return true;
	}

	
	@Mapping(provides={},
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
	public static boolean processIBlockStateClass()
	{
		ClassNode block = getClassNode(getClassMapping("net/minecraft/block/Block"));
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode iProperty = getClassNodeFromMapping("net/minecraft/block/properties/IProperty");
		if (block == null || iBlockState == null || iProperty == null) return false;
		
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
	public static boolean getIBlockAccessClass()
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
	public static boolean processIBlockAccessClass()
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
	public static boolean getBlockPosClass()
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
	public static boolean processBlockPosClass()
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
		
		
		methods = getMatchingMethods(blockPos, null, "()L" + blockPos.name + ";");
		if (methods.size() == 6) {
			for (int methodNum = 0; methodNum < 6; methodNum++) {
				AbstractInsnNode insn = findNextOpcodeNum(methods.get(methodNum).instructions.getFirst(), Opcodes.INVOKEVIRTUAL);
				if (insn == null) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (!mn.owner.equals(blockPos.name) || !mn.desc.equals("(I)L" + blockPos.name + ";")) continue;
				for (int dirNum = 0; dirNum < 6; dirNum++) {
					if (offsetMethods[dirNum].name.equals(mn.name)) {
						addMethodMapping("net/minecraft/util/BlockPos " + dirs[dirNum] + " ()Lnet/minecraft/util/BlockPos;",
								blockPos.name + " " + methods.get(methodNum).name + " " + methods.get(methodNum).desc);
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
	public static boolean processVec3iClass()
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
	public static boolean getEntityItemClass()
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
			"net/minecraft/block/BlockHopper"
			},
			providesFields={
			"net/minecraft/init/Blocks chest Lnet/minecraft/block/BlockChest;",
			"net/minecraft/init/Blocks dirt Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks obsidian Lnet/minecraft/block/Block;",
			"net/minecraft/init/Blocks hopper Lnet/minecraft/block/BlockHopper;",
			"net/minecraft/init/Blocks glass_pane Lnet/minecraft/block/Block;"
			},
			depends={
			"net/minecraft/init/Blocks",
			"net/minecraft/block/Block"})
	public static boolean processBlocksClass()
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
		
		FieldInsnNode field = blocksFields.get("chest");
		if (field != null) {
			addFieldMapping("net/minecraft/init/Blocks chest Lnet/minecraft/block/BlockChest;",
					field.owner + " " + field.name + " " + field.desc);
		}
		
		field = blocksFields.get("glass_pane");
		if (field != null) {
			addFieldMapping("net/minecraft/init/Blocks glass_pane Lnet/minecraft/block/Block;",
					field.owner + " " + field.name + " " + field.desc);
		}
		
		field = blocksFields.get("dirt");
		if (field != null) {
			addFieldMapping("net/minecraft/init/Blocks dirt Lnet/minecraft/block/Block;",
					field.owner + " " + field.name + " " + field.desc);
		}
		
		field = blocksFields.get("obsidian");
		if (field != null) {
			addFieldMapping("net/minecraft/init/Blocks obsidian Lnet/minecraft/block/Block;",
					field.owner + " " + field.name + " " + field.desc);
		}

		className = blocksClassFields.get("hopper");
		if (className != null && searchConstantPoolForStrings(className, "facing", "enabled")) {
			// TODO - Better detection
			addClassMapping("net/minecraft/block/BlockHopper", className);
		}
		field = blocksFields.get("hopper");
		if (field != null) {
			addFieldMapping("net/minecraft/init/Blocks hopper Lnet/minecraft/block/BlockHopper;",
					field.owner + " " + field.name + " " + field.desc);
		}
		
		
		return true;
	}

	
	@Mapping(providesMethods={}, depends={"net/minecraft/block/BlockHopper"})
	public static boolean processBlockHopperClass()
	{
		ClassNode blockHopper = getClassNodeFromMapping("net/minecraft/block/BlockHopper");
		if (!MeddleUtil.notNull(blockHopper)) return false;
				
		
		
		return true;
	}
	
	

	@Mapping(provides="net/minecraft/block/BlockLeavesBase",
			depends={
			"net/minecraft/block/Block", 
			"net/minecraft/block/BlockLeaves"
			})
	public static boolean getBlockLeavesBaseClass()
	{
		ClassNode blockLeaves = getClassNode(getClassMapping("net/minecraft/block/BlockLeaves"));
		ClassNode blockClass = getClassNode(getClassMapping("net/minecraft/block/Block"));
		if (blockClass == null || blockLeaves == null || blockLeaves.superName == null) return false;

		// BlockLeaves extends BlockLeavesBase
		ClassNode blockLeavesBase = getClassNode(blockLeaves.superName);
		if (blockLeavesBase == null || blockLeavesBase.superName == null) return false;

		// BlockLeavesBase should then extend Block, otherwise class hierarchy is unknown
		if (!blockClass.name.equals(blockLeavesBase.superName)) return false;

		addClassMapping("net/minecraft/block/BlockLeavesBase", blockLeavesBase);
		return true;
	}


	@Mapping(providesMethods={
			},
			depends={
			"net/minecraft/block/BlockSlab"
			})
	public static boolean processBlockSlabClass() 
	{
		ClassNode blockSlab = getClassNodeFromMapping("net/minecraft/block/BlockSlab");
		if (!MeddleUtil.notNull(blockSlab)) return false;
		
		
		
		return true;		
	}
	
	
	@Mapping(provides="net/minecraft/item/Item",
			depends={
			"net/minecraft/block/Block", 
			"net/minecraft/item/ItemStack"
			})
	public static boolean getItemClass()
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
	public static boolean getBlockContainerClass()
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
	public static boolean getTileEntityClass()
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
			"net/minecraft/tileentity/TileEntity createAndLoadEntity (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/tileentity/TileEntity;",
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
			"net/minecraft/tileentity/TileEntity readFromNBT (Lnet/minecraft/nbt/NBTTagCompound;)V",
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
	public static boolean processTileEntityClass()
	{
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
		methods = getMatchingMethods(tileEntity, null, "(L" + tagCompound.name + ";)L" + tileEntity.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/tileentity/TileEntity createAndLoadEntity (Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/tileentity/TileEntity;",
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
		methods = getMatchingMethods(tileEntity, null, "(L" + tagCompound.name + ";)V");
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
		}
		
		
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
	public static boolean processTileEntityChestClass()
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
	public static boolean processContainerChest()
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
	public static boolean getEntityPlayerClass()
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
	public static boolean getEntityLivingBaseClass()
	{
		ClassNode entityPlayerClass = getClassNode(getClassMapping("net/minecraft/entity/player/EntityPlayer"));
		if (entityPlayerClass == null || entityPlayerClass.superName == null) return false;

		if (!searchConstantPoolForStrings(entityPlayerClass.superName, "Health", "doMobLoot", "ai")) return false;

		addClassMapping("net/minecraft/entity/EntityLivingBase", getClassNode(entityPlayerClass.superName));
		return true;
	}


	@Mapping(provides="net/minecraft/entity/Entity", depends="net/minecraft/entity/EntityLivingBase")
	public static boolean getEntityClass()
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
	public static boolean processEntityClass()
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
	public static boolean getEntityListClass()
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
			"net/minecraft/entity/monster/EntityEnderman"
	},
	depends="net/minecraft/entity/EntityList")
	public static boolean parseEntityList()
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
				addClassMapping("net/minecraft/entity/monster/EntityZombie", getClassNode(zombieClass));
			}
		}

		String villagerClass = entityListClasses.get("Villager");
		if (villagerClass != null) {
			if (searchConstantPoolForStrings(villagerClass, "Profession", "entity.Villager.")) {
				addClassMapping("net/minecraft/entity/passive/EntityVillager", getClassNode(villagerClass));
			}
		}
		
		String sheepClass = entityListClasses.get("Sheep");
		if (sheepClass != null) {
			if (searchConstantPoolForStrings(sheepClass, "mob.sheep.shear")) {
				addClassMapping("net/minecraft/entity/passive/EntitySheep", getClassNode(sheepClass));
			}
		}
		
		String endermanClass = entityListClasses.get("Enderman");
		if (endermanClass != null) {
			if (searchConstantPoolForStrings(endermanClass, "carried", "mob.endermen.portal")) {
				addClassMapping("net/minecraft/entity/monster/EntityEnderman", getClassNode(endermanClass));
			}
		}		

		return true;

	}


	
	@Mapping(provides={
			}, 
			providesFields={
			"net/minecraft/entity/monster/EntityEnderman carriableBlocks Ljava/util/Set;"
			}, 
			providesMethods={
			}, 
			depends={
			"net/minecraft/entity/monster/EntityEnderman"
			})
	public static boolean processEntityEndermanClass()
	{
		ClassNode entityEnderman = getClassNodeFromMapping("net/minecraft/entity/monster/EntityEnderman");
		if (entityEnderman == null) return false;
		
		List<FieldNode> fields = getMatchingFields(entityEnderman, null, "Ljava/util/Set;");
		if (fields.size() == 1) {
			addFieldMapping("net/minecraft/entity/monster/EntityEnderman carriableBlocks Ljava/util/Set;",
					entityEnderman.name + " " + fields.get(0).name + " " + fields.get(0).desc);
		}
				
		
		// TODO
		/*for (FieldNode field : entityEnderman.fields) {
			Type t = Type.getType(field.desc);
			if (t.getSort() != Type.OBJECT) continue;
			System.out.println(t.getClassName());
		}*/
		
		return true;
	}
	
	
	
	
	
	@Mapping(provides={
			"net/minecraft/block/BlockAnvil",
			"net/minecraft/block/BlockDoor",
			"net/minecraft/block/BlockBed",
			"net/minecraft/block/BlockFenceGate",
			"net/minecraft/block/BlockPane",
			"net/minecraft/block/BlockDynamicLiquid",
			"net/minecraft/block/BlockStaticLiquid"
			},
			dependsMethods={
			"net/minecraft/block/Block registerBlocks ()V"
			},
			depends={
			"net/minecraft/block/Block"
			})
	public static boolean discoverBlocks()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		if (block == null) return false;
		
		MethodNode registerBlocks = getMethodNodeFromMapping(block, "net/minecraft/block/Block registerBlocks ()V");
		if (registerBlocks == null) return false;
		
		Map<String, String> blockClasses = new HashMap<>();
		
		String className;
		String blockName = null;
		
		// Create a map if the block names and their respective classes.
		// Note: May still just be the generic Block class.
		for (AbstractInsnNode insn = registerBlocks.instructions.getFirst(); insn != null; insn = insn.getNext()) 
		{
			String ldcString = getLdcString(insn);
			if (ldcString != null) { blockName = ldcString; continue; }
			
			if (blockName != null && insn.getOpcode() == Opcodes.NEW) {
				TypeInsnNode tn = (TypeInsnNode)insn;
				blockClasses.put(blockName, tn.desc);				
				blockName = null;
			}			
		}
		
		className = blockClasses.get("anvil");
		if (className != null && searchConstantPoolForStrings(className, "damage"))
		{
			// TODO - Better detection method
			addClassMapping("net/minecraft/block/BlockAnvil", className);
		}
		
		className = blockClasses.get("wooden_door");
		if (className != null && searchConstantPoolForStrings(className, "hinge")) {
			addClassMapping("net/minecraft/block/BlockDoor", className);
		}
		
		className = blockClasses.get("bed");
		if (className != null && searchConstantPoolForStrings(className, "occupied", "tile.bed.occupied")) {
			addClassMapping("net/minecraft/block/BlockBed", className);
		}
		
		className = blockClasses.get("fence_gate");
		if (className != null && searchConstantPoolForStrings(className, "in_wall")) {
			addClassMapping("net/minecraft/block/BlockFenceGate", className);
		}
		
		className = blockClasses.get("glass_pane");
		if (className != null && searchConstantPoolForStrings(className, "north", "south", "east", "west")) {
			addClassMapping("net/minecraft/block/BlockPane", className);
		}
		
		className = blockClasses.get("flowing_water");
		if (className != null) { // TODO - Better detection
			addClassMapping("net/minecraft/block/BlockDynamicLiquid", className);
		}
        
		className = blockClasses.get("water");
		if (className != null && searchConstantPoolForStrings(className, "doFireTick")) {
			addClassMapping("net/minecraft/block/BlockStaticLiquid", className);
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
	public static boolean processBlockPaneClass()
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
	public static boolean processBlockDynamicLiquidClass()
	{
		ClassNode dynamicLiquid = getClassNodeFromMapping("net/minecraft/block/BlockDynamicLiquid");
		if (!MeddleUtil.notNull(dynamicLiquid)) return false;
		
		String blockLiquid_name = dynamicLiquid.superName;
		if (searchConstantPoolForStrings(blockLiquid_name, "level", "random.fizz")) {
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
	public static boolean processBlockDoorClass()
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
	public static boolean processBlockDoorProperties()
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
	public static boolean getBlockDirectionalClass()
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
			"net/minecraft/block/properties/PropertyDirection create (Ljava/lang/String;)Lnet/minecraft/block/properties/PropertyDirection;"
			},
			depends={
			"net/minecraft/block/properties/PropertyDirection",
			"net/minecraft/block/properties/IProperty"
			})
	public static boolean processPropertyDirectionClass()
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
	public static boolean getIInteractionObjectClass()
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
			"net/minecraft/inventory/Slot"
			},
			providesFields={
			"net/minecraft/inventory/Container inventorySlots Ljava/util/List;"
			},
			providesMethods={
			"net/minecraft/inventory/Container addSlotToContainer (Lnet/minecraft/inventory/Slot;)Lnet/minecraft/inventory/Slot;",
			"net/minecraft/inventory/Container canInteractWith (Lnet/minecraft/entity/player/EntityPlayer;)Z",
			"net/minecraft/inventory/Container transferStackInSlot (Lnet/minecraft/entity/player/EntityPlayer;I)Lnet/minecraft/item/ItemStack;",
			"net/minecraft/inventory/Container onContainerClosed (Lnet/minecraft/entity/player/EntityPlayer;)V",
			"net/minecraft/inventory/Container getSlot (I)Lnet/minecraft/inventory/Slot;",
			"net/minecraft/inventory/Container mergeItemStack (Lnet/minecraft/item/ItemStack;IIZ)Z"
			},
			depends={
			"net/minecraft/inventory/Container",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/item/ItemStack"
			})
	public static boolean processContainerClass()
	{
		ClassNode container = getClassNodeFromMapping("net/minecraft/inventory/Container");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		if (container == null || entityPlayer == null || itemStack == null) return false;
		
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
	public static boolean processSlotClass()
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
			"net/minecraft/item/ItemDye"
			},
			providesMethods={
			"net/minecraft/item/Item registerItems ()V"
			},
			depends="net/minecraft/item/Item")
	public static boolean discoverItems()
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


		return true;
	}

	
	@Mapping(providesMethods={
			"net/minecraft/item/Item setHasSubtypes (Z)Lnet/minecraft/item/Item;"
			},
			providesFields={
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/item/ItemDye"
			})
	public static boolean processItemDyeClass()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode itemDye = getClassNodeFromMapping("net/minecraft/item/ItemDye");
		if (!MeddleUtil.notNull(item, itemDye)) return false;
		
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
	public static boolean getMaxDamageStuff()
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



	public static String assembleDescriptor(Object... objects)
	{
		String output = "";

		for (Object o : objects) {
			if (o instanceof String) output += (String)o;
			else if (o instanceof ClassNode) output += "L" + ((ClassNode)o).name + ";";
		}

		return output;
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
			"net/minecraft/block/Block BLOCK_STATE_IDS Lnet/minecraft/util/ObjectIntIdentityMap;"
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
	public static boolean processBlockClass()
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
			},
			depends={
			"net/minecraft/block/Block",
			"net/minecraft/item/Item",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/util/BlockPos",
			"net/minecraft/world/World",
			"net/minecraft/world/IBlockAccess"
			})
	public static boolean processBlockClass2()
	{
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode iBlockAccess = getClassNodeFromMapping("net/minecraft/world/IBlockAccess");
		if (!MeddleUtil.notNull(block, item, iBlockState, blockPos, world, iBlockAccess)) return false;
		
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
	public static boolean processBlockStateClass()
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
	public static boolean getCreativeTabs()
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
			"net/minecraft/item/Item onItemUse (Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/MainOrOffHand;Lnet/minecraft/util/EnumFacing;FFF)Lnet/minecraft/util/ItemUseResult;"
			},
			providesFields={
			"net/minecraft/item/Item maxStackSize I"
			},
			depends={
			"net/minecraft/item/Item",
			"net/minecraft/block/Block",
			"net/minecraft/item/ItemStack",
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/entity/EntityLivingBase"})
	public static boolean processItemClass()
	{
		ClassNode item = getClassNodeFromMapping("net/minecraft/item/Item");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode itemStack = getClassNodeFromMapping("net/minecraft/item/ItemStack");
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode entityLivingBase = getClassNodeFromMapping("net/minecraft/entity/EntityLivingBase");

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
			if (reverseClassMappings.containsKey(className)) continue;
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


		


		return true;
	}

	
	
	@Mapping(providesFields={
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
	public static boolean processEnumFacingClass()
	{
		ClassNode enumFacing = getClassNodeFromMapping("net/minecraft/util/EnumFacing");
		if (enumFacing == null) return false;	
		
		List<FieldNode> fields = getMatchingFields(enumFacing, null, "L" + enumFacing.name + ";");
		if (fields.size() == 6) {
			
			String[] names = new String[] { "DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST" };
			
			for (int n = 0; n < 6; n++) {				
				addFieldMapping("net/minecraft/util/EnumFacing " + names[n] + " Lnet/minecraft/util/EnumFacing;",
						enumFacing.name + " " + fields.get(n).name + " " + fields.get(n).desc);
			}
		}
		
		return true;
	}
	
	
	

	@Mapping(providesFields={
			"net/minecraft/world/World isRemote Z"
			},
			providesMethods={
			"net/minecraft/world/World setBlockState (Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
			"net/minecraft/world/World markBlockForUpdate (Lnet/minecraft/util/BlockPos;)V",
			"net/minecraft/world/World markBlockRangeForRenderUpdate (Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/BlockPos;)V",
			"net/minecraft/world/World playAuxSFXAtEntity (Lnet/minecraft/entity/player/EntityPlayer;ILnet/minecraft/util/BlockPos;I)V",
			"net/minecraft/world/World addBlockEvent (Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/Block;II)V",
			"net/minecraft/block/Block onBlockEventReceived (Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;II)Z",
			"net/minecraft/world/World setTileEntity (Lnet/minecraft/util/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
			"net/minecraft/world/World markChunkDirty (Lnet/minecraft/util/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V"
			},
			depends={
			"net/minecraft/world/World",
			"net/minecraft/util/BlockPos",
			"net/minecraft/block/state/IBlockState",
			"net/minecraft/entity/player/EntityPlayer",
			"net/minecraft/block/Block",
			"net/minecraft/tileentity/TileEntity"
			})
	public static boolean processWorldClass()
	{
		ClassNode world = getClassNodeFromMapping("net/minecraft/world/World");
		ClassNode blockPos = getClassNodeFromMapping("net/minecraft/util/BlockPos");
		ClassNode iBlockState = getClassNodeFromMapping("net/minecraft/block/state/IBlockState");
		ClassNode entityPlayer = getClassNodeFromMapping("net/minecraft/entity/player/EntityPlayer");
		ClassNode block = getClassNodeFromMapping("net/minecraft/block/Block");
		ClassNode tileEntity = getClassNodeFromMapping("net/minecraft/tileentity/TileEntity");
		if (!MeddleUtil.notNull(world, blockPos, iBlockState, entityPlayer, block, tileEntity)) return false;

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
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/world/World setBlockState (Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
					world.name + " " + methods.get(0).name + " " + methods.get(0).desc);
			
			
			// public void markBlockForUpdate(BlockPos pos)
			List<MethodInsnNode> nodes = new ArrayList<>();
			for (AbstractInsnNode insn = methods.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
				MethodInsnNode mn = (MethodInsnNode)insn;
				if (mn.owner.equals(world.name) && mn.desc.equals("(L" + blockPos.name + ";)V")) {
					nodes.add(mn);
				}
			}
			
			if (nodes.size() == 1) {
				addMethodMapping("net/minecraft/world/World markBlockForUpdate (Lnet/minecraft/util/BlockPos;)V",
						world.name + " " + nodes.get(0).name + " " + nodes.get(0).desc);
			}
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
	public static boolean processBlockChestClass()
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
	public static boolean processIWorldNameable()
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
	public static boolean getChatComponentTextClass()
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
	public static boolean processEntityVillagerClass()
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
	public static boolean processEntityPlayerClass()
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



	public static AbstractInsnNode getNextRealOpcode(AbstractInsnNode insn)
	{
		while (insn != null && insn.getOpcode() < 0) insn = insn.getNext();
		return insn;
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
	public static boolean getNBTClasses()
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
			"net/minecraft/nbt/NBTTagCompound setInteger (Ljava/lang/String;I)V"
	},
	providesFields={
			"net/minecraft/nbt/NBTTagList tagType B"
	},
	depends={
			"net/minecraft/nbt/NBTTagCompound",
			"net/minecraft/nbt/NBTTagList",
			"net/minecraft/nbt/NBTBase"
	})
	public static boolean processNBTTagCompound()
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
	public static boolean processInventoryBasicClass()
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
	public static boolean getItemClassMethods()
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
	public static boolean processItemStackClass()
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

	
	
	
	

	@Mapping(providesFields={
			"net/minecraft/util/ItemUseResult SUCCESS Lnet/minecraft/util/ItemUseResult;",
			"net/minecraft/util/ItemUseResult PASS Lnet/minecraft/util/ItemUseResult;",
			"net/minecraft/util/ItemUseResult FAIL Lnet/minecraft/util/ItemUseResult;"
			},
			depends={
			"net/minecraft/util/ItemUseResult"
			})
	public static boolean processItemUseResultClass()
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
	public static boolean getCraftingManagerClass() 
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
			"net/minecraft/item/crafting/ShapedRecipes"
			},
			providesMethods={
			"net/minecraft/item/crafting/CraftingManager addRecipe (Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)Lnet/minecraft/item/crafting/ShapedRecipes;",
			"net/minecraft/item/crafting/CraftingManager addShapelessRecipe (Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
			"net/minecraft/item/crafting/CraftingManager getInstance ()Lnet/minecraft/item/crafting/CraftingManager;"
			},
			depends={
			"net/minecraft/item/crafting/CraftingManager",
			"net/minecraft/item/ItemStack"
			})
	public static boolean processCraftingManagerClass() 
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
		
		
		// public void addShapelessRecipe(ItemStack param0, Object... param1)
		methods = getMatchingMethods(craftingManager, null, "(L" + itemStack.name + ";[Ljava/lang/Object;)V");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/crafting/CraftingManager addShapelessRecipe (Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
					craftingManager.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		// public static CraftingManager getInstance()
		methods = getMatchingMethods(craftingManager, null, "()L" + craftingManager.name + ";");
		if (methods.size() == 1) {
			addMethodMapping("net/minecraft/item/crafting/CraftingManager getInstance ()Lnet/minecraft/item/crafting/CraftingManager;",
					craftingManager.name + " " + methods.get(0).name + " " + methods.get(0).desc);
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
	public static boolean getInitItemsClass() 
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
			"net/minecraft/init/Items leather Lnet/minecraft/item/Item;"
			},
			depends={
			"net/minecraft/init/Items",
			"net/minecraft/item/Item"
			})
	public static boolean discoverItemsFields()
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
	public static boolean findServerCommandManagerClass()
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
	public static boolean processServerCommandManagerClass()
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
	public static boolean processCommandHandlerClass()
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
	public static boolean processICommandClass()
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
			"net/minecraft/command/ICommandSender setCommandStat (Lnet/minecraft/command/CommandResultStats$Type;)V"
			},
			depends={
			"net/minecraft/command/ICommandSender",
			"net/minecraft/util/IChatComponent",
			"net/minecraft/util/BlockPos",
			"net/minecraft/util/Vec3",
			"net/minecraft/world/World",
			"net/minecraft/entity/Entity"
			})
	public static boolean processICommandSenderClass()
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
			addMethodMapping("net/minecraft/command/ICommandSender setCommandStat (Lnet/minecraft/command/CommandResultStats$Type;)V",
					iCommandSender.name + " " + methods.get(0).name + " " + methods.get(0).desc);
		}
		
		
		return true;
	}


	@Mapping(provides="net/minecraft/entity/player/EntityPlayerMP", depends="net/minecraft/server/MinecraftServer")
	public static boolean getEntityPlayerMPClass()
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
	public static boolean processEntityPlayerMPClass()
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
						if (searchConstantPoolForStrings(mn.owner, "keepAlive", "Illegal position", "Sending packet")) {
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
			},
			depends={
			"net/minecraft/util/AxisAlignedBB"
			})
	public static boolean processAxisAlignedBBClass()
	{
		ClassNode aabb = getClassNodeFromMapping("net/minecraft/util/AxisAlignedBB");
		if (!MeddleUtil.notNull(aabb)) return false;	
		
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
	public static boolean processRegistryNamespacedClass()
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
	
	
    
}

