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

}
