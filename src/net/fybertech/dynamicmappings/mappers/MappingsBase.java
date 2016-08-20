package net.fybertech.dynamicmappings.mappers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.fybertech.dynamicmappings.DynamicMappings;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class MappingsBase 
{
	public ClassNode getClassNode(String className)
	{
		return DynamicMappings.getClassNode(className);
	}
	
	public ClassNode getClassNodeFromMapping(String deobfClass)
	{
		return DynamicMappings.getClassNodeFromMapping(deobfClass);
	}
	
	public void addClassMapping(String deobfClassName, ClassNode node)
	{
		DynamicMappings.addClassMapping(deobfClassName, node);
	}
	
	public void addClassMapping(String deobfClassName, String obfClassName)
	{
		DynamicMappings.addClassMapping(deobfClassName, obfClassName);
	}
	
	public String getClassMapping(String deobfClassName)
	{
		return DynamicMappings.getClassMapping(deobfClassName);
	}
	
	public boolean searchConstantPoolForStrings(String className, String... matchStrings)
	{
		return DynamicMappings.searchConstantPoolForStrings(className,  matchStrings);
	}
	
	public List<MethodNode> getMatchingMethods(ClassNode cn, String name, String desc)
	{
		return DynamicMappings.getMatchingMethods(cn,  name,  desc);
	}

	public List<MethodNode> getMatchingMethods(ClassNode cn, int accessCode, int returnType, int... parameterTypes)
	{
		return DynamicMappings.getMatchingMethods(cn, accessCode, returnType, parameterTypes);
	}
		
	public void addFieldMapping(String deobfFieldDesc, String obfFieldDesc)
	{
		DynamicMappings.addFieldMapping(deobfFieldDesc, obfFieldDesc);
	}
	
	public void addFieldMapping(String deobfFieldDesc, FieldInsnNode obfField)
	{
		DynamicMappings.addFieldMapping(deobfFieldDesc, obfField.owner + " " + obfField.name + " " + obfField.desc);
	}
	
	public void addMethodMapping(String deobfMethodDesc, String obfMethodDesc)
	{
		DynamicMappings.addMethodMapping(deobfMethodDesc, obfMethodDesc);
	}
	
	public void addMethodMapping(String deobfMethodDesc, MethodInsnNode obfMethod)
	{
		DynamicMappings.addMethodMapping(deobfMethodDesc, obfMethod.owner + " " + obfMethod.name + " " + obfMethod.desc);
	}
	
	public boolean matchOpcodeSequence(AbstractInsnNode insn, int...opcodes)
	{
		return DynamicMappings.matchOpcodeSequence(insn, opcodes);
	}
	
	public String getLdcString(AbstractInsnNode node)
	{
		return DynamicMappings.getLdcString(node);
	}
	
	public boolean checkMethodParameters(MethodNode method, int ... types)
	{
		return DynamicMappings.checkMethodParameters(method, types);
	}
	
	public List<FieldNode> getMatchingFields(ClassNode cn, String name, String desc)
	{
		return DynamicMappings.getMatchingFields(cn, name, desc);
	}
	
	public List<MethodNode> removeMethodsWithFlags(List<MethodNode> methods, int accFlags)
	{
		return DynamicMappings.removeMethodsWithFlags(methods, accFlags);
	}
	
	public List<MethodNode> removeMethodsWithoutFlags(List<MethodNode> methods, int accFlags)
	{
		return DynamicMappings.removeMethodsWithoutFlags(methods, accFlags);
	}
	
	public boolean isLdcWithString(AbstractInsnNode node, String string)
	{
		return DynamicMappings.isLdcWithString(node, string);
	}
	
	public boolean isLdcWithInteger(AbstractInsnNode node, int val)
	{
		return DynamicMappings.isLdcWithInteger(node, val);
	}	
	
	public boolean isLdcWithFloat(AbstractInsnNode node, float val)
	{
		return DynamicMappings.isLdcWithFloat(node, val);
	}
	
	public AbstractInsnNode getNextRealOpcode(AbstractInsnNode insn)
	{
		return DynamicMappings.getNextRealOpcode(insn);
	}
	
	public MethodNode getMethodNode(ClassNode cn, String obfMapping)
	{
		return DynamicMappings.getMethodNode(cn, obfMapping);
	}
	
	public MethodNode getMethodNode(ClassNode cn, MethodInsnNode obfMethod)
	{
		if (obfMethod == null) return null;
		return DynamicMappings.getMethodNode(cn, obfMethod.owner + " " + obfMethod.name + " " + obfMethod.desc);
	}
	
	public MethodNode getMethodNodeFromMapping(ClassNode cn, String deobfMapping)
	{
		return DynamicMappings.getMethodNodeFromMapping(cn, deobfMapping);
	}
	
	@SuppressWarnings("rawtypes")
	public List<MethodNode> getMethodsWithDescriptor(List methods, String desc)
	{
		return DynamicMappings.getMethodsWithDescriptor(methods, desc);
	}
	
	public String assembleDescriptor(Object... objects)
	{
		return DynamicMappings.assembleDescriptor(objects);
	}	
	
	public <T> List<T> getAllInsnNodesOfType(AbstractInsnNode startInsn, Class<T> classType)
	{
		return DynamicMappings.getAllInsnNodesOfType(startInsn, classType);
	}
	
	public boolean searchConstantPoolForClasses(String className, String... matchStrings)
	{
		return DynamicMappings.searchConstantPoolForClasses(className, matchStrings);
	}
	
	public AbstractInsnNode[] getOpcodeSequenceArray(AbstractInsnNode insn, int...opcodes)
	{
		return DynamicMappings.getOpcodeSequenceArray(insn, opcodes);
	}		
	
	@SuppressWarnings("unchecked")
	public AbstractInsnNode[] getInsnNodeSequenceArray(AbstractInsnNode insn, Class<? extends AbstractInsnNode>...nodeClasses)
	{
		return DynamicMappings.getInsnNodeSequenceArray(insn, nodeClasses);
	}
	
	public AbstractInsnNode findNextOpcodeNum(AbstractInsnNode insn, int opcode)
	{
		return DynamicMappings.findNextOpcodeNum(insn, opcode);
	}
	
	public <T> T getNextInsnNodeOfType(AbstractInsnNode startInsn, Class<T> classType)
	{
		return DynamicMappings.getNextInsnNodeOfType(startInsn, classType);
	}
	
	public String getLdcClass(AbstractInsnNode node)
	{
		return DynamicMappings.getLdcClass(node);
	}	
	
	public FieldNode getFieldNode(ClassNode cn, String obfMapping)
	{
		return DynamicMappings.getFieldNode(cn, obfMapping);
	}
	
	public FieldNode getFieldNodeFromMapping(ClassNode cn, String deobfMapping)
	{
		return DynamicMappings.getFieldNodeFromMapping(cn, deobfMapping);
	}
		
	public boolean classHasInterfaces(ClassNode classNode, String... ifaces)
	{
		return DynamicMappings.classHasInterfaces(classNode, ifaces);
	}
	
	public boolean doesInheritFrom(String className, String inheritFrom)
	{
		return DynamicMappings.doesInheritFrom(className, inheritFrom);
	}
	
	
	public boolean searchConstantPoolForFields(String className, String...fields)
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
			if (pos == 0 || reader.b[pos - 1] != 9) continue;
			String owner = reader.readClass(pos, buffer);
			pos = reader.getItem(reader.readUnsignedShort(pos + 2));
			String name = reader.readUTF8(pos, buffer);
			String desc = reader.readUTF8(pos + 2, buffer);

			String fieldRef = owner + " " + name + " " + desc;
			
			for (int n2 = 0; n2 < fields.length; n2++) {
				if (fieldRef.equals(fields[n2].replace(".", "/"))) { matches++; break; }
			}
		}

		return (matches == fields.length);		
	}
	
	
	public String getSoundField(String sound)
	{
		String full = getSoundFieldFull(sound);
		if (full == null) return null;
		
		return full.split(" ")[1];
		
	}
	
	public String getSoundFieldFull(String sound)
	{
		sound = sound.replace(".", "_");
		return DynamicMappings.getFieldMapping("net/minecraft/init/Sounds " + sound + " Lnet/minecraft/util/Sound;");
	}
	

	public <T> List<T> getAllInsnNodesOfType(MethodNode method, Class<T> classType)
	{
		return DynamicMappings.getAllInsnNodesOfType(method.instructions.getFirst(), classType);
	}	
	
	public <T> List<T> getAllInsnNodesOfType(ClassNode classNode, Class<T> classType)
	{
		List<T> output = new ArrayList<>();
		if (classNode.methods == null) return output;
		
		for (MethodNode method : classNode.methods) {
			output.addAll(getAllInsnNodesOfType(method, classType));
		}
		return output;
	}
	
	
	@SuppressWarnings("unchecked")
	public boolean matchInsnNodeSequence(AbstractInsnNode insn, Class<? extends AbstractInsnNode>...nodeClasses)
	{
		return DynamicMappings.matchInsnNodeSequence(insn, nodeClasses);
	}
	
	
	public List<FieldInsnNode> filterFieldInsnNodes(List<FieldInsnNode> list, String owner, String desc)
	{
		List<FieldInsnNode> output = new ArrayList<>();
		for (FieldInsnNode fn : list) {
			if (owner != null && !fn.owner.equals(owner)) continue;
			if (desc != null && !fn.desc.equals(desc)) continue;
			output.add(fn);
		}
		return output;
	}
	
	
	public List<MethodInsnNode> filterMethodInsnNodes(List<MethodInsnNode> list, String owner, String name, String desc)
	{
		List<MethodInsnNode> output = new ArrayList<>();
		for (MethodInsnNode mn : list) {
			if (owner != null && !mn.owner.equals(owner)) continue;
			if (name != null && !mn.name.equals(name)) continue;
			if (desc != null && !mn.desc.equals(desc)) continue;
			output.add(mn);
		}
		return output;
	}
	
	public List<MethodInsnNode> filterMethodInsnNodes(List<MethodInsnNode> list, String owner, String desc)
	{
		return filterMethodInsnNodes(list, owner, null, desc);
	}
	
	public List<MethodInsnNode> filterMethodInsnNodes(List<MethodInsnNode> list, String owner, MethodNode node)
	{
		return filterMethodInsnNodes(list, owner, node.name, node.desc);
	}
	
	
	public int countFieldsWithDesc(ClassNode cn, String obfDesc)
	{
		int count = 0;
		for (FieldNode fn : cn.fields) {
			if (fn.desc.equals(obfDesc)) count++;
		}
		return count;
	}


	public List<String> getStringsFromMethod(MethodNode method)
	{
		return DynamicMappings.getStringsFromMethod(method);
	}


	public boolean doesMethodContainString(MethodNode method, String string)
	{
		return DynamicMappings.doesMethodContainString(method, string);
	}


	public List<MethodNode> getMethodsContainingString(ClassNode cn, String string)
	{
		return DynamicMappings.getMethodsContainingString(cn, string);
	}

	
	public boolean doesMethodUseField(MethodNode method, String owner, String name, String desc)
	{
		return DynamicMappings.doesMethodUseField(method, owner, name, desc);
	}
	
	
	public boolean doesMethodUseMethod(MethodNode method, String owner, String name, String desc)
	{
		return DynamicMappings.doesMethodUseMethod(method, owner, name, desc);
	}
	
	
	public List<MethodNode> filterMethodsUsingField(List<MethodNode> paramMethods, String owner, String name, String desc)
	{
		return DynamicMappings.filterMethodsUsingField(paramMethods, owner, name, desc);
	}
	
	
	public Integer getLdcInteger(AbstractInsnNode node)
	{
		return DynamicMappings.getLdcInteger(node);
	}
	
	
	public Float getLdcFloat(AbstractInsnNode node)
	{
		return DynamicMappings.getLdcFloat(node);
	}

	
	public List<Integer> getIntegersFromMethod(MethodNode method)
	{
		return DynamicMappings.getIntegersFromMethod(method);
	}
	
	
	public List<Float> getFloatsFromMethod(MethodNode method)
	{
		return DynamicMappings.getFloatsFromMethod(method);
	}
	
	
	public boolean doesMethodContainInteger(MethodNode method, int i)
	{
		return DynamicMappings.doesMethodContainInteger(method, i);
	}

	
	public List<MethodNode> filterMethodsUsingMethod(List<MethodNode> paramMethods, String owner, String name, String desc)
	{
		return DynamicMappings.filterMethodsUsingMethod(paramMethods, owner, name, desc);
	}
}
