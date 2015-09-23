package net.fybertech.dynamicmappings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fybertech.dynamicmappings.InheritanceMap.FieldHolder;
import net.fybertech.dynamicmappings.InheritanceMap.MethodHolder;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;


public class ModMappings {

	
	/*public static List<String> walkTreeForClasses(File initialDir, File dir)
	{
		List<String> files = new ArrayList<>();
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) files.addAll(walkTreeForClasses(initialDir, f));
			else if (f.getName().endsWith(".class")) {
				String className = initialDir.toURI().relativize(f.toURI()).getPath();
				className = className.substring(0,  className.length() - 6);
				files.add(className);
			}
		}
		return files;
	}*/
	
	
	static class Holder
	{
		public int type;
		public String owner;
		public String name;
		public String desc;
		
		public Holder(int t, String o, String n, String d) {
			type = t;
			owner = o;
			name = n;
			desc = d;
		}
		
		@Override
		public int hashCode() {
		    int result = 17;
		    result = 31 * result + type;
		    result = 31 * result + owner.hashCode();
		    result = 31 * result + name.hashCode();
		    result = 31 * result + desc.hashCode();		    
		    return result;
		}
		
		@Override		
		public boolean equals(Object obj) {		
			return obj.hashCode() == this.hashCode();
		}
	}
	
	
	static String[] exclusions = new String[] { 
		"org/objectweb/", 
		"java/", 
		"net/minecraft/launchwrapper/", 
		"net/fybertech/dynamicmappings/",
		"org/apache/",
		"net/fybertech/meddle/",
		"net/fybertech/meddleapi/"
	};
	
	
	
