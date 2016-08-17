package net.fybertech.dynamicmappings;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
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

import net.fybertech.dynamicmappings.mappers.ClientMappings;
import net.fybertech.dynamicmappings.mappers.SharedMappings;
import net.fybertech.meddle.MeddleUtil;


public class DynamicMappings
{	
	public static final Logger LOGGER = LogManager.getLogger("Meddle");

	/** Deobfuscated class -> obfuscated class */
	public static final Map<String, String> classMappings = new HashMap<String, String>();
	/** Obfuscated class -> deobfuscated class */
	public static final Map<String, String> reverseClassMappings = new HashMap<String, String>();

	/** Deobfuscated field -> obfuscated field */
	public static final Map<String, String> fieldMappings = new HashMap<String, String>();
	/** Obfuscated field -> deobfuscated field */
	public static final Map<String, String> reverseFieldMappings = new HashMap<String, String>();

	/** Deobfuscated method -> obfuscated method */
	public static final Map<String, String> methodMappings = new HashMap<String, String>();
	/** Obfuscated method -> deobfuscated method */
	public static final Map<String, String> reverseMethodMappings = new HashMap<String, String>();

	/** Only used when simulatedMappings is enabled, to help with ModMappings */
	public static final Set<String> clientMappingsSet = new HashSet<>();
	/** Only used when simulatedMappings is enabled, to help with ModMappings */
	public static final Set<String> serverMappingsSet = new HashSet<>();
	
	/** Used by getClassNode to avoid reloading the classes over and over */
	private static Map<String, ClassNode> cachedClassNodes = new HashMap<String, ClassNode>();

	
	/**
	 * If set to true, fake mappings populate the map objects above, ignoring whether
	 * they can actually be located in the Minecraft code.
	 * 
	 * Used by ModMappings when determining all possible mappings provided without 
	 * needing a Minecraft jar to scan (since ModMappings needs a deobfuscated one, 
	 * which DynamicMappings can't process properly.)
	 */	
	public static boolean simulatedMappings = false;
	
	
	
	/**
	 * Called to initialize all mappings.
	 */
	public static void generateClassMappings()
	{
		generateClassLinkages();
		DynamicMappings.registerMappingsClass(SharedMappings.class);
		DynamicMappings.registerMappingsClass(ClientMappings.class);
	}
	
	

	/**
	 * Stores relevant information about a mapper method in one structure.
	 */
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
	
	
	
