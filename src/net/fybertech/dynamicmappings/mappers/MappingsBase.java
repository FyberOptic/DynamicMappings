package net.fybertech.dynamicmappings.mappers;

import java.util.List;

import net.fybertech.dynamicmappings.DynamicMappings;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
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
}
