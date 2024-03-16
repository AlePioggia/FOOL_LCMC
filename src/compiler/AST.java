package compiler;

import java.util.*;
import compiler.lib.*;

/**
 * Ogni classe dell'AST è statica per renderla pubblica e farla stare in un unico file.
 * Tutti i file estendono la classe astratta node.
 * Vogliamo mantenere un ordinamento, i primi metodi sono relativi alla radice, scendendo invece vado verso le foglie.
 * */

public class AST {
	
	public static class ProgLetInNode extends Node {
		final List<DecNode> declist;
		final Node exp;
		ProgLetInNode(List<DecNode> d, Node e) {
			declist = Collections.unmodifiableList(d); 
			exp = e;
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	/**
	 * Radice dell'albero, 'unico parametro è il corpo del programma.
	 * */
	public static class ProgNode extends Node {
		final Node exp;
		ProgNode(Node e) {exp = e;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class FunNode extends DecNode {
		final String id;//nome
		final TypeNode retType;//tipo di ritorno
		final List<ParNode> parlist;//lista di parametri
		final List<DecNode> declist; //lista di dichiarazioni locali (quello che è dentro il let)
		final Node exp; // corpo della funzione
		FunNode(String i, TypeNode rt, List<ParNode> pl, List<DecNode> dl, Node e) {
	    	id=i; 
	    	retType=rt; 
	    	parlist=Collections.unmodifiableList(pl); 
	    	declist=Collections.unmodifiableList(dl); 
	    	exp=e;
	    }
		
		//void setType(TypeNode t) {type = t;}
		
		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	public static class ParNode extends DecNode {
		final String id;
		ParNode(String i, TypeNode t) {id = i; type = t;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class VarNode extends DecNode {
		final String id;
		final Node exp;
		VarNode(String i, TypeNode t, Node v) {id = i; type = t; exp = v;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
		
	public static class PrintNode extends Node {
		final Node exp;
		PrintNode(Node e) {exp = e;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class IfNode extends Node {
		final Node cond;
		final Node th;
		final Node el;
		IfNode(Node c, Node t, Node e) {cond = c; th = t; el = e;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class EqualNode extends Node {
		final Node left;
		final Node right;
		EqualNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class TimesNode extends Node {
		final Node left;
		final Node right;
		TimesNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	public static class GreaterEqualNode extends Node {
		final Node left;
		final Node right;
		GreaterEqualNode(Node l, Node r) { left = l; right = r; }

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class LessEqualNode extends Node {
		final Node left;
		final Node right;
		LessEqualNode(Node l, Node r) { left = l; right = r; }

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class OrNode extends Node {
		final Node left;
		final Node right;
		OrNode(Node l, Node r) { left = l; right = r; }

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class AndNode extends Node {
		final Node left;
		final Node right;
		AndNode(Node l, Node r) { left = l; right = r; }

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class DivNode extends Node {
		final Node left;
		final Node right;
		DivNode(Node l, Node r) { left = l; right = r; }

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class NotNode extends Node {
		final Node node;

		NotNode(Node n) {
			node = n;
		}

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}


	public static class PlusNode extends Node {
		final Node left;
		final Node right;
		PlusNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	public static class MinusNode extends Node {
		final Node left;
		final Node right;

		MinusNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class CallNode extends Node {
		final String id;
		final List<Node> arglist;
		STentry entry;
		int nl;
		CallNode(String i, List<Node> p) {
			id = i; 
			arglist = Collections.unmodifiableList(p);
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class IdNode extends Node {
		final String id;
		STentry entry;
		int nl;
		IdNode(String i) {id = i;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class BoolNode extends Node {
		final Boolean val;
		BoolNode(boolean n) {val = n;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class IntNode extends Node {
		final Integer val;
		IntNode(Integer n) {val = n;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	/**
	 * serve per rappresentare il tipo di una funzione (freccia),
	 * che contiene le informazioni corrispondenti alla notazione per i tipi funzionali.
	 * (T1...Tn parametri) -> T (tipo di ritorno)
	 *
	 * */
	public static class ArrowTypeNode extends TypeNode {
		final List<TypeNode> parlist;
		final TypeNode ret;
		ArrowTypeNode(List<TypeNode> p, TypeNode r) {
			parlist = Collections.unmodifiableList(p); 
			ret = r;
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class BoolTypeNode extends TypeNode {

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	public static class IntTypeNode extends TypeNode {

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	public static class ClassNode extends DecNode {

		final String classId;
		final List<FieldNode> fields;
		final List<MethodNode> methods;
		final TypeNode superId;
		ClassTypeNode classType;

		public ClassNode(String id, List<FieldNode> fields, List<MethodNode> methods, TypeNode superId) {
			this.classId = id;
			this.fields = Collections.unmodifiableList(fields);
			this.methods = Collections.unmodifiableList(methods);
			this.superId = superId;
		}

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class FieldNode extends DecNode {
		final String id;
		int offset;
		public FieldNode(String i, TypeNode t) {
			id = i;
			type = t;
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}


	public static class MethodNode extends DecNode {

		final String id;//nome
		final TypeNode retType;//tipo di ritorno
		final List<ParNode> parlist;//lista di parametri
		final List<DecNode> declist; //lista di dichiarazioni locali (quello che è dentro il let)
		final Node exp; // corpo della funzione
		int offset;

		public MethodNode(String i, TypeNode rt, List<ParNode> pl, List<DecNode> dl, Node e) {
			id=i;
			retType=rt;
			parlist=Collections.unmodifiableList(pl);
			declist=Collections.unmodifiableList(dl);
			exp=e;
		}

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class NewNode extends Node {

		final String id;
		final List<Node> args;

		public NewNode(String id, List<Node> args) {
			this.id = id;
			this.args = args;
		}

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class EmptyNode extends Node {

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class ClassCallNode extends Node {

		final String id;
		final List<TypeNode> args;

		public ClassCallNode(String id, List<TypeNode> args) {
			this.id = id;
			this.args = args;
		}

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class RefTypeNode extends TypeNode {
		final String id; //object reference

		public RefTypeNode(String id) {
			this.id = id;
		}

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class EmptyTypeNode extends TypeNode {

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class ClassTypeNode extends TypeNode {
		final List<TypeNode> allFields;
		final List<ArrowTypeNode> allMethods;

		public ClassTypeNode(List<TypeNode> fields, List<ArrowTypeNode> methods) {
			this.allFields = fields;
			this.allMethods = methods;
		}

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

	public static class MethodTypeNode extends TypeNode {
		final ArrowTypeNode arrowTypeNode;


		public MethodTypeNode(final ArrowTypeNode arrowTypeNode) {
			this.arrowTypeNode = arrowTypeNode;
		}

		@Override
		public <S, E extends Exception> S accept(BaseASTVisitor<S, E> visitor) throws E {
			return visitor.visitNode(this);
		}
	}

}