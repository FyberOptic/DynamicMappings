package net.fybertech.dynamicmappings;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

import net.fybertech.dynamicmappings.mappings.ClientMappings;
import net.fybertech.dynamicmappings.mappings.SharedMappings;
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

	// If set to true, fake mappings populate the maps.
	// Used when determining all possible mappings without needing a Minecraft jar.
	public static boolean simulatedMappings = false;
	
	
	
	public static void generateClassMappings()
	{		
		DynamicMappings.registerMappingsClass(SharedMappings.class);
		DynamicMappings.registerMappingsClass(ClientMappings.class);
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
	

	// Parses a class for dynamic mappings.
	// Methods must implement the @Mapping annotation.
	// 
	// Class may optionally use @MappingsClass annotation if it includes client
	// or sever-side mappings.
	public static boolean registerMappingsClass(Class<? extends Object> mappingsClass)
	{
		List<MappingMethod> mappingMethods = new ArrayList<MappingMethod>();	
		
		boolean clientSide = false;
		boolean serverSide = false;
		
		if (mappingsClass.isAnnotationPresent(MappingsClass.class)) {
			MappingsClass mc = mappingsClass.getAnnotation(MappingsClass.class);
			clientSide = mc.clientSide();
			serverSide = mc.serverSide();
		}
		
		
		if (!simulatedMappings && clientSide && !MeddleUtil.isClientJar()) {
			LOGGER.info("[DynamicMappings] Ignoring client-side class " + mappingsClass.getName());
			return false;
		}
		
		if (!simulatedMappings && serverSide && MeddleUtil.isClientJar()) {
			LOGGER.info("[DynamicMappings] Ignoring server-side class " + mappingsClass.getName());
			return false;
		}
		
		LOGGER.info("[DynamicMappings] Processing class " + mappingsClass.getName());
		
		
		for (Method method : mappingsClass.getMethods())
		{
			if (!method.isAnnotationPresent(Mapping.class)) continue;
			Mapping mapping = method.getAnnotation(Mapping.class);
			mappingMethods.add(new MappingMethod(method, mapping));
		}
		
		Object mappingsObject = null;
		try {
			Constructor<? extends Object> mappingsConstructor = mappingsClass.getConstructor(new Class[0]);
			mappingsObject = mappingsConstructor.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		

		while (true)
		{
			int startSize = mappingMethods.size();
			for (Iterator<MappingMethod> it = mappingMethods.iterator(); it.hasNext();)
			{
				MappingMethod mm = it.next();
				boolean isStatic = Modifier.isStatic(mm.method.getModifiers());

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
						if (isStatic) mm.method.invoke(null);
						else mm.method.invoke(mappingsObject, (Object[])null);
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
	

	public static AbstractInsnNode getNextRealOpcode(AbstractInsnNode insn)
	{
		while (insn != null && insn.getOpcode() < 0) insn = insn.getNext();
		return insn;
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
	
    
}

