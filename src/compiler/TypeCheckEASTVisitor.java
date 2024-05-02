package compiler;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

import java.util.List;
import java.util.stream.Stream;

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

	//controlla se un type object è visitabile (non incompleto)
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
		return visit(n.exp); //visito il corpo del programma e ne ritorno il tipo
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
	 *
	 * f(par) -> controllo che par sia corretto, solo nel momento in cui faccio la chiamata
	 * */
	@Override
	public TypeNode visitNode(FunNode n) throws TypeException {
		if (print) printNode(n,n.id);
		//visito tutte le dichiarazioni per verificare, induttivamente, che siano corrette
		for (Node dec : n.declist)
			try {
				visit(dec); // controllo sulla dichiarazione -> varNode
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a function declaration: " + e.text);
			}
		//Qui faccio il check sul tipo di ritorno
		if ( !isSubtype(visit(n.exp), ckvisit(n.retType)) )
			throw new TypeException("Wrong return type for function " + n.id,n.getLine());
		return null;
	}

	/**
	 * es:
	 * var name: Int = ...
	 * Devo controllare che l'assegnamento, sia coerente con la dichiarazione
	 * */
	@Override
	public TypeNode visitNode(VarNode n) throws TypeException {
		if (print) printNode(n,n.id);
		if ( !isSubtype(visit(n.exp), ckvisit(n.getType())) )
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
	 * Siano ford e car. Nel caso dell'if-then-else devo ritornare il sopra-tipo.
	 * Molto banalmente, torniamo un tipo che vada bene per entrambi i possibili valori ritornati.
	 * */
	@Override
	public TypeNode visitNode(IfNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.cond), new BoolTypeNode())) )
			throw new TypeException("Non boolean condition in if",n.getLine());
		TypeNode t = visit(n.th);
		TypeNode e = visit(n.el);
		TypeNode lowestCommonAncestor = lowestCommonAncestor(t, e); // trovo il sopratipo comune fra ciò che ritorna then e ciò che ritorna else
		if (lowestCommonAncestor == null) {
			throw new TypeException("Failed typecheking for if-else statement ", n.getLine());
		}
		return lowestCommonAncestor;
	}

	/**
	 * In java, se x e y
	 * - non hanno nessuna relazione di subtyping, non si può fare il confronto
	 * - se c'è una relazione di subtyping, il confronto si può fare
	 *
	 * Stessa cosa succede nel ge e le.
	 *
	 * Ho due superclassi, x e y. Una classe c le implementa entrambe (separatamente, quindi C implements X e C implements Y), ed ha una istanza di una e dell'altra, il confronto si può effettuare?
	 * Sì.
	 *
	 * Quindi java consente il confronto se:
	 * - c'è una sotto-classe in comune;
	 * - c'è un rapporto di ereditarietà
	 *
	 * Il caso del sotto-tipo comune non lo consideriamo, perché non useremo l'ereditarietà multipla.
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

	// ID()
	/**
	 * Devo recuperare l'entry type, deve essere un arrowTypeNode, se non lo è tiro l'eccezione
	 */
	@Override
	public TypeNode visitNode(CallNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry);
		if ((t instanceof MethodTypeNode)) { // se si tratta di una chiamata a metodo, aggiorno t
			t = ((MethodTypeNode) t).arrowTypeNode;
		}
		if ( !(t instanceof ArrowTypeNode)) {
			throw new TypeException("Invocation of a non-function/method "+n.id,n.getLine());
		}
		ArrowTypeNode at = (ArrowTypeNode) t;
		if ( !(at.parlist.size() == n.arglist.size()) ) // controllo il numero di argomenti
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++) // controllo il tipo di ogni argomento
			if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		return at.ret;
	}

	/**
	 * Ho attaccato al node la stEntry, così ho tutte le informazioni sulla dichiarazione
	 * il tipo è contenuto dentro n.entry.type. Può essere un parametro o una variabile, per questo controllo
	 * che non sia una funzione.
	 * caso: x + 7 ma x può essere anche una funzione!
	 * Lo capisco entrando nella stEntry (si visita) e da lì capisco il tipo della dichiarazione
	 * */
	@Override
	public TypeNode visitNode(IdNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry); // visito x
		if (t instanceof ArrowTypeNode) // controllo che non sia una funzione
			throw new TypeException("Wrong usage of function identifier " + n.id,n.getLine());
		if (t instanceof ClassTypeNode) // controllo che non sia una classe
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

	//Chiamata a funzione
	@Override
	public TypeNode visitNode(ArrowTypeNode n) throws TypeException {
		if (print) printNode(n);
		for (Node par: n.parlist) visit(par); // visito i parametri della funzione
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

/*
	*/
/*	*//*
*/
/**
	 * Possiamo assumere che i parametri siano corretti, perché verificato dal symbolTableVisitor
	 * */

	@Override
	public TypeNode visitNode(ClassNode n) throws TypeException {
		if (print) printNode(n, n.classId);
		for (var method: n.methods) { // verifico che i metodi siano dichiarati correttamente, in modo induttivo
			try {
				visit(method);
			} catch (TypeException e) {
				System.out.println("Type checking error in a class declaration: " + e.text);
			}
		}
		// Confronta il suo tipo ClassTypeNode in campo type, con quello del genitore, in campo superEntry,
		// per controllare che eventuali overriding siano corretti.
		if (n.superId != null) {
			superType.put(n.classId, n.superId); //OTTIMIZZAZIONE: utile per il calcolo del lowestCommonAncestor
			ClassTypeNode classTypeNode = n.classType; // recupero il classType della classe corrente
			ClassTypeNode superEntry = (ClassTypeNode) n.superEntry.type; // recupero il classType della classe da cui eredito

			// FIELDS
			List<TypeException> wrongFieldExceptions = n.fields.stream()
					.filter((field) -> -field.offset - 1 < superEntry.allMethods.size()) // attraverso il controllo sulla posizione (a sinistra), recupero solo i campi che fanno override
					.flatMap((field) -> { // In questo filtro, eseguo il controllo sul tipo, se si manifestano errori, vengono collezionati, in modo ordinato
						int position = -field.offset - 1;
						return !isSubtype(classTypeNode.allFields.get(position), superEntry.allFields.get(position))
								? Stream.of(new TypeException("Wrong type for field " + field.id, field.getLine()))
								: Stream.empty();
					})
					.toList();

			for (var exception: wrongFieldExceptions) {throw exception;} // se e solo se trovo eccezioni, le tiro

			// METHODS
			List<TypeException> wrongTypeForMethodExceptions = n.methods.stream()
					.filter((method) -> method.offset < superEntry.allMethods.size()) // similmente alla gestione dei metodi, faccio un controllo sulla posizione
					.flatMap((method) -> { //In questo filtro, eseguo il controllo sul tipo, se si manifestano errori, vengono collezionati in modo ordinati
						int position = method.offset;
						return !isSubtype(classTypeNode.allMethods.get(position), superEntry.allMethods.get(position))
								? Stream.of(new TypeException("Wrong type for method " + method.id, method.getLine()))
								: Stream.empty();
					})
					.toList();

			for (var exception: wrongTypeForMethodExceptions) {throw exception;} // se e solo se trovo eccezioni, le genero
		}
		return null;
	}


	@Override
	public TypeNode visitNode(MethodNode n) throws TypeException {
		if (print) printNode(n,n.id);
		//visito tutte le dichiarazioni per verificare, induttivamente, che siano corrette
		for (Node dec : n.declist)
			try {
				visit(dec);
			} catch (IncomplException e) {
			} catch (TypeException e) {
				System.out.println("Type checking error in a method declaration: " + e.text);
			}
		//Qui faccio il check sul tipo di ritorno
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) )
			throw new TypeException("Wrong return type for function " + n.id,n.getLine());
		return null;
	}

	/**
	 * ID1().ID2()
	 * Il fatto che la classe che sto referenziando sia consistente, è già stato controllato dal symbolTableVisitor (metodo classCallNode).
	 * Ovvero che ID1 sia di tipo RefTypeNode.
	 * */
	@Override
	public TypeNode visitNode(ClassCallNode node) throws TypeException {
		if (print) {printNode(node, node.id1+"."+node.id2);}
		TypeNode methodType = visit(node.methodEntry);
		if (!(methodType instanceof MethodTypeNode)) {
			throw new TypeException("Invocation of a non-method " + node.id2, node.getLine());
		}
		ArrowTypeNode arrowType = ((MethodTypeNode) methodType).arrowTypeNode;
		if (node.args.size() != arrowType.parlist.size()) {
			throw new TypeException("Wrong number of parameters in the invocation of " + node.id2, node.getLine());
		}
		for (var i = 0; i < node.args.size(); i++) {
			if (!isSubtype(visit(node.args.get(i)), arrowType.parlist.get(i))) {
				throw new TypeException(
						"Wrong type for " + (i+1) + "-th parameter in the invocation of " + node.id2, node.getLine()
				);
			}
		}
		return arrowType.ret;
	}

	/**
	 * specie di chiamata a funzione.
	 * Devo controllare che gli argomenti siano consistenti con i parametri formali.
	 * Le dichiarazioni dei tipi dei campi le trovo dentro il classTypeNode di ID.
	 * */
	@Override
	public TypeNode visitNode(NewNode n) throws TypeException {
		if (print) printNode(n, n.id); // id() -> riferimento classe
		TypeNode t = visit(n.sTentry); // devo verificare che il tipo id sia un ClassTypeNode
		if (!(t instanceof ClassTypeNode)) {
			throw new TypeException("Inconsistent type " + n.id, n.getLine());
		}
		ClassTypeNode ct = ((ClassTypeNode) t); // una volta appurato che si tratta di un classtypenode, eseguo il cast
		if (ct.allFields.size() != n.args.size()) { // Controllo 1: verifico che il numero di argomenti corrisponda con i parametri formali
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		}
		for (int i = 0; i < n.args.size(); i++) // Controllo 2: verifico, argomento per argomento, che il tipo del parametro sia consistente
			if (!(isSubtype(visit(n.args.get(i)), ct.allFields.get(i))))
				throw new TypeException("Wrong type for " + (i+1) + "-th parameter in the invocation of " + n.id,n.getLine());
		return new RefTypeNode(n.id);
	}

	@Override
	public TypeNode visitNode(EmptyNode n) throws TypeException {
		if (print) printNode(n);
		return new EmptyTypeNode();
	}

	@Override
	public TypeNode visitNode(ClassTypeNode n) throws TypeException {
		if (print) printNode(n);
		for (TypeNode f : n.allFields) visit(f); // visito e verifico se ci sono errori di tipo nei campi della classe
		for (ArrowTypeNode m : n.allMethods) visit(m); // visito e verifico se ci sono errori di tipo nei metodi
		return null;
	}

	@Override
	public TypeNode visitNode(MethodTypeNode node) throws TypeException {
		if (print) {printNode(node);}
		visit(node.arrowTypeNode); // in questo caso, chiamata a metodo identica a funzione
		return null;
	}

	@Override
	public TypeNode visitNode(RefTypeNode n) throws TypeException {
		if (print) printNode(n, n.id);
		return null;
	}

// STentry (ritorna campo type)

	// checks that a type object is visitable (not incomplete)
	@Override
	public TypeNode visitSTentry(STentry entry) throws TypeException {
		if (print) printSTentry("type");
		return ckvisit(entry.type); 
	}

}