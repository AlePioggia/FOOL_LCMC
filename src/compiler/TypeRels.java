package compiler;

import compiler.AST.*;
import compiler.lib.*;

import java.util.HashMap;
import java.util.Map;

public class TypeRels {

	// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode

	public static Map<String,String> superType = new HashMap<>();

	/**
	 * Consideriamo i booleani essere sottotipo degli interi con l'interpretazione: true vale 1 e false vale 0.
	 * */

	public static boolean isSubtype(TypeNode a, TypeNode b) {
		if (a instanceof RefTypeNode && b instanceof RefTypeNode) {
			String directSuperType = ((RefTypeNode) a).id;
			while (directSuperType != null && !directSuperType.equals(((RefTypeNode) b).id)) {
				directSuperType = superType.get(directSuperType);
			}
			return ((RefTypeNode) a).id.equals(((RefTypeNode) b).id) || directSuperType != null;
		}
		if ((a instanceof ArrowTypeNode) && (b instanceof ArrowTypeNode)){
			for (int i=0; i<((ArrowTypeNode) a).parlist.size(); i++) {
				if(!isSubtype(((ArrowTypeNode) b).parlist.get(i), ((ArrowTypeNode) a).parlist.get(i))){
					return false;
				}
			}
			return isSubtype(((ArrowTypeNode) a).ret, ((ArrowTypeNode) b).ret);
		}
		return a.getClass().equals(b.getClass()) || ((a instanceof BoolTypeNode) && (b instanceof IntTypeNode))
				|| (a instanceof EmptyTypeNode);
	}

	public static TypeNode lowestCommonAncestor(TypeNode a, TypeNode b) {
		// Check if both a and b are either RefTypeNode or EmptyTypeNode
		if ((a instanceof RefTypeNode && b instanceof RefTypeNode) ||
				(a instanceof EmptyTypeNode || b instanceof EmptyTypeNode)) {

			// If one of them is EmptyTypeNode, return the other
			if (a instanceof EmptyTypeNode) {
				return b;
			} else if (b instanceof EmptyTypeNode) {
				return a;
			}

			// If both are RefTypeNode but with the same id, return a
			if (((RefTypeNode) a).id.equals(((RefTypeNode) b).id)) {
				return a;
			}

			// Find the common supertype if exists
			String type = superType.get(((RefTypeNode) a).id);
			while (type != null && isSubtype(b, new RefTypeNode(type))) {
				type = superType.get(type);
			}

			// If a common supertype is found, return it as a RefTypeNode
			return (type != null) ? new RefTypeNode(type) : null;

		} else if ((a instanceof BoolTypeNode || a instanceof IntTypeNode) &&
				(b instanceof BoolTypeNode || b instanceof IntTypeNode)) {
			// If both a and b are either BoolTypeNode or IntTypeNode

			// If either a or b is IntTypeNode, return IntTypeNode, otherwise return BoolTypeNode
			return (a instanceof IntTypeNode || b instanceof IntTypeNode) ? new IntTypeNode() : new BoolTypeNode();
		}

		// If no common ancestor found, return null
		return null;
	}

}