	/**
	 * Parses a class for dynamic mappings.
	 * Methods adding mappings should use the @Mapping annotation.
	 * 
	 * The class may optionally use @MappingsClass annotation if it includes 
	 * client or sever-side mappings.
	 * 
	 * @param mappingsClass - The class to process for mapper methods.
	 * @return True if all mappings were successfully discovered.
	 */	
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
			System.out.println("[DynamicMappings] Ignoring client-side class " + mappingsClass.getName());
			return false;
		}
		
		if (!simulatedMappings && serverSide && MeddleUtil.isClientJar()) {
			System.out.println("[DynamicMappings] Ignoring server-side class " + mappingsClass.getName());
			return false;
		}
		
		System.out.println("[DynamicMappings] Processing class " + mappingsClass.getName());
		
		
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
					for (String s : mm.provides) {
						classMappings.put(s, "---");
						if (clientSide) clientMappingsSet.add(s);
						if (serverSide) serverMappingsSet.add(s);
					}
					for (String s : mm.providesFields) {
						fieldMappings.put(s, "--- --- ---");
						if (clientSide) clientMappingsSet.add(s);
						if (serverSide) serverMappingsSet.add(s);
					}
					for (String s : mm.providesMethods) {
						methodMappings.put(s, "--- --- ---");
						if (clientSide) clientMappingsSet.add(s);
						if (serverSide) serverMappingsSet.add(s);
					}
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
					System.out.println("  Mapper Method: " + mm.method.getName());
					for (String depend : mm.depends) {
						if (!classMappings.keySet().contains(depend)) System.out.println("    Class: " + depend);						
					}
					for (String depend : mm.dependsFields) {
						if (!fieldMappings.keySet().contains(depend)) System.out.println("    Field: " + depend);
					}
					for (String depend : mm.dependsMethods) {
						if (!methodMappings.keySet().contains(depend)) System.out.println("    Method: " + depend);
					}
				}
				return false;
			}
		}
	}
	
	
	public static Map<String, Set<String>> classDeps = new HashMap<>();
	public static Map<String, Set<String>> classesExtendFrom = new HashMap<>();
	public static Map<String, Set<String>> classesImplementFrom = new HashMap<>();

	static String[] classSearchExceptions = new String[] { "java/", "javax/", "sun/", "com/google/", 
			"org/apache/", "com/sun/", "io/netty/", "jdk/internal/", "org/xml/", 
			"org/w3c/", "jdk/net/", "com/ibm/", "org/lwjgl/", "com/jcraft/",
			"joptsimple/", "net/java/", "paulscode/", "com/mojang/"};
	
	public static void getConstantPoolClassesRecursive(String className)
	{
		if (startsWithAny(className, classSearchExceptions)) return;
		
		ClassReader reader = null;
		try {
			reader = new ClassReader(className);
		} catch (IOException e) {}
		if (reader != null) {
			String superName = reader.getSuperName();
			Set<String> extendSet = classesExtendFrom.get(superName);
			if (extendSet == null) { extendSet = new HashSet<>(); classesExtendFrom.put(superName, extendSet); }
			extendSet.add(className);			
			
			for (String iface : reader.getInterfaces()) {
				Set<String> implementSet = classesImplementFrom.get(iface);
				if (implementSet == null) { implementSet = new HashSet<>(); classesImplementFrom.put(iface, implementSet); }
				implementSet.add(className);
			}
		}
		
		Set<String> classes = classDeps.get(className);
		if (classes == null) {
			classes = getConstantPoolClasses(className, true);
			if (classes == null) return;
			classDeps.put(className, classes);
		}			
		//System.out.println(className + " " + (classes != null));
		for (String s : classes) {
			if (!classDeps.containsKey(s)) getConstantPoolClassesRecursive(s);
		}		
	}
	
	
	public static boolean startsWithAny(String string, String[] list) 
	{
		for (String s : list) {
			if (string.startsWith(s)) return true;
		}
		return false;
	}
	
	
	public static Set<String> getChildClasses(String className) 
	{
		Set<String> outSet = new HashSet<>();
		Set<String> tempSet = new HashSet<>();
		if (classesExtendFrom.containsKey(className)) tempSet.addAll(classesExtendFrom.get(className));		
		if (classesImplementFrom.containsKey(className)) tempSet.addAll(classesImplementFrom.get(className));
		outSet.addAll(tempSet);
		
		for (String s : tempSet) {
			outSet.addAll(getChildClasses(s));
		}		
		return outSet;
	}
	
	
	public static void generateClassLinkages()
	{
		System.out.print("[DynamicMappings] Generating linkages...");
		getConstantPoolClassesRecursive("net/minecraft/server/MinecraftServer");
		getConstantPoolClassesRecursive("net/minecraft/client/main/Main");
		System.out.println("done");
	}
	

	
	public static void log(boolean toConsole, PrintWriter writer, String text)
	{
		if (toConsole) System.out.println(text);
		if (writer != null) writer.println(text);
	}
	
	
	/** Used for debugging purposes, and to print out a full list 
	 * @throws FileNotFoundException */
	public static void main(String[] args)
	{
		// If true, prints out the mappings
		boolean showMappings = false;		
		// If true, saves mappings to currentmappings.txt
		boolean saveMappings = true;
		
		PrintWriter writer = null;
		
		try {
			if (saveMappings) writer = new PrintWriter("currentmappings.txt");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}	
		
		generateClassMappings();	

		log(true, writer, "[DynamicMappings] Minecraft version: " + MeddleUtil.findMinecraftVersion());
		log(true, writer, "[DynamicMappings] Minecraft jar type: " + (MeddleUtil.isClientJar() ? "client" : "server"));		

		if (!showMappings && !saveMappings) return;	

		log(showMappings, writer, "\nCLASSES:");	

		List<String> sorted = new ArrayList<String>();
		sorted.addAll(classMappings.keySet());
		Collections.sort(sorted);
		for (String s : sorted) {
			log(showMappings, writer, s + " -> " + classMappings.get(s));
		}

		log(showMappings, writer, "\nFIELDS:");

		sorted.clear();
		sorted.addAll(fieldMappings.keySet());
		Collections.sort(sorted);
		for (String s : sorted) {
			log(showMappings, writer, s + " -> " + fieldMappings.get(s));
		}

		log(showMappings, writer, "\nMETHODS:");

		sorted.clear();
		sorted.addAll(methodMappings.keySet());
		Collections.sort(sorted);
		for (String s : sorted) {
			log(showMappings, writer, s + " -> " + methodMappings.get(s));
		}
		
		writer.close();
	}
	
	
	
	/**
	 * Load a ClassNode by its name.  This is for loading the original obfuscated 
	 * classes.
	 * 
	 * Note: *Do not* edit classes you get from this.  They're cached and used by
	 * anyone doing analysis of vanilla class files.
	 * 
	 * @param className - The normal (probably obfuscated) name of the class to load.
	 * @return The ClassNode requested, or null if it doesn't exist.
	 */	
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

		
	/**
	 * Get constant pool string that an LDC instruction is 
	 * loading.
	 * 
	 * @param node The instruction node to check.
	 * @return Returns the string if the LDC is accessing a String
	 * constant pool item, else null.
	 */
	public static String getLdcString(AbstractInsnNode node)
	{
		if (!(node instanceof LdcInsnNode)) return null;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof String)) return null;
		return new String((String)ldc.cst);
	}


	/**
	 * Get constant pool class that an LDC instruction is 
	 * referencing.
	 * 
	 * @param node - The instruction node to check.
	 * @return Returns the class name if the LDC is accessing a 
	 * Class constant pool item, else null.
	 */
	public static String getLdcClass(AbstractInsnNode node)
	{
		if (!(node instanceof LdcInsnNode)) return null;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof Type)) return null;
		return ((Type)ldc.cst).getClassName();
	}


	/**
	 * Check if an LDC instruction is loading the specified string.
	 * 
	 * @param node The instruction node to check
	 * @param string The string to compare to.
	 * @return Returns true if the instruction is an LDC, is accessing
	 * a String constant pool item, and the string matches the input string.
	 */
	public static boolean isLdcWithString(AbstractInsnNode node, String string)
	{
		String s = getLdcString(node);
		return (s != null && string.equals(s));
	}


	/**
	 * Check if an LDC instruction is loading specified int value.
	 * 
	 * @param node The instruction node to check.
	 * @param val The integer to compare to.
	 * @return Returns true if the instruction is an LDC, is accessing
	 * a Integer constant pool item, and its value matches the input value.
	 */
	public static boolean isLdcWithInteger(AbstractInsnNode node, int val)
	{
		if (!(node instanceof LdcInsnNode)) return false;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof Integer)) return false;
		return ((Integer)ldc.cst) == val;
	}
	
	
	/**
	 * Check if an LDC instruction is loading specified float value.
	 * 
	 * @param node The instruction node to check.
	 * @param val The integer to compare to.
	 * @return Returns true if the instruction is an LDC, is accessing
	 * a Float constant pool item, and its value matches the input value.
	 */
	public static boolean isLdcWithFloat(AbstractInsnNode node, float val)
	{
		if (!(node instanceof LdcInsnNode)) return false;
		LdcInsnNode ldc = (LdcInsnNode)node;
		if (!(ldc.cst instanceof Float)) return false;
		return ((Float)ldc.cst) == val;
	}


	/**
	 * Get the description of the specified field from a class.
	 * 
	 * @param cn - The class to search.
	 * @param fieldName - The name of the field.
	 * @return The description of the field, or null if not found.
	 */
	public static String getFieldDesc(ClassNode cn, String fieldName)
	{
		for (FieldNode field : cn.fields)
		{
			if (field.name.equals(fieldName)) return field.desc;
		}
		return null;
	}


	/**
	 * Get the specified field node from the class.
	 * 
	 * @param cn - The class to search
	 * @param fieldName - The name of the field to find.
	 * @return The FieldNode if present, else null.
	 */
	public static FieldNode getFieldByName(ClassNode cn, String fieldName)
	{
		for (FieldNode field : cn.fields)
		{
			if (field.name.equals(fieldName)) return field;
		}
		return null;
	}


	/**
	 * Searches a class's constant pool for the specified list of strings.
	 * 
	 * NOTE: Constant pool strings are trimmed of whitespace!  Take into 
	 * account when matching.
	 * 
	 * @param className - The name of the class to search.
	 * @param matchStrings - The list of strings to find.
	 * @return True if all strings were found.
	 */
	public static boolean searchConstantPoolForStrings(String className, String... matchStrings)
	{
		if (className == null) return false;
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
			if (pos == 0 || reader.b[pos - 1] != 8) continue;

			Arrays.fill(buffer, (char)0);
			String string = reader.readUTF8(pos,  buffer).trim();
			//String string = (new String(buffer)).trim();

			for (int n2 = 0; n2 < matchStrings.length; n2++) {
				if (string.equals(matchStrings[n2].trim())) { matches++; break; }
			}
		}

		return (matches == matchStrings.length);
	}

	
	/**
	 * Searches a class's constant pool for the specified list of class 
	 * references.
	 * 	
	 * @param className - The name of the class to search.
	 * @param matchStrings - The list of class names to find.
	 * @return True if all class names were found.
	 */
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
			String string = reader.readUTF8(pos,  buffer);
			//String string = (new String(buffer)).trim();

			for (int n2 = 0; n2 < matchStrings.length; n2++) {
				if (string.equals(matchStrings[n2].replace(".", "/"))) { matches++; break; }
			}
		}

		return (matches == matchStrings.length);
	}


	/**
	 * Returns a list of the 'String' types from the class's constant pool.
	 * 
	 * @param className - Name of class to search.
	 * @return The list of constant pool strings.
	 */
	public static List<String> getConstantPoolStrings(String className)
	{
		List<String> strings = new ArrayList<String>();

		className = className.replace(".", "/");
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
			String string = reader.readUTF8(pos,  buffer);
			//String string = (new String(buffer)).trim();

			strings.add(string);
		}

		return strings;
	}

	
	/**
	 * Returns a set of the 'Class' types from the class's constant pool.
	 * 
	 * @param className - Name of class to search.
	 * @return The list of constant pool strings.
	 */
	public static Set<String> getConstantPoolClasses(String className, boolean processArrays)
	{
		Set<String> strings = new HashSet<String>();

		className = className.replace(".", "/");
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
			if (pos == 0 || reader.b[pos - 1] != 7) continue;

			Arrays.fill(buffer, (char)0);
			String string = reader.readUTF8(pos,  buffer);
			//String string = (new String(buffer)).trim();
			
			if (string.startsWith("[") && processArrays) {
				string = ModMappings.getArrayType(string);
				if (string == null) continue;
			}
			if (string.length() < 1) continue;

			strings.add(string);
		}

		return strings;
	}
	

	/**
	 * Confirm the parameter types of a method's description.
	 * 
	 * Uses org.objectweb.asm.Type for values.
	 * 
	 * @param method - MethodNode to check.
	 * @param types - Sequence of method parameter types.
	 * @return True if the method description matches specified types.
	 */
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


	/**
	 * Finds all methods matching the specified name and/or description.
	 * 
	 * @param cn - ClassNode to search.
	 * @param name - Optional name of method(s) to find.
	 * @param desc - Optional description of method(s) to find.
	 * @return List of matching methods.
	 */
	public static List<MethodNode> getMatchingMethods(ClassNode cn, String name, String desc)
	{
		List<MethodNode> output = new ArrayList<MethodNode>();

		for (MethodNode method : cn.methods) {
			if ((name == null || (name != null && method.name.equals(name))) &&
					(desc == null || (desc != null && method.desc.equals(desc)))) output.add(method);
		}

		return output;
	}


	/**
	 * Finds all methods matching the specified return and argument types
	 *
	 * @param cn - ClassNode to search.
	 * @param accessCode - The Opcode for the access of the method.
	 * @param returnType - The return type as integer
	 * @param parameterTypes - The arguments of the method as integer. Can be null if the method haven't arguments
	 * @return List of matching methods.
	 * @author canitzp
	 */
	public static List<MethodNode> getMatchingMethods(ClassNode cn, int accessCode, int returnType, int... parameterTypes){
		List<MethodNode> methodNodes = new ArrayList<>();

		for(MethodNode method : cn.methods){
			Type rt = Type.getReturnType(method.desc);
			Type[] params = Type.getArgumentTypes(method.desc);
			if(returnType == rt.getSort() && (method.access & accessCode) != 0){
				if(parameterTypes != null){
					if(parameterTypes.length == params.length){
						boolean isSame = true;
						for(int i = 0; i < parameterTypes.length; i++){
							if(parameterTypes[i] != params[i].getSort()){
								isSame = false;
								break;
							}
						}
						if(isSame){
							methodNodes.add(method);
						}
					}
				} else if(params.length == 0){
					methodNodes.add(method);
				}
			}
		}

		return methodNodes;
	}
	
	/**
	 * Finds all fields matching the specified name and/or description.
	 * 
	 * @param cn - ClassNode to search.
	 * @param name - Optional name of field(s) to find.
	 * @param desc - Optional description of field(s) to find.
	 * @return List of matching fields.
	 */
	public static List<FieldNode> getMatchingFields(ClassNode cn, String name, String desc)
	{
		List<FieldNode> output = new ArrayList<FieldNode>();

		for (FieldNode field : cn.fields) {
			if ((name == null || (name != null && field.name.equals(name))) &&
					(desc == null || (desc != null && field.desc.equals(desc)))) output.add(field);
		}

		return output;
	}


	/**
	 * Gets a ClassNode after translating the specified deobfuscated class name to 
	 * the obfuscated name.
	 * 
	 * @param deobfClass - Class name to translate and get.
	 * @return The ClassNode, or null if it doesn't exist.
	 */
	public static ClassNode getClassNodeFromMapping(String deobfClass)
	{
		return getClassNode(getClassMapping(deobfClass));
	}


	/**
	 * Checks if the sequence of opcodes exists, starting at the specified 
	 * instruction.  This ignores synthetic opcodes added by ASM, such as 
	 * labels.
	 * 
	 * @param insn - Instruction node to start at.
	 * @param opcodes - Sequence of opcodes to look for.
	 * @return True if all opcodes are found and in the specifed order.
	 */
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
	

	/**
	 * Identifies and extracts the specified opcode sequence as an array of
	 * instruction nodes.  Ignores synthetic opcodes added by ASM, such as 
	 * labels.
	 * 
	 * @param insn - Instruction to start at.
	 * @param opcodes - Sequence of opcodes to look for.
	 * @return The list of instruction nodes, if one is found matching the
	 * specified sequence. 
	 */
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
	public static boolean matchInsnNodeSequence(AbstractInsnNode insn, Class<? extends AbstractInsnNode>...nodeClasses)
	{
		for (Class<? extends AbstractInsnNode> nodeClass : nodeClasses) {
			insn = getNextRealOpcode(insn);
			if (insn == null) return false;
			if (nodeClass != insn.getClass()) return false;		
			insn = insn.getNext();
		}

		return true;
	}
	
	
	@SuppressWarnings("unchecked")
	public static AbstractInsnNode[] getInsnNodeSequenceArray(AbstractInsnNode insn, Class<? extends AbstractInsnNode>...nodeClasses)
	{
		AbstractInsnNode[] outNodes = new AbstractInsnNode[nodeClasses.length];
		int pos = 0;
		
		for (Class<? extends AbstractInsnNode> nodeClass : nodeClasses) {
			insn = getNextRealOpcode(insn);
			if (insn == null) return null;
			if (nodeClass != insn.getClass()) return null;
			outNodes[pos++] = insn;
			insn = insn.getNext();
		}

		return outNodes;
	}
	
	
	
	
	
	/**
	 * Gets a list of all instruction nodes matching the specified class.
	 * 
	 * @param startInsn - Instruction node to start at.
	 * @param classType - Class to compare against.
	 * @return The list of matching nodes.
	 */
	@SuppressWarnings("unchecked")
	public static <T> List<T> getAllInsnNodesOfType(AbstractInsnNode startInsn, Class<T> classType)
	{				
		List<T> list = new ArrayList<>();
		
		for (AbstractInsnNode insn = startInsn; insn != null; insn = insn.getNext()) {
			if (insn.getClass() == classType) list.add((T)insn);
		}

		return list;
	}
	
	
	/**
	 * Gets the next matching instruction node matching the specified class.
	 * 
	 * @param startInsn - Instruction node to start at.
	 * @param classType - Class to compare against.
	 * @return The first matching instruction node, or null if none.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getNextInsnNodeOfType(AbstractInsnNode startInsn, Class<T> classType)
	{		
		for (AbstractInsnNode insn = startInsn; insn != null; insn = insn.getNext()) {
			if (insn.getClass() == classType) return (T) insn;
		}

		return null;
	}
	


	/**
	 * Gets the obfuscated class name from a deobfuscated input.
	 * 
	 * @param deobfClassName - The deobfuscated class name.
	 * @return The obfuscated class name, or null
	 */
	public static String getClassMapping(String deobfClassName)
	{
		return classMappings.get(deobfClassName.replace(".",  "/"));
	}
	

	/**
	 * Gets the deobfuscated class name from an obfuscated input.
	 * 
	 * @param obfClassName - The obfuscated class name.
	 * @return The deobfuscated class name, or null
	 */
	public static String getReverseClassMapping(String obfClassName)
	{
		return reverseClassMappings.get(obfClassName.replace(".",  "/"));
	}


	/**
	 * Adds a class mapping.
	 * 
	 * @param deobfClassName - The deobfuscated class name.
	 * @param node - The obfuscated ClassNode.
	 */
	public static void addClassMapping(String deobfClassName, ClassNode node)
	{
		if (deobfClassName == null) return;
		deobfClassName = deobfClassName.replace(".", "/");
		addClassMapping(deobfClassName, node.name);
	}

	
	/**
	 * Adds a class mapping.
	 * 
	 * @param deobfClassName - The deobfuscated class name.
	 * @param obfClassName - The obfuscated class name.
	 */
	public static void addClassMapping(String deobfClassName, String obfClassName)
	{
		deobfClassName = deobfClassName.replace(".", "/");
		obfClassName = obfClassName.replace(".", "/");
		
		if (classMappings.containsKey(deobfClassName) && !classMappings.get(deobfClassName).equals(obfClassName))
			System.out.println("WARNING: " + deobfClassName + " has been remapped from " + classMappings.get(deobfClassName) + " to " + obfClassName);
		if (reverseClassMappings.containsKey(obfClassName) && !reverseClassMappings.get(obfClassName).equals(deobfClassName))
			System.out.println("WARNING: " + obfClassName + " has been remapped from " + reverseClassMappings.get(obfClassName) + " to " + deobfClassName);
		
		classMappings.put(deobfClassName, obfClassName);
		reverseClassMappings.put(obfClassName, deobfClassName);
	}

	
	// Both inputs in the format of "class_name method_name method_desc"
	public static void addMethodMapping(String deobfMethodDesc, String obfMethodDesc)
	{
		if (classMappings.containsKey(deobfMethodDesc) && !classMappings.get(deobfMethodDesc).equals(obfMethodDesc))
			System.out.println("WARNING: " + deobfMethodDesc + " has been remapped from " + classMappings.get(deobfMethodDesc) + " to " + obfMethodDesc);
		if (reverseClassMappings.containsKey(obfMethodDesc) && !reverseClassMappings.get(obfMethodDesc).equals(deobfMethodDesc))
			System.out.println("WARNING: " + obfMethodDesc + " has been remapped from " + reverseClassMappings.get(obfMethodDesc) + " to " + deobfMethodDesc);
		
		methodMappings.put(deobfMethodDesc, obfMethodDesc);
		reverseMethodMappings.put(obfMethodDesc, deobfMethodDesc);
	}
	

	// Both inputs in the format of "class_name field_name field_desc"
	public static void addFieldMapping(String deobfFieldDesc, String obfFieldDesc)
	{
		if (classMappings.containsKey(deobfFieldDesc) && !classMappings.get(deobfFieldDesc).equals(obfFieldDesc))
			System.out.println("WARNING: " + deobfFieldDesc + " has been remapped from " + classMappings.get(deobfFieldDesc) + " to " + obfFieldDesc);
		if (reverseClassMappings.containsKey(obfFieldDesc) && !reverseClassMappings.get(obfFieldDesc).equals(deobfFieldDesc))
			System.out.println("WARNING: " + obfFieldDesc + " has been remapped from " + reverseClassMappings.get(obfFieldDesc) + " to " + deobfFieldDesc);
		
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
	
	
	public static FieldNode getFieldNode(ClassNode cn, String obfMapping)
	{
		if (cn == null || obfMapping == null) return null;

		String[] split = obfMapping.split(" ");
		if (split.length < 3) return null;

		for (FieldNode field : cn.fields) {
			if (field.name.equals(split[1]) && field.desc.equals(split[2])) {
				return field;
			}
		}

		return null;
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
	
    
	public static boolean classHasInterfaces(ClassNode classNode, String... ifaces) 
	{
		boolean implementsAll = true;
		List<String> implemented = classNode.interfaces;
		
		for (String iface : ifaces) {
			if (!implemented.contains(iface)) { implementsAll = false; break; }
		}
		
		return implementsAll;		
	}
	
	
	public static boolean doesInheritFrom(String className, String inheritFrom)
	{
		if (className.equals(inheritFrom)) return true;
		
		ClassNode cn = getClassNode(className);
		if (cn == null) return false;	
		
		List<String> classes = new ArrayList<>();		
		if (cn.superName != null) classes.add(cn.superName);
		classes.addAll(cn.interfaces);		
		
		// First pass
		for (String c : classes) {
			if (c.equals(inheritFrom)) return true;
		}
		
		// Deeper pass
		for (String c : classes) {
			if (doesInheritFrom(c, inheritFrom)) return true;
		}
		
		return false;
	}

    /**
     *
     * @param method The Method to get the parameter ClassNodes
     * @return A List of ClassNodes
     * @author canitzp
     */
	public static List<ClassNode> getClassNodesFromMethodArguments(MethodNode method){
	    List<ClassNode> classes = new ArrayList<>();
        Type[] params = Type.getArgumentTypes(method.desc);
        for(Type param : params){
            classes.add(getClassNode(param.getClassName()));
        }
        return classes;
    }



	public static List<String> getStringsFromMethod(MethodNode method)
	{
		List<String> list = new ArrayList<>();

		for (AbstractInsnNode node : method.instructions.toArray()) {
			String s = getLdcString(node);
			if (s != null) list.add(s);
		}

		return list;
	}


	public static boolean doesMethodContainString(MethodNode method, String string)
	{
		return getStringsFromMethod(method).contains(string);
	}


	public static List<MethodNode> getMethodsContainingString(ClassNode cn, String string)
	{
		List<MethodNode> list = new ArrayList<>();

		for (MethodNode method : cn.methods) {
			if (doesMethodContainString(method, string)) list.add(method);
		}

		return list;
	}

}

