package compiler.lib;

import compiler.AST.*;
import compiler.exc.*;

import static compiler.lib.FOOLlib.*;

/*
Attraverso l'uso dei generici, abbiamo potuto utilizzare anche il void, ritornando semplicemente null.
Questo perché, ovviamente nella print non devo ritornare nessun valore.
Ahimé return null è una sfortuna tecnica.

Abilitando il debug ho la stampa dell'albero fino all'eccezione, oltre all'eccezione stessa.
**/

/**
 * La gestione del meccanismo di stampa è presente in BaseVisitor
 * Ogni metodo di visita lancia un'eccezione
 * */

public class BaseASTVisitor<S,E extends Exception> {

	private boolean incomplExc; // enables throwing IncomplException
	protected boolean print;    // enables printing
	protected String indent;

	protected BaseASTVisitor() {}
	protected BaseASTVisitor(boolean ie) { incomplExc = ie; }
	protected BaseASTVisitor(boolean ie, boolean p) { incomplExc = ie; print = p; }

	protected void printNode(Node n) {
		System.out.println(indent+extractNodeName(n.getClass().getName()));
	}

	/*Tramite la reflection di java prendono il nome del nodo**/
	protected void printNode(Node n, String s) {
		System.out.println(indent+extractNodeName(n.getClass().getName())+": "+s);
	}

	public S visit(Visitable v) throws E {
		return visit(v, "");                //performs unmarked visit
	}

	public S visit(Visitable v, String mark) throws E {   //when printing marks this visit with string mark
		if (v==null)
			if (incomplExc) throw new IncomplException();
			else
				return null;
		if (print) {
			String temp = indent;
			indent = (indent == null) ? "" : indent + "  ";
			indent+=mark; //inserts mark
			try {
				S result = visitByAcc(v);
				return result;
			} finally { indent = temp; } //ripristino dell'indentazione, serve nel caso in cui tiri l'eccezione
		} else
			return visitByAcc(v);
	}

	/*
	* Questo chiama l'accept, che chiama la visit specifica e torna un valore. Poi torno il valore ritornato
	* dall'accept. Quindi se ho un Integer, torna un integer
	* */
	S visitByAcc(Visitable v) throws E {
		return v.accept(this);
	}

	public S visitNode(ProgLetInNode n) throws E {throw new UnimplException();}
	public S visitNode(ProgNode n) throws E {throw new UnimplException();}
	public S visitNode(FunNode n) throws E {throw new UnimplException();}
	public S visitNode(ParNode n) throws E {throw new UnimplException();}
	public S visitNode(VarNode n) throws E {throw new UnimplException();}
	public S visitNode(PrintNode n) throws E {throw new UnimplException();}
	public S visitNode(IfNode n) throws E {throw new UnimplException();}
	public S visitNode(EqualNode n) throws E {throw new UnimplException();}
	public S visitNode(TimesNode n) throws E {throw new UnimplException();}
	public S visitNode(PlusNode n) throws E {throw new UnimplException();}
	public S visitNode(CallNode n) throws E {throw new UnimplException();}
	public S visitNode(IdNode n) throws E {throw new UnimplException();}
	public S visitNode(BoolNode n) throws E {throw new UnimplException();}
	public S visitNode(IntNode n) throws E {throw new UnimplException();}

	public S visitNode(ArrowTypeNode n) throws E {throw new UnimplException();}
	public S visitNode(BoolTypeNode n) throws E {throw new UnimplException();}
	public S visitNode(IntTypeNode n) throws E {throw new UnimplException();}

	// OPERATOR EXTENSION

	public S visitNode(GreaterEqualNode n) throws E {throw new UnimplException();}
	public S visitNode(LessEqualNode n) throws E {throw new UnimplException();}
	public S visitNode(NotNode n) throws E {throw new UnimplException();}
	public S visitNode(MinusNode n) throws E {throw new UnimplException();}
	public S visitNode(OrNode n) throws E {throw new UnimplException();}
	public S visitNode(DivNode n) throws E {throw new UnimplException();}
	public S visitNode(AndNode n) throws E {throw new UnimplException();}

	// OBJECT-ORIENTED EXTENSION

	public S visitNode(ClassNode n) throws E {throw new UnimplException();}
	public S visitNode(FieldNode node) throws E {throw new UnimplException();}
	public S visitNode(MethodNode n) throws E {throw new UnimplException();}
	public S visitNode(ClassCallNode node) throws E {throw new UnimplException();}
	public S visitNode(NewNode n) throws E {throw new UnimplException();}
	public S visitNode(EmptyNode n) throws E {throw new UnimplException();}

	public S visitNode(ClassTypeNode n) throws E {throw new UnimplException();}
	public S visitNode(MethodTypeNode n) throws E {throw new UnimplException();}
	public S visitNode(RefTypeNode n) throws E {throw new UnimplException();}
	public S visitNode(EmptyTypeNode n) throws E {throw new UnimplException();}

}
