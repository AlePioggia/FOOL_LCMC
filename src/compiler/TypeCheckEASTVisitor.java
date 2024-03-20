package compiler;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

import java.util.List;

import static compiler.TypeRels.*;

//visitNode(n) fa il type checking di un Node n e ritorna:
//- per una espressione, il suo tipo (oggetto BoolTypeNode o IntTypeNode)
//- per una dichiarazione, "null"; controlla la correttezza interna della dichiarazione
//(- per un tipo: "null"; controlla che il tipo non sia incompleto) 
//
//visitSTentry(s) ritorna, per una STentry s, il tipo contenuto al suo interno

/**
 * Una volta arricchito l'albero con le stEntry, posso procedere con il type checking.
 * Typechecking funzioni:
 * - il tc delle espressioni usate come argomento ci dovrà dare, per ogni espressione un sottotipo del tipo del
 * corrispondente parametro.
 *
 * I metodi visit visitano l'AST non i nodi dell'EAST.
 * */
public class TypeCheckEASTVisitor extends BaseEASTVisitor<TypeNode,TypeException> {

	TypeCheckEASTVisitor() { super(true); } // enables incomplete tree exceptions 
	TypeCheckEASTVisitor(boolean debug) { super(true,debug); } // enables print for debugging

	//checks that a type object is visitable (not incomplete) 
	private TypeNode ckvisit(TypeNode t) throws TypeException {
		visit(t);
		return t;
	} 

