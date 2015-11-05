package net.fybertech.dynamicmappings;

import java.util.Iterator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class MethodCallVisitor implements Iterable<MethodCallIterator.MethodCall> {

	private MethodNode method;
	private boolean printWarnings;
	
	public MethodCallVisitor(MethodNode method, boolean printWarnings) {
		this.method = method;
		this.printWarnings = printWarnings;
	}

	@Override
	public Iterator<MethodCallIterator.MethodCall> iterator() {
		return new MethodCallIterator(this.method, this.printWarnings, false);
	}

}