	public static boolean isExcluded(String owner)
	{
		for (String exclusion : exclusions) if (owner.startsWith(exclusion)) return true; 
		return false;
	}
	
	
	public static List<File> walkTreeForClasses(File dir)
	{
		List<File> files = new ArrayList<>();
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) files.addAll(walkTreeForClasses(f));
			else if (f.getName().endsWith(".class")) files.add(f);
		}
		return files;
	}
	
	
	public static Set<String> searchConstantPoolForClasses(File classFile)
	{
		Set<String> classes = new HashSet<>();
		
		InputStream stream;
		ClassReader reader;
		try {
			stream = new FileInputStream(classFile);
			reader = new ClassReader(stream);
		} catch (Exception e1) { return classes; }

		int itemCount = reader.getItemCount();
		char[] buffer = new char[reader.getMaxStringLength()];

		for (int n = 1; n < itemCount; n++)	{
			int pos = reader.getItem(n);
			if (pos == 0 || reader.b[pos - 1] != 7) continue;

			Arrays.fill(buffer, (char)0);
			reader.readUTF8(pos,  buffer);
			String string = (new String(buffer)).trim();
			if (string.length() < 1) continue;
			
			while (string != null && string.startsWith("[")) string = getArrayType(string);
			if (string == null) continue;
			
			if (!isExcluded(string)) classes.add(string);
		}

		return classes;
	}
	
	
	public static Set<Holder> searchConstantPoolForFields(File classFile)
	{
		Set<Holder> fields = new HashSet<>();
		
		InputStream stream;
		ClassReader reader;
		try {
			stream = new FileInputStream(classFile);
			reader = new ClassReader(stream);
		} catch (Exception e1) { return fields; }

		int itemCount = reader.getItemCount();
		char[] buffer = new char[reader.getMaxStringLength()];

		for (int n = 1; n < itemCount; n++)	{
			int pos = reader.getItem(n);
			if (pos == 0) continue;
			int cpType = reader.b[pos - 1];
			if (cpType != 9) continue;
			
			String owner = reader.readClass(pos, buffer);
			int cpIndex = reader.getItem(reader.readUnsignedShort(pos + 2));
			String name = reader.readUTF8(cpIndex, buffer);
			String desc = reader.readUTF8(cpIndex + 2, buffer);
			if (!isExcluded(owner)) fields.add(new Holder(cpType, owner, name, desc));
		}

		return fields;
	}
	
	public static Set<Holder> searchConstantPoolForMethods(File classFile)
	{
		Set<Holder> fields = new HashSet<>();
		
		InputStream stream;
		ClassReader reader;
		try {
			stream = new FileInputStream(classFile);
			reader = new ClassReader(stream);
		} catch (Exception e1) { return fields; }

		int itemCount = reader.getItemCount();
		char[] buffer = new char[reader.getMaxStringLength()];

		for (int n = 1; n < itemCount; n++)	{
			int pos = reader.getItem(n);
			if (pos == 0) continue;
			int cpType = reader.b[pos - 1];
			if (cpType != 10 && cpType != 11) continue;
			
			String owner = reader.readClass(pos, buffer);
			int cpIndex = reader.getItem(reader.readUnsignedShort(pos + 2));
			String name = reader.readUTF8(cpIndex, buffer);
			String desc = reader.readUTF8(cpIndex + 2, buffer);
			if (!isExcluded(owner)) fields.add(new Holder(cpType, owner, name, desc));
		}

		return fields;
	}
	
	
	public static Set<Holder> getClassFields(File classFile)
	{
		Set<Holder> fields = new HashSet<>();
		
		InputStream stream;
		ClassReader reader;
		ClassNode cn = new ClassNode();
		try {
			stream = new FileInputStream(classFile);
			reader = new ClassReader(stream);						
		} catch (Exception e1) { return fields; }
		
		reader.accept(cn, 0);
		
		for (FieldNode field : cn.fields) {
			fields.add(new Holder(9, cn.name, field.name, field.desc));
		}
		
		return fields;
	}
	
	
	public static Set<Holder> getClassMethods(File classFile)
	{
		Set<Holder> methods = new HashSet<>();
		
		InputStream stream;
		ClassReader reader;
		ClassNode cn = new ClassNode();
		try {
			stream = new FileInputStream(classFile);
			reader = new ClassReader(stream);						
		} catch (Exception e1) { return methods; }
		
		reader.accept(cn, 0);
		
		for (MethodNode method : cn.methods) {
			methods.add(new Holder(10, cn.name, method.name, method.desc));
		}
		
		return methods;
	}

	
	public static String getArrayType(String array)
	{
		Type t = Type.getType(array);
		if (t.getSort() != Type.ARRAY) return array;
		t = t.getElementType();
		if (t.getSort() != Type.OBJECT) return null;
		return t.getClassName().replace(".", "/");
	}
	
	
	public static int getMappingSide(String mapping)
	{
		if (DynamicMappings.clientMappingsSet.contains(mapping)) return 1;
		if (DynamicMappings.serverMappingsSet.contains(mapping)) return 2;
		return 3;
	}
	
	
	public static void main(String[] args) 
	{
		//System.out.println(System.getProperty("java.class.path"));
		
		if (args.length < 1) {
			System.out.println("No input directory specified!");
			return;
		}
		
		File classDir = new File(args[0]);
		if (!classDir.exists() || !classDir.isDirectory()) { 
			System.out.println("Doesn't exist or isn't directory: " + args[0]); 
			return; 
		}
		
		
		File outputFile = null;
		
		if (args.length < 2) {
			System.out.println("No output file specified, printing to console.");
		}
		else outputFile = new File(args[1]);
		
		
		
		
		
		
		// Put DynamicMappings in simulated mode so that we can get the full list of mappings they 
		// might provide, while allowing this application to use a remapped jar for its inheritance
		// maps.
		DynamicMappings.simulatedMappings = true;
		DynamicMappings.generateClassMappings();
		
		
		//System.out.println(classDir.getAbsolutePath());

		Set<String> classes = new HashSet<>();
		Set<Holder> fields = new HashSet<>();
		Set<Holder> methods = new HashSet<>();
		
		List<File> classFiles = walkTreeForClasses(classDir);
		for (File f : classFiles) {
			classes.addAll(searchConstantPoolForClasses(f));			
			
			fields.addAll(searchConstantPoolForFields(f));
			fields.addAll(getClassFields(f));			
			
			methods.addAll(searchConstantPoolForMethods(f));
			methods.addAll(getClassMethods(f));
		}
		
		InheritanceMap inheritanceMap = new InheritanceMap();
		
		List<String> output = new ArrayList<>();
		
		//System.out.println("Classes:");
		for (String s : classes) {
			boolean hasMapping = DynamicMappings.classMappings.containsKey(s);
			//System.out.println("  " + (hasMapping ? "TRUE " :"") + s);
			if (hasMapping) output.add("C " + getMappingSide(s) + " " + s);
		}
		
		//System.out.println("Fields:");
		for (Holder holder : fields) {
			//System.out.println("  " + holder.owner + " " + holder.name + " " + holder.desc);
			InheritanceMap im = null;
			try {
				im = inheritanceMap.buildMap(holder.owner);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (im == null) { System.out.println("COULDN'T BUILD MAP: " + holder.owner); continue; }
			
			HashSet<FieldHolder> fh = im.fields.get(holder.name + " " + holder.desc);
			for (FieldHolder f : fh) {
				String mapping = f.cn.name + " " + f.fn.name + " " + f.fn.desc;
				boolean hasMapping = DynamicMappings.fieldMappings.containsKey(mapping);
				//System.out.println("    " + (hasMapping ? "TRUE " :"") + f.cn.name + " " + f.fn.name);
				if (hasMapping) output.add("F " + getMappingSide(mapping) + " " + mapping);
			}
		}
		
		//System.out.println("Methods:");
		for (Holder holder : methods) {
			//System.out.println("  " + holder.owner + " " + holder.name + " " + holder.desc);
			
			InheritanceMap im = null;
			try {
				im = inheritanceMap.buildMap(holder.owner);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (im == null) { System.out.println("COULDN'T BUILD MAP: " + holder.owner); continue; }
			
			HashSet<MethodHolder> mh = im.methods.get(holder.name + " " + holder.desc);
			if (mh == null) continue; // Shouldn't be possible in normal circumstances, but we'll do for safety
			
			for (MethodHolder m : mh) {
				String mapping = m.cn.name + " " + m.mn.name + " " + m.mn.desc;
				boolean hasMapping = DynamicMappings.methodMappings.containsKey(mapping);
				//System.out.println("    " + (hasMapping ? "TRUE " :"") + m.cn.name + " " + m.mn.name);
				if (hasMapping) output.add("M " + getMappingSide(mapping) + " " + mapping);
			}
		}
		
		if (outputFile != null) {
			outputFile.toPath().getParent().toFile().mkdirs();
			
			PrintWriter pw;
			try {
				pw = new PrintWriter(new FileOutputStream(outputFile));
				for (String line : output) pw.println(line);
			    pw.close();			
			} catch (IOException e) {				
				e.printStackTrace();
			}			
		}
		else
			for (String line : output) System.out.println(line);
		
		
		
	}
	
}