	/**
	 * Invece qui ho una parte di dichiarazione, poi l'espressione.
	 * Il tipo che dobbiamo ritornare, è il tipo della visita del corpo.
	 *
	 * Quando visito una dichiarazione, nel type checking faccio controlli interni, perché:
	 * - se è una dichiarazione di variabile, devo controllare che l'espressione con cui inizializzo la variabile sia un sottotipo del tipo dichiarato per la variabile;
	 * - se è una funzione devo fare altre robe
	 *
	 * In ogni caso, la visit di una dichiarazione non deve tornare nulla.
	 *
	 * Con il try catch gestisco gli errori di tipo, per consentire che la compilazione continui.
	 * */
	@Override
	public TypeNode visitNode(ProgLetInNode n) throws TypeException {
		if (print) printNode(n);
		for (Node dec : n.declist)
			try {
				visit(dec); //controlli interni che abbiamo citato (le visite tornano null)
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		//visito il corpo del programma e ne ritorno il tipo
		return visit(n.exp);
	}

	/**
	 * Programma senza dichiarazioni
	 * */
	@Override
	public TypeNode visitNode(ProgNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}

	/**
	 * Controlliamo che tutte le dichiarazioni locali siano consistenti (durante la dichiarazione non serve verificare
	 * che i parametri siano corretti, la verifica avviene durante la chiamata).
	 *
	 * Poi però c'è il corpo della funzione (che è una espressione), devo verificare che sia compatibile con il tipo
	 * di ritorno dichiarato della funzione.
	 *
	 * Gestione errori nelle dichiarazioni:
	 * Li catturo, per consentire il continuo della visita.
	 * */
	@Override
	public TypeNode visitNode(FunNode n) throws TypeException {
		if (print) printNode(n,n.id);
		for (Node dec : n.declist)
			try {
				visit(dec);
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a function declaration: " + e.text);
			}
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) )  //Qui faccio il check sul tipo di ritorno
			throw new TypeException("Wrong return type for function " + n.id,n.getLine());
		return null;
	}

	@Override
	public TypeNode visitNode(MethodNode n) throws TypeException {
		if (print) printNode(n,n.id);
		for (Node dec : n.declist)
			try {
				visit(dec);
			} catch (IncomplException e) {
			} catch (TypeException e) {
				System.out.println("Type checking error in a method declaration: " + e.text);
			}
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) )
			throw new TypeException("Wrong return type for function " + n.id,n.getLine());
		return null;
	}

	/**
	 * @implNote check visit method, not sure if visitNode() or visit()
	 * */
	@Override
	public TypeNode visitNode(ClassNode n) throws TypeException {
		if (print) printNode(n, n.classId);
		for (var method: n.methods) {
			visitNode(method);
		}
		return null;
	}

	/**
	 * es:
	 * type: Int
	 * La visita dell'espressione mi dà il tipo dell'espressione, devo verificare che sia il sotto-tipo di type
	 * */
	@Override
	public TypeNode visitNode(VarNode n) throws TypeException {
		if (print) printNode(n,n.id);
		if ( !isSubtype(visit(n.exp),ckvisit(n.getType())) )
			throw new TypeException("Incompatible value for variable " + n.id,n.getLine());
		return null;
	}

	@Override
	public TypeNode visitNode(PrintNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}

	/**
	 * Visitiamo condizione, then ed else.
	 * Condizione -> deve essere booleana;
	 *
	 * Siano ford e car. Nel caso dell'if-else devo ritornare il sopra-tipo.
	 * Molto banalmente, torniamo un tipo che vada bene per entrambi i possibili valori ritornati.
	 * */
	@Override
	public TypeNode visitNode(IfNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.cond), new BoolTypeNode())) )
			throw new TypeException("Non boolean condition in if",n.getLine());
		TypeNode t = visit(n.th);
		TypeNode e = visit(n.el);
		if (isSubtype(t, e)) return e;
		if (isSubtype(e, t)) return t;
		throw new TypeException("Incompatible types in then-else branches",n.getLine());
	}

	/**
	 * In java, se x e y
	 * - non hanno nessuna relazione di subtyping, non si può fare il confronto
	 * - se c'è una relazione di subtyping, il confronto si può fare
	 *
	 * Stessa cosa succede nel ge e le.
	 *
	 * Ho due superclassi, x e y. Una classe c le implementa entrambe, ed ha una istanza di una e dell'altra, il confronto si può effettuare?
	 * Sì.
	 *
	 * Quindi java consente il confronto se:
	 * - c'è una sotto-classe in comune;
	 * - c'è un rapporto di ereditarietà
	 *
	 * Il caso del sotto-tipo comune non lo consideriamo, perché non useremo l'ereditarietà multipla.
	 *
	 * */
	@Override
	public TypeNode visitNode(EqualNode n) throws TypeException {
		if (print) printNode(n);
		TypeNode l = visit(n.left);
		TypeNode r = visit(n.right);
		if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(TimesNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(PlusNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in sum",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(GreaterEqualNode n) throws TypeException {
		if (print) printNode(n);
		TypeNode l = visit(n.left);
		TypeNode r = visit(n.right);
		if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			throw new TypeException("Incompatible types in greater or equal",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(LessEqualNode n) throws TypeException {
		if (print) printNode(n);
		TypeNode l = visit(n.left);
		TypeNode r = visit(n.right);
		if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			throw new TypeException("Incompatible types in less or equal",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(NotNode n) throws TypeException {
		if (print) printNode(n);
		if ( !isSubtype(visit(n.node), new BoolTypeNode())) {
			throw new TypeException("Non boolean in not condition", n.getLine());
		}
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(MinusNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in sub",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(OrNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new BoolTypeNode())
				&& isSubtype(visit(n.right), new BoolTypeNode())) )
			throw new TypeException("Non booleans in or",n.getLine());
		return new BoolTypeNode();
	}
	
	@Override
	public TypeNode visitNode(DivNode n) throws TypeException {
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in div",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(AndNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new BoolTypeNode())
				&& isSubtype(visit(n.right), new BoolTypeNode())) )
			throw new TypeException("Non booleans in AND",n.getLine());
		return new BoolTypeNode();
	}

	/**
	 * specie di chiamata a funzione.
	 * Devo controllare che gli argomenti siano consistenti con i parametri formali.
	 * Le dichiarazioni dei tipi dei campi le trovo dentro il classTypeNode di ID.
	 * */
	@Override
	public TypeNode visitNode(NewNode n) throws TypeException {
		if (print) printNode(n, n.id);
		if (!(visit(n.sTentry) instanceof ClassTypeNode)) {
			throw new TypeException("Inconsistent type " + n.id, n.getLine());
		}
		List<TypeNode> parList = ((ClassTypeNode) n.sTentry.type).allFields;
		if ( !(parList.size() == n.args.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.args.size(); i++)
			if ( !(isSubtype(visit(n.args.get(i)),parList.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		return new RefTypeNode(n.id);
	}

	@Override
	public TypeNode visitNode(EmptyNode n) throws TypeException {
		if (print) printNode(n);
		return new EmptyTypeNode();
	}

	@Override
	public TypeNode visitNode(CallNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry);
		if ((t instanceof MethodTypeNode)) {
			t = ((MethodTypeNode) t).arrowTypeNode;
		}
		if ( !(t instanceof ArrowTypeNode)) {
			throw new TypeException("Invocation of a non-function/method "+n.id,n.getLine());
		}
		ArrowTypeNode at = (ArrowTypeNode) t;
		if ( !(at.parlist.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		return at.ret;
	}

	@Override
	public TypeNode visitNode(ClassCallNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry);
		if ((t instanceof MethodTypeNode)) {
			t = ((MethodTypeNode) t).arrowTypeNode;
		}
		if ( !(t instanceof ArrowTypeNode)) {
			throw new TypeException("Invocation of a non-function/method "+n.id,n.getLine());
		}
		ArrowTypeNode at = (ArrowTypeNode) t;
		if ( !(at.parlist.size() == n.args.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.args.size(); i++)
			if ( !(isSubtype(visit(n.args.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		return at.ret;
	}

	/**
	 * Ho attaccato al node la stEntry, così ho tutte le informazioni sulla dichiarazione
	 * è contenuto dentro n.entry.type.  Può essere un parametro o una variabile, per questo controllo
	 * che non sia una funzione.
	 * caso: x + 7 ma x può essere anche una funzione!
	 * Lo capisco entrando nella stEntry (si visita) e da lì capisco il tipo della dichiarazione
	 * */
	@Override
	public TypeNode visitNode(IdNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry); 
		if (t instanceof ArrowTypeNode)
			throw new TypeException("Wrong usage of function identifier " + n.id,n.getLine());
		if (t instanceof ClassTypeNode)
			throw new TypeException("Wrong usage of identifier, class type name spotted" + n.id, n.getLine());
		return t;
	}

	@Override
	public TypeNode visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return new IntTypeNode();
	}

// gestione tipi incompleti	(se lo sono lancia eccezione)
	
	@Override
	public TypeNode visitNode(ArrowTypeNode n) throws TypeException {
		if (print) printNode(n);
		for (Node par: n.parlist) visit(par);
		visit(n.ret,"->"); //marks return type
		return null;
	}

	@Override
	public TypeNode visitNode(BoolTypeNode n) {
		if (print) printNode(n);
		return null;
	}

	@Override
	public TypeNode visitNode(IntTypeNode n) {
		if (print) printNode(n);
		return null;
	}

// STentry (ritorna campo type)

	@Override
	public TypeNode visitSTentry(STentry entry) throws TypeException {
		if (print) printSTentry("type");
		return ckvisit(entry.type); 
	}

}