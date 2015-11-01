package net.fybertech.dynamicmappings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;


public class MergeJars 
{
	public static Map<String, ClassInfo> classes = new HashMap<String, ClassInfo>();
	public static Map<String, MiscInfo> miscFiles = new HashMap<String, MiscInfo>();
	
	public static boolean quiet = false;
	
	public static final int CLIENT = 1;
	public static final int SERVER = 2;
	public static final int BOTH = 3;
	
	
	public static class MiscInfo
	{
		String name;
		
		int onClientServer = 0;
	}
	
	public static class ClassInfo
	{
		String name;
		Map<String, FieldInfo> fields = new HashMap<String, FieldInfo>();
		Map<String, MethodInfo> methods = new HashMap<String, MethodInfo>();
		
		int onClientServer = 0;
	}
	
	public static class FieldInfo
	{
		String name;
		String desc;
		
		int onClientServer = 0;
	}
	
	public static class MethodInfo
	{
		String name;
		String desc;		
		
		int onClientServer = 0;
	}
	
		
	public static String[] excluded = new String[] { "META-INF/", "org/", "com/", "gnu/", "io/", "javax/", "org/" };
	
	
	public static JarFile discoverClasses(File filename, int clientOrServer)
	{				
		
		JarFile jarFile = null;
		try {
			jarFile = new JarFile(filename);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Enumeration<JarEntry> entries = jarFile.entries();
		while (entries.hasMoreElements())
		{
			JarEntry entry = entries.nextElement();	
			String entryName = entry.getName();
			
			boolean isExcluded = false;
			for (String s : excluded) if (entryName.startsWith(s)) { isExcluded = true; break; }
			if (isExcluded) continue;
			
			if (!entryName.endsWith(".class"))
			{
				MiscInfo info = miscFiles.get(entryName);
				if (info == null) 
				{ 
					info = new MiscInfo(); 
					info.name = entryName; 
					miscFiles.put(entryName,  info); 
				}				
				info.onClientServer |= clientOrServer;
				
				continue;
			}
			
			byte[] buffer = getFileFromJar(entry, jarFile);
			
			ClassReader reader = new ClassReader(buffer);
			ClassNode cn = new ClassNode();
			reader.accept(cn, 0);			
			
			ClassInfo info = classes.get(cn.name);
			if (info == null) { info = new ClassInfo(); classes.put(cn.name, info); }
			info.name = cn.name;
			info.onClientServer |= clientOrServer;
			
			for (FieldNode field : (List<FieldNode>)cn.fields)
			{				
				FieldInfo finfo = info.fields.get(field.name);
				if (finfo == null)
				{ 
					finfo = new FieldInfo();
					finfo.name = field.name;
					finfo.desc = field.desc;
					info.fields.put(field.name, finfo);
				}
				finfo.onClientServer |= clientOrServer;
			}
			
			for (MethodNode method : (List<MethodNode>)cn.methods)
			{
				MethodInfo minfo = info.methods.get(method.name + method.desc);
				if (minfo == null)
				{
					minfo = new MethodInfo();
					minfo.name = method.name;
					minfo.desc = method.desc;				
					info.methods.put(method.name + method.desc, minfo);	
				}
				minfo.onClientServer |= clientOrServer;
				
			}			
			
		}
		
		return jarFile;
		
	}
	
	
	public static byte[] getFileFromJar(ZipEntry entry, JarFile jarFile)
	{
		byte[] buffer = null;		

		if (entry != null)
		{			
			try {
				InputStream stream = jarFile.getInputStream(entry);				
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
	
	
	public static byte[] getFileFromJar(String filename, JarFile jarFile)
	{
		return getFileFromJar(jarFile.getEntry(filename), jarFile);
	}
	
	
	public static byte[] getFileFromJar(String filename, String jarName)
	{
		byte[] buffer = null;
		
		JarFile jarFile = null;
		try {
			jarFile = new JarFile(jarName);
		} catch (IOException e) {
			e.printStackTrace();			
		}
		
		buffer = getFileFromJar(filename, jarFile);		
		
		try {
			jarFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return buffer;
	}
	
	
	public static ClassNode getClassFromJar(String className, String jarName)
	{
		ClassNode node = null;		
		
		className = className.replace(".",  "/");
		byte[] buffer = getFileFromJar(className + ".class", jarName);		
			
		if (buffer != null)
		{
			ClassReader reader = new ClassReader(buffer);
			node = new ClassNode();
			reader.accept(node, 0);
		}
		
		return node;
	}
	
	
	public static ClassNode getClassFromJar(String className, JarFile jarFile)
	{
		ClassNode node = null;		
		
		className = className.replace(".",  "/");
		byte[] buffer = getFileFromJar(className + ".class", jarFile);		
			
		if (buffer != null)
		{
			ClassReader reader = new ClassReader(buffer);
			node = new ClassNode();
			reader.accept(node, 0);
		}
		
		return node;
	}
	
	
	public static void writeToFile(String filename, byte[] data)
	{
		File f = new File(filename);
		f.toPath().getParent().toFile().mkdirs();
		
		try {
			FileOutputStream stream = new FileOutputStream(filename);
			stream.write(data);
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	
	
	public static void mergeJars(File clientJarFile, File serverJarFile, File outputFile)
	{
		if (!quiet) System.out.println("> Parsing client");
		JarFile clientJar = discoverClasses(clientJarFile, CLIENT);
		if (!quiet) System.out.println("> Parsing server");
		JarFile serverJar = discoverClasses(serverJarFile, SERVER);
		
		JarOutputStream outputJar = null;		
		try {
			outputJar = new JarOutputStream(new FileOutputStream(outputFile));
		} catch (IOException e) {
			e.printStackTrace();
		}		
		
		if (!quiet) System.out.println("> Merging client and server");
		
		
		for (MiscInfo info : miscFiles.values())
		{
			if ((info.onClientServer & CLIENT) == CLIENT)
			{
				ZipEntry entry = clientJar.getEntry(info.name);
				try {
					outputJar.putNextEntry(new ZipEntry(entry));
					outputJar.write(getFileFromJar(entry, clientJar));
				} catch (IOException e) {					
					e.printStackTrace();
				}			
			}
			else
			{
				ZipEntry entry = serverJar.getEntry(info.name);
				try {
					outputJar.putNextEntry(new ZipEntry(entry));
					outputJar.write(getFileFromJar(entry, serverJar));
				} catch (IOException e) {					
					e.printStackTrace();
				}			
			}
		}
		
		
		for (ClassInfo info : classes.values())
		{			
			if (info.onClientServer == CLIENT)
			{				
				ClassNode clientClass = getClassFromJar(info.name, clientJar);				
			
				if (clientClass.visibleAnnotations == null) clientClass.visibleAnnotations = new ArrayList();
				clientClass.visibleAnnotations.add(new AnnotationNode("Lnet/fybertech/meddleapi/side/ClientOnly;"));
				
				ClassWriter writer = new ClassWriter(Opcodes.ASM4);
				clientClass.accept(writer);
				
				byte[] classData = writer.toByteArray();
				//writeToFile(outputFilename + File.separator + info.name + ".class", classData);
				try {
					outputJar.putNextEntry(new ZipEntry(info.name + ".class"));
					outputJar.write(classData);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			else if (info.onClientServer == SERVER)
			{				
				ClassNode serverClass = getClassFromJar(info.name, serverJar);				
				
				if (serverClass.visibleAnnotations == null) serverClass.visibleAnnotations = new ArrayList();
				serverClass.visibleAnnotations.add(new AnnotationNode("Lnet/fybertech/meddleapi/side/ServerOnly;"));
				
				ClassWriter writer = new ClassWriter(Opcodes.ASM4);
				serverClass.accept(writer);
				
				byte[] classData = writer.toByteArray();
				
				//writeToFile(outputFilename + File.separator + info.name + ".class", classData);
				try {
					outputJar.putNextEntry(new ZipEntry(info.name + ".class"));
					outputJar.write(classData);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			else
			{
				ClassNode clientClass = getClassFromJar(info.name, clientJar);
				ClassNode serverClass = getClassFromJar(info.name, serverJar);
				
				for (FieldInfo field : info.fields.values())
				{
					if (field.onClientServer == SERVER)
					{
						FieldNode serverField = null;
						for (FieldNode fn : (List<FieldNode>)serverClass.fields)
						{
							if (fn.name.equals(field.name)) { serverField = fn; break; }
						}
						
						if (serverField.visibleAnnotations == null) serverField.visibleAnnotations = new ArrayList();
						serverField.visibleAnnotations.add(new AnnotationNode("Lnet/fybertech/meddleapi/side/ServerOnly;"));
						clientClass.fields.add(serverField);
					}
					else if (field.onClientServer == CLIENT)
					{
						FieldNode clientField = null;
						for (FieldNode fn : (List<FieldNode>)clientClass.fields)
						{
							if (fn.name.equals(field.name)) { clientField = fn; break; }
						}
						
						if (clientField.visibleAnnotations == null) clientField.visibleAnnotations = new ArrayList();
						clientField.visibleAnnotations.add(new AnnotationNode("Lnet/fybertech/meddleapi/side/ClientOnly;"));
					}
				}
				
				for (MethodInfo method : info.methods.values())
				{
					if (method.onClientServer == SERVER)
					{
						MethodNode serverMethod = null;
						for (MethodNode fn : (List<MethodNode>)serverClass.methods)
						{
							if (fn.name.equals(method.name) && fn.desc.equals(method.desc)) { serverMethod = fn; break; }
						}
						
						if (serverMethod.visibleAnnotations == null) serverMethod.visibleAnnotations = new ArrayList();
						serverMethod.visibleAnnotations.add(new AnnotationNode("Lnet/fybertech/meddleapi/side/ServerOnly;"));
						clientClass.methods.add(serverMethod);
					}
					else if (method.onClientServer == CLIENT)
					{
						MethodNode clientMethod = null;
						for (MethodNode fn : (List<MethodNode>)clientClass.methods)
						{
							if (fn.name.equals(method.name) && fn.desc.equals(method.desc)) { clientMethod = fn; break; }
						}
						
						if (clientMethod.visibleAnnotations == null) clientMethod.visibleAnnotations = new ArrayList();
						clientMethod.visibleAnnotations.add(new AnnotationNode("Lnet/fybertech/meddleapi/side/ClientOnly;"));						
					}
				}
				
				ClassWriter writer = new ClassWriter(Opcodes.ASM4);
				clientClass.accept(writer);
				//writeToFile(outputFilename + File.separator + info.name + ".class", writer.toByteArray());
				try {
					outputJar.putNextEntry(new ZipEntry(info.name + ".class"));
					outputJar.write(writer.toByteArray());
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}			
		}
		
		
		try {
			clientJar.close();
			serverJar.close();
			outputJar.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	public static void main(String[] args)
	{			
		String clientJarFilename = null;
		String serverJarFilename = null;
		String outputFilename = null;		
		
		int nextVal = 0;
		for (String arg : args)
		{			
			if (nextVal > 0)
			{
				if (nextVal == 1) clientJarFilename = arg;
				else if (nextVal == 2) serverJarFilename = arg;
				else outputFilename = arg;
								
				nextVal = 0;
				continue;
			}
			
			if (arg.equalsIgnoreCase("-c")) nextVal = 1;
			else if (arg.equalsIgnoreCase("-s")) nextVal = 2;
			else if (arg.equalsIgnoreCase("-o")) nextVal = 3;
			else if (arg.equalsIgnoreCase("-q")) quiet = true;
		}	
		
		if (clientJarFilename == null) { System.err.println("Error: Client jar not specified"); System.exit(1); }
		if (serverJarFilename == null) { System.err.println("Error: Server jar not specified"); System.exit(2); }
		if (outputFilename == null)    { System.err.println("Error: Output jar not specified"); System.exit(3); }
		
		File clientJarFile = new File(clientJarFilename);
		File serverJarFile = new File(serverJarFilename);		
		if (!clientJarFile.exists()) { System.err.println("Error: Client jar not found"); System.exit(4); }
		if (!serverJarFile.exists()) { System.err.println("Error: Server jar not found"); System.exit(5); }
		
		mergeJars(clientJarFile, serverJarFile, new File(outputFilename));
	}
	
	
}
