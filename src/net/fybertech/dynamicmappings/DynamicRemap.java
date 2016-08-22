package net.fybertech.dynamicmappings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import net.fybertech.dynamicmappings.InheritanceMap;
import net.fybertech.dynamicmappings.InheritanceMap.FieldHolder;
import net.fybertech.dynamicmappings.InheritanceMap.MethodHolder;
import net.fybertech.dynamicmappings.ParmParser.Parm;


public class DynamicRemap 
{
	
	Map<String, String> classMappings;
	Map<String, String> fieldMappings;
	Map<String, String> methodMappings;	
	
	public String unpackagedPrefix = "net/minecraft/class_";
	public String unpackagedInnerPrefix = "innerclass_";
	
	public InheritanceMap inheritanceMapper = new InheritanceMap();
	
	
	
	public DynamicRemap(Map<String, String> cm, Map<String, String> fm, Map<String, String> mm)
	{
		classMappings = cm;
		fieldMappings = fm;
		methodMappings = mm;	
	}
	
	
	public ClassNode getClassNode(String className)
	{
		return DynamicMappings.getClassNode(className);
	}
	
	
	
	private boolean isObfInner(String s)
	{
		//if (s.length() > 1) return false;
		
		try {
			Integer.parseInt(s);
			return false;
		}
		catch (NumberFormatException e) 
		{
			return true;
		}
	}
		
	
	private class MyRemapper extends Remapper
	{
		
		boolean showMapMethod = false;
		final ClassNode classNode;
		
		public MyRemapper(ClassNode cn, boolean debug) {
			classNode = cn;
			showMapMethod = debug;
		}
		
		
		@Override
		public String map(String typeName) 
		{
			boolean originallyUnpackaged = !typeName.contains("/");
			
			if (classMappings.containsKey(typeName)) return classMappings.get(typeName);			
			
			// Remap the parent class in the case of an inner class
			String[] split = typeName.split("\\$");
			if (classMappings.containsKey(split[0])) split[0] = classMappings.get(split[0]);			
			typeName = split[0];
			
			for (int n = 1; n < split.length; n++) {
				String inner = split[n];
				if (originallyUnpackaged && isObfInner(inner) && unpackagedInnerPrefix != null) inner = unpackagedInnerPrefix + inner + n;
				typeName += "$" + inner;				
			}			
			
			if (!typeName.contains("/") && unpackagedPrefix != null) typeName = unpackagedPrefix + typeName;
			return super.map(typeName);
		}
		
		
		@Override
		public String mapFieldName(String owner, String name, String desc)
		{			
			ClassNode cn = getClassNode(owner);			
			if (cn == null) return super.mapFieldName(owner, name, desc);
			
			InheritanceMap map = null;
			try {
				map = inheritanceMapper.buildMap(cn);
			} catch (IOException e) {
				e.printStackTrace();
			}			
			Set<FieldHolder> fields = map.fields.get(name + " " + desc);
			
			if (fields == null) return super.mapFieldName(owner, name, desc);
			
			for (FieldHolder holder : fields) {			
				String key = holder.cn.name + " " + holder.fn.name + " " + holder.fn.desc;
				if (fieldMappings.containsKey(key)) {
					String mapping = fieldMappings.get(key);
					String[] split = mapping.split(" ");
					return super.mapFieldName(owner, split[1], desc);
				}
			}
			
			return super.mapFieldName(owner,  name,  desc);
		}		
		
		
		@Override
		public String mapMethodName(String owner, String name, String desc) 
		{			
			if (owner.startsWith("[") || name.startsWith("<")) return super.mapMethodName(owner, name, desc);		
			
			if (showMapMethod) System.out.println("mapMethod: " + owner + " " + name + " " + desc);
			
			ClassNode cn = getClassNode(owner);			
			if (cn == null) return super.mapMethodName(owner, name, desc);
						
			InheritanceMap map = null;
			try {
				map = inheritanceMapper.buildMap(cn);
			} catch (IOException e) {
				e.printStackTrace();
			}
					
			Set<MethodHolder> methods = map.methods.get(name + " " + desc);	
			if (methods == null) return super.mapMethodName(owner, name, desc);		
			
			
			for (MethodHolder holder : methods) {			
				String key = holder.cn.name + " " + holder.mn.name + " " + holder.mn.desc;
				if (showMapMethod) System.out.println("Key: " + key);
				
				if (methodMappings.containsKey(key)) {
					if (showMapMethod) System.out.println("    HAS KEY");
					String mapping = methodMappings.get(key);	
					//System.out.println(mapping);
					String[] split = mapping.split(" ");
					return super.mapMethodName(owner, split[1], desc);
				}
			}
			
			return super.mapMethodName(owner, name, desc);
		}
	}
	
	
	public ClassNode remapClass(String className)
	{
		if (className == null) return null;
		
		InputStream stream = getClass().getClassLoader().getResourceAsStream(className + ".class");
		return remapClass(stream);
	}
	
	
	public ClassNode remapClass(InputStream stream)
	{
		if (stream == null) return null;
				
		ClassReader reader = null;
		try {
			reader = new ClassReader(stream);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return remapClass(reader);
	}
	
	
	public byte[] remapClass(byte[] basicClass) 
	{
		if (basicClass == null) return null;
		
		ClassReader reader = new ClassReader(basicClass);
		
		ClassNode cn = remapClass(reader);
		if (cn == null) return null;
		
		ClassWriter cw = new ClassWriter(0);
		cn.accept(cw);
		return cw.toByteArray();
	}
	
	
	private class CustomRemappingClassAdapter extends RemappingClassAdapter
	{
		public CustomRemappingClassAdapter(ClassVisitor cv, Remapper remapper) {
			super(cv, remapper);
		}
		
		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) 
		{			
			// TODO - Might need to handle for nested inner classes
			if (classMappings.containsKey(name)) {
				String outer = classMappings.get(outerName);
				if (outer == null) outer = outerName;
				outerName = outer;
				
				name = classMappings.get(name);
				innerName = name.substring(name.lastIndexOf("$") + 1);
				//System.out.println("INNER: " + outerName + " " + innerName);
				
				super.visitInnerClass(name, outerName, innerName, access);
				return;
			}
			
			if (!name.contains("/") && innerName != null && unpackagedInnerPrefix != null && isObfInner(innerName))
			{
				innerName = unpackagedInnerPrefix + innerName;				
			}
			
			super.visitInnerClass(name, outerName, innerName, access);
		}
		
	}
	
	
	public ClassNode remapClass(ClassReader reader)
	{
		boolean showDebug = false;
		
		ClassNode cn = new ClassNode();		
		reader.accept(new CustomRemappingClassAdapter(cn, new MyRemapper(cn, showDebug)), ClassReader.EXPAND_FRAMES);		
		
		// Fix obfuscation of local variable names
		for (MethodNode method : cn.methods)
		{
			int paramCount = 0;
			int varCount = 0;
			
			if (method.localVariables != null)
				for (LocalVariableNode lvn : method.localVariables)
				{
					if (!lvn.name.equals("\u2603")) continue;
					if (lvn.start == method.instructions.getFirst()) lvn.name = "param" + paramCount++;
					else lvn.name = "var" + varCount++;
				}
		}		
		
		if (showDebug) {
			System.out.println(cn.name);
			for (MethodNode method : cn.methods) {
				System.out.println("  " + method.name + " " + method.desc);
			}
		}
		
		return cn;
	}
		
	
	public static byte[] getFileFromZip(ZipEntry entry, ZipFile zipFile)
	{
		byte[] buffer = null;		

		if (entry != null)
		{			
			try {
				InputStream stream = zipFile.getInputStream(entry);				
				int pos = 0;
				buffer = new byte[(int)entry.getSize()];
				while (true)
				{
					int read = stream.read(buffer, pos, Math.min(1024, (int)entry.getSize() - pos));					
					pos += read;					
					if (read < 1) break;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return buffer;
	}
	
	
	
	/**
	 * Parses the jar to find any unmapped child classes of any of the specified 
	 * classes, then gives them generic names and repackages them.
	 * 
	 * @param mcJar - The obfuscated Minecraft jar
	 * @param baseClasses - List of deobfuscated class names to discover children for
	 */
	public static void remapUnknownChildren(JarFile mcJar, String ... baseClasses )
	{
		for (Enumeration<JarEntry> enumerator = mcJar.entries(); enumerator.hasMoreElements();)
		{
			JarEntry entry = enumerator.nextElement();
			String filename = entry.getName();				
			if (!filename.endsWith(".class")) continue;
			String className = filename.substring(0, filename.length() - 6);
			
			if (className.contains("$")) continue;
			
			for (String mappedClass : baseClasses) {
			
				String baseClass = DynamicMappings.getClassMapping(mappedClass);
				String classPrefix = mappedClass + "Unknown_";			
		
				if (baseClass != null && DynamicMappings.doesInheritFrom(className, baseClass) && !DynamicMappings.reverseClassMappings.containsKey(className)) {
					DynamicMappings.addClassMapping(classPrefix + className, className);
					break;
				}
			}
		}	
	}
	
	
	
	public static void main(String[] args)
	{	
		ParmParser pp = new ParmParser();
		Parm outputParm = pp.addParm("-o",  1); // Output location
		Parm clearMappersParm = pp.addParm("-clearmappers", 0);		
		Parm addMapperParm = pp.addParm("-addmapper", 1);
		pp.processArgs(args);
		
		File outputFile = new File("mcremapped.jar");
		if (outputParm.found) outputFile = new File(outputParm.getFirstResult());
		
		if (clearMappersParm.found) DynamicMappings.MAPPINGS_CLASSES.clear();
		if (addMapperParm.found) {
			String split[] = addMapperParm.getFirstResult().split(":;,");
			for (String mapper : split) DynamicMappings.MAPPINGS_CLASSES.add(mapper);
		}
		
		DynamicMappings.generateClassMappings();

		AccessUtil accessUtil = new AccessUtil();
		accessUtil.readAllTransformerConfigs();	
		
		JarFile mcJar = DynamicMappings.getMinecraftJar();
		
		// Moves unmapped blocks, items, etc to the appropriate packages
		if (mcJar != null) {			
			remapUnknownChildren(mcJar, "net/minecraft/block/Block", "net/minecraft/item/Item", "net/minecraft/entity/monster/EntityMob",
					"net/minecraft/entity/Entity", "net/minecraft/tileentity/TileEntity", "net/minecraft/inventory/Container", 
					"net/minecraft/client/gui/inventory/GuiContainer", "net/minecraft/client/gui/Gui", "net/minecraft/stats/StatBase");				
			
			try {
				mcJar.close();
			} catch (IOException e) {}
		}
		
				
		DynamicRemap remapper = new DynamicRemap(
				DynamicMappings.reverseClassMappings, 
				DynamicMappings.reverseFieldMappings, 
				DynamicMappings.reverseMethodMappings);		
		
		
		URL url = DynamicRemap.class.getClassLoader().getResource("net/minecraft/server/MinecraftServer.class");	
		if (url == null) { System.out.println("Couldn't locate server class!"); return; }
		
		
		JarFile jar = null;
		if ("jar".equals(url.getProtocol())) {
			JarURLConnection connection = null;
			try {
				connection = (JarURLConnection) url.openConnection();
				jar = connection.getJarFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if (jar == null) { System.out.println("Couldn't locate Minecraft jar!"); return; }
		
		
		JarOutputStream outJar = null;
		try {
			outJar = new JarOutputStream(new FileOutputStream(outputFile));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		for (Enumeration<JarEntry> enumerator = jar.entries(); enumerator.hasMoreElements();)
		{
			JarEntry entry = enumerator.nextElement();
			String name = entry.getName();
			byte[] bytes = null;
			
			if (name.startsWith("META-INF/")) {
				if (name.endsWith(".RSA") || name.endsWith(".SF")) continue;
			}
			
			if (name.endsWith(".class")) {
				name = name.substring(0, name.length() - 6);			
				ClassNode mapped = remapper.remapClass(name);
				accessUtil.transformDeobfuscatedClass(mapped);
				ClassWriter writer = new ClassWriter(0);
				mapped.accept(writer);
				name = mapped.name + ".class";
				bytes = writer.toByteArray();
			}
			else bytes = getFileFromZip(entry, jar);
			
			ZipEntry ze = new ZipEntry(name);
			try {
				outJar.putNextEntry(ze);				
				outJar.write(bytes);
			} catch (IOException e) {
				e.printStackTrace();
			}		
		}
		
		try {
			outJar.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		
		
	}


	
	
}
