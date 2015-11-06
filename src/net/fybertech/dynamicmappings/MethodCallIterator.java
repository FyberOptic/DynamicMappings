package net.fybertech.dynamicmappings;

import java.util.Iterator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class MethodCallIterator implements Iterator<MethodCallIterator.MethodCall> {

	private MethodNode method;
	private AbstractInsnNode insn;
	private MethodCallIterator.MethodCall next;
	
	private boolean printWarnings;
	private boolean printDebug;
	private int index;
	private Object[] stack;
	private Object[] vars;
	private int sp;

	public MethodCallIterator(MethodNode method, boolean printWarnings, boolean printDebug) {
		this.method = method;
		this.insn = this.method.instructions.getFirst();
		this.stack = new Object[100];
		this.vars = new Object[100];
		this.sp = 0;
		this.index = 0;
		this.printWarnings = printWarnings;
		this.printDebug = printDebug;
		this.next = null;
		this.loadNext();
	}
	
	@Override
	public boolean hasNext() {
		return this.next != null;
	}

	@Override
	public MethodCallIterator.MethodCall next() {
		MethodCallIterator.MethodCall result = this.next;
		this.loadNext();
		return result;
	}
	
	private boolean loadNext() {
		// Loop through the contents of registerBlocks and pull out any calls to registerBlock.
		// Then map the block ids and class names based on the parameters to registerBlock from
		// the mapping provided above.
		
		boolean foundNext = false;
		for (; insn != null && !foundNext; insn = insn.getNext()) 
		{
			if (insn instanceof LabelNode || insn instanceof LineNumberNode) {
				// skip label/line numbers
				index++;
				continue;
			}
			
			if (insn instanceof InsnNode) {
				// handle integer / float constants, just push to virtual stack
				
				int opCode = ((InsnNode)insn).getOpcode();
				switch (opCode) {
					case Opcodes.ICONST_0:
						stack[sp++] = 0;
						break;
					case Opcodes.ICONST_1:
						stack[sp++] = 1;
						break;
					case Opcodes.ICONST_2:
						stack[sp++] = 2;
						break;
					case Opcodes.ICONST_3:
						stack[sp++] = 3;
						break;
					case Opcodes.ICONST_4:
						stack[sp++] = 4;
						break;
					case Opcodes.ICONST_5:
						stack[sp++] = 5;
						break;
					case Opcodes.DUP:		// duplicate top of stack
						stack[sp] = stack[sp - 1];
						sp++;
						break;
					case Opcodes.FCONST_0:
						stack[sp++] = 0f;
						break;
					case Opcodes.FCONST_1:
						stack[sp++] = 1f;
						break;
					case Opcodes.FCONST_2:
						stack[sp++] = 2f;
						break;
					default:
						if (printWarnings) System.out.println("WARNING: discoverBlocks Unhandled InsnNode opcode: " + opCode);
						break;
				}
			} else if (insn instanceof FieldInsnNode) {
				// handle static field references, just push to virtual stack
				
				FieldInsnNode fi = (FieldInsnNode)insn;
				int opCode = fi.getOpcode();
				switch (opCode) {
					case Opcodes.GETSTATIC:
						stack[sp++] = fi.name;
						break;
					default:
						if (printWarnings) System.out.println("WARNING: discoverBlocks Unhandled FieldInsnNode opcode: " + opCode);
						break;
				}
				
			} else if (insn instanceof TypeInsnNode) {
				// handle type references, push new instance to virtual stack
				
				TypeInsnNode ti = (TypeInsnNode)insn;
				int opCode = ti.getOpcode();
				switch (opCode) {
					case Opcodes.NEW:
						stack[sp++] = ti.desc;
						break;
					case Opcodes.ANEWARRAY:
						stack[sp++] = ti.desc;
						break;
					default:
						if (printWarnings) System.out.println("WARNING: discoverBlocks Unhandled TypeInsnNode opcode: " + opCode);
						break;
				}

			} else if (insn instanceof MethodInsnNode) {
				// handle method invocations, pop appropriate number of arguments off of virtual stack
				// and check if method is registerBlock; if registerBlock is called then check class
				// mapping and add if block id is found.
				
				MethodInsnNode mi = (MethodInsnNode)insn;
				int argCount = argCount(mi.desc);
				
				if (insn.getOpcode() == Opcodes.INVOKESPECIAL) argCount++;	 // constructor consumes one item from the stack
				
				if (printDebug) System.out.print(index + ": " + mi.name + " (");
				Object[] tempArgs = new Object[argCount];
				for (int i = argCount - 1; i >= 0; i--) {
					tempArgs[i] = stack[--sp];
					if (printDebug) System.out.print(stack[sp] + ",");
				}
				if (printDebug) System.out.println(")");
				
				this.next = new MethodCallIterator.MethodCall(mi, tempArgs);
				foundNext = true;

			} else if (insn instanceof LdcInsnNode) {
				// handle load constant value references, just push to virtual stack
				
				LdcInsnNode li = (LdcInsnNode)insn;
				stack[sp++] = li.cst;
				
			} else if (insn instanceof VarInsnNode) {
				// handle variable references, if it is a LOAD instruction then push to stack,
				// if STORE instruction then pop from stack into virtual variable storage
				
				VarInsnNode vi = (VarInsnNode)insn;
				int opCode = vi.getOpcode();
				switch (opCode) {
					case Opcodes.ALOAD:
						stack[sp++] = vars[vi.var];
						//System.out.println("ALOAD: " + vars[vi.var]);
						break;
					case Opcodes.ASTORE:
						vars[vi.var] = stack[--sp];
						//System.out.println("var" + vi.var + " = " + stack[sp]);
						break;
					default:
						if (printWarnings) System.out.println("WARNING: discoverBlocks Unhandled VarInsnNode opcode: " + opCode);
				}
				
			} else if (insn instanceof IntInsnNode) {
				// handle integer types, just push value to virtual stack
				
				IntInsnNode ii = (IntInsnNode)insn;
				int opCode = ii.getOpcode();
				switch (opCode) {
					case Opcodes.BIPUSH:
						stack[sp++] = ii.operand;
						break;
					case Opcodes.SIPUSH:
						stack[sp++] = ii.operand;
						break;
					default:
						if (printWarnings) System.out.println("WARNING: discoverBlocks Unhandled IntInsnNode opcode: " + opCode);
				}
				
			} else {
				// unhandled type, display warning for troubleshooting.
				
				if (printWarnings) System.out.println("WARNING: Unhandled IsnsNode " + index + ": " + insn.toString());
				
			}
			
		}
		
		if (!foundNext) this.next = null;
		return foundNext;
	}
	
	/**
	 * Counts the number of arguments that must be passed into a method
	 * based on the method signature provided.
	 * @param methodDesc
	 * @return
	 */
	public static int argCount(String methodDesc) {
		int count = 0;
		boolean complexType = false;
		for (int i = 1; i < methodDesc.length() && methodDesc.charAt(i) != ')'; i++) {
			if (complexType) {
				if (methodDesc.charAt(i) == ';') {
					complexType = false;
					count++;
				}
				continue;
			}
			if (methodDesc.charAt(i) == '[' || methodDesc.charAt(i) == 'L')
				complexType = true;
			else
				count++;
		}
		return count;
	}
	
	public class MethodCall {
		public MethodInsnNode methodNode;
		public Object[] args;
		
		public MethodCall(MethodInsnNode methodNode, Object[] args) {
			this.methodNode = methodNode;
			this.args = args;
		}
	}
}
