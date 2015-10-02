package net.fybertech.dynamicmappings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import net.minecraft.launchwrapper.Launch;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class AccessUtil 
{
	public static final int allAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;	
	
	public List<String> accessTransformerFields = new ArrayList<>();
	public List<String> accessTransformerMethods = new ArrayList<>();
	public List<String> accessTransformerClasses = new ArrayList<>(); // TODO
	
	
	public void readAllTransformerConfigs()
	{
		System.out.println("Discovering access transformers...");

		Enumeration<URL> urls = null;
		
		try {
			urls = AccessUtil.class.getClassLoader().getResources("accesstransformer.cfg");		
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (urls == null) return;
		
		while (urls.hasMoreElements()) {
			URL url = urls.nextElement();
			try {				
				BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

				System.out.println("Processing access transformer at " + url);

				String line = null;				
				while ((line = reader.readLine()) != null) {
					// Ignore commented or short lines
					if (line.startsWith("#") || line.length() < 1) continue;
					
					String[] split = line.split(" ", 2);
					if (split.length < 2) continue;
					
					String mode = split[0].toUpperCase();
					String ac = split[1];
					
					if (mode.equals("F")) accessTransformerFields.add(ac);
					else if (mode.equals("M")) accessTransformerMethods.add(ac);
					else if (mode.equals("C")) accessTransformerClasses.add(ac);
				}
				
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	

	public void transformDeobfuscatedClass(ClassNode cn)
	{		
		for (String transformer : accessTransformerFields) {
			String[] split = transformer.split(" ");
			if (split.length != 4) continue;
			
			String className = split[0];
			String fieldName = split[1];
			String fieldDesc = split[2];
			int access = 0;
			try {
				access = Integer.parseInt(split[3]);
			}
			catch (NumberFormatException e) {}
			if (access < 1) continue;
			
			if (!cn.name.equals(className)) continue;
			
			boolean wildcardName = fieldName.equals("*");
			boolean wildcardDesc = fieldDesc.equals("*");
			
			for (FieldNode field : cn.fields) {
				if (!field.name.equals(fieldName) && !wildcardName) continue;
				if (!field.desc.equals(fieldDesc) && !wildcardDesc) continue;
				field.access = (field.access & ~allAccess) | access;
				System.out.println("Modifying access of " + className + " " + field.name + " " + field.desc);
			}			
		}
		
		
		for (String transformer : accessTransformerMethods) {
			String[] split = transformer.split(" ");
			if (split.length != 4) continue;
			
			String className = split[0];
			String methodName = split[1];
			String methodDesc = split[2];
			int access = 0;
			try {
				access = Integer.parseInt(split[3]);
			}
			catch (NumberFormatException e) {}
			if (access < 1) continue;
			
			if (!cn.name.equals(className)) continue;
			
			boolean wildcardName = methodName.equals("*");
			boolean wildcardDesc = methodDesc.equals("*");
			
			for (MethodNode method : cn.methods) {
				if (!method.name.equals(methodName) && !wildcardName) continue;
				if (!method.desc.equals(methodDesc) && !wildcardDesc) continue;
				method.access = (method.access & ~allAccess) | access;
				System.out.println("Modifying access of " + className + " " + method.name + " " + method.desc);
			}			
		}
		
		
	}

	
}
