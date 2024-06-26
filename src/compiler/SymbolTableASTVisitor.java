package compiler;

import java.util.*;
import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

/**
 * Utilizziamo una symbol table, scegliamo come realizzazione una lista
 * (ArrayList) di hashtable (HashMap),
 * per collegare usi a dichiarazioni.
 * Gli scope sono rappresentati dunque da tabelle che stanno dentro la lista.
 * Nella lista, avremo il fronte della lista che è lo scope attuale, in cui
 * siamo posizionati, poi via via le tabelle
 * degli scope in cui siamo racchiusi, fino ad arrivare allo scope globale.
 * Regole di scoping statico:
 * - a use of an identifier x matches the declaration in the most closely
 * enclosing scope (such that the declaration
 * precedes the use)
 * - inner scope identifier x declaration hides x declared in an outer scope.
 *
 * L'obiettivo di questa visita sarà verificare la correttezza del sorgente,
 * quindi dare i seguenti errori:
 * - dichiarazioni multiple o identificatori non dichiarati;
 * - nel caso in cui riusciamo a stabilire un'associazione fra uso e
 * dichiarazione,
 * dobbiamo attaccare alla foglia dell'AST che rappresenta l'uso di un
 * identificatore x,
 * la symbol table entry (oggetto di classe STentry) che contiene le
 * informazioni prese
 * dalla dichiarazione di x (per ora consideriamo solo il nesting level).
 *
 * L'effetto della visita è che l'AST si trasforma in un Enriched Abstract
 * Syntax Tree (EAST),
 * dove ad alcuni nodi dell'AST sono attaccate STentry.
 * Le STentry sono palline attaccate agli usi degli identificatori.
 * E' stato possibile stabilire tale collegamento semantico grazie ai nomi degli
 * identificatori,
 * che da ora in poi non verranno più usati.
 *
 * Si tratta di una visita, con i soliti settaggi, costruttore, metodi di
 * visita, ecc..
 *
 * L'obiettivo della visita non è ritornare qualcosa! Bensì serve per arricchire
 * l'albero.
 * L'arricchimento avviene solo quando è in grado di collegare un uso ad una
 * dichiarazione.
 *
 * Ora dobbiamo occuparci di visitare l'EAST, stampando anche la STEntry. La
 * stEntry deve essere visitata
 * allo stesso modo di node. Devo dunque generalizzare, includendo
 * un'interfaccia Visitable, che vada bene
 * sia per nodi che stentry.
 */
public class SymbolTableASTVisitor extends BaseASTVisitor<Void, VoidException> {

	/**
	 * Symble table. E' una lista di mappe, mappa nomi di identifictori alla nostra
	 * stEntry
	 */
	private List<Map<String, STentry>> symTable = new ArrayList<>();
	private Map<String, Map<String, STentry>> classTable = new HashMap();
	private int nestingLevel = 0; // current nesting level, quando entro in uno scope lo incremento
	private int decOffset = -2; // counter for offset of local declarations at current nesting level. starts
								// with -2 due to our layout choice
	int stErrors = 0; // errori che incontriamo
	private Set<String> onClassVisitScope;

	SymbolTableASTVisitor() {
	}

	SymbolTableASTVisitor(boolean debug) {
		super(debug);
	} // enables print for debugging

	// Cerco nella SymbolTable il nesting level corrente
	private STentry stLookup(String id) {
		int j = nestingLevel;
		STentry entry = null;
		while (j >= 0 && entry == null)
			entry = symTable.get(j--).get(id);
		return entry;
	}

	/**
	 * Radice, quando ho un let in nel progbody.
	 * Quando esploro l'albero la tabella cresce quando entro negli scope, decresce
	 * quando ci esco.
	 * Quando invece ho finito il programma, rimuovo tutto.
	 * La symbol table serve per fare check e decorazione dell'albero.
	 *
	 * Le dichiarazioni incontrate potranno essere, delle dichiarazioni di variabili
	 * o funzioni.
	 */
	@Override
	public Void visitNode(ProgLetInNode n) {
		if (print)
			printNode(n);
		// Creo una tabella per l'ambiente globale
		Map<String, STentry> hm = new HashMap<>();
		// la aggiungo poi alla symTable (è vuota all'inizio)
		symTable.add(hm);
		for (Node dec : n.declist)
			visit(dec); // visito le dichiarazioni
		visit(n.exp); // visito il corpo, che userà le dichiarazioni
		symTable.remove(0);
		return null;
	}

	// visito e non faccio ulla
	@Override
	public Void visitNode(ProgNode n) {
		if (print)
			printNode(n);
		visit(n.exp);
		return null;
	}

	/**
	 * Quando ho una dichiarazione di funzione, come offset metto "decoffset--" per
	 * far fronte alla scelta del layout delle funzioni
	 */
	@Override
	public Void visitNode(FunNode n) {
		if (print)
			printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel); // prendo il fronte della lista (nesting level dello scope
																// attuale)
		List<TypeNode> parTypes = new ArrayList<>(); // gestisco i parametri
		for (ParNode par : n.parlist)
			parTypes.add(par.getType());
		// ArrowTypeNode è un tipo funzionale, parTypes è una lista di parametri,
		// retType è invece il tipo di ritorno
		STentry entry = new STentry(nestingLevel, new ArrowTypeNode(parTypes, n.retType), decOffset--);
		// inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Fun id " + n.id + " at line " + n.getLine() + " already declared");
			stErrors++;
		}
		nestingLevel++; // creare una nuova hashmap per la symTable, vado al nesting nevel successivo,
						// entrando in un nuovo scope
		// creo una hashmap per il nuovo scope
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		int prevNLDecOffset = decOffset; // stores counter for offset of declarations at previous nesting level
		decOffset = -2; // resetto il mio decOffset quando entro in un nuovo livello, in quanto, sia
						// parametri che variabili partono da -2 con il nuovo layout

		int parOffset = 1; // parOffset è 1 per via del layout scelto, cresce per ogni parametro dichiarato
		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel, par.getType(), parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line " + n.getLine() + " already declared");
				stErrors++;
			}
		for (Node dec : n.declist)
			visit(dec);
		// visito il corpo della funzione, prima ho elaborato tutte le dichiarazioni ed
		// i parametri,
		// poi analizzo l'espressione, che usa le dichiarazioni (si ricorda il modello
		// let/in)
		visit(n.exp);
		symTable.remove(nestingLevel--); // rimuovo la hashmap corrente poiche' esco dallo scope e decremento il nesting
											// level
		decOffset = prevNLDecOffset; // restores counter for offset of declarations at previous nesting level
		return null;
	}

	/**
	 * I VarNode aggiungono loro stessi alla lista, ovvero attacco i vari stentry
	 * alla mappa. Al nesting level corretto.
	 *
	 * Supponiamo di avere var x: int = x + 7;
	 * Devo fare la visita prima, perché se usa una x, dovrà trovarlo nella symbol
	 * table prima che io inserisca l'x
	 * che sto definendo adesso.
	 *
	 * Le visite del VarNode vengono chiamate dal ProgLetInNode
	 */
	@Override
	public Void visitNode(VarNode n) {
		if (print)
			printNode(n);
		// visito l'espressione (parte destra), prima di aggiungere la variabile nella
		// symtable,
		// questo perché, se afferisce a variabili non dichiarate, devo subito lanciare
		// l'eccezione
		// (nel nostro caso, aumentare il numero di errori)
		visit(n.exp);
		Map<String, STentry> hm = symTable.get(nestingLevel); // recupero il fronte della tabella
		STentry entry = new STentry(nestingLevel, n.getType(), decOffset--); // creo una pallina con le informazioni
																				// prese dalla dichiarazione
		// inserimento di ID della variabile nella symtable (inserisco la pallina)
		if (hm.put(n.id, entry) != null) {
			// controllo se la dichiarazione è multipla
			System.out.println("Var id " + n.id + " at line " + n.getLine() + " already declared");
			stErrors++;
		}
		return null;
	}

	@Override
	public Void visitNode(PrintNode n) {
		if (print)
			printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(IfNode n) {
		if (print)
			printNode(n);
		visit(n.cond);
		visit(n.th);
		visit(n.el);
		return null;
	}

	@Override
	public Void visitNode(EqualNode n) {
		if (print)
			printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(TimesNode n) {
		if (print)
			printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(PlusNode n) {
		if (print)
			printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(GreaterEqualNode n) throws VoidException {
		if (print)
			printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(LessEqualNode n) throws VoidException {
		if (print)
			printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(NotNode n) throws VoidException {
		if (print)
			printNode(n);
		visit(n.node);
		return null;
	}

	@Override
	public Void visitNode(MinusNode n) throws VoidException {
		if (print)
			printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(OrNode n) throws VoidException {
		if (print)
			printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(DivNode n) throws VoidException {
		if (print)
			printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(AndNode n) throws VoidException {
		if (print)
			printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	// ID()
	@Override
	public Void visitNode(CallNode n) {
		if (print)
			printNode(n);
		STentry entry = stLookup(n.id); // verifico che la funzione sia dichiarata
		if (entry == null) {
			System.out.println("Fun id " + n.id + " at line " + n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry; // linking
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist)
			visit(arg); // visita degli argomenti della funzione
		return null;
	}

	/**
	 * Idnode -> uso della variabile. La variabile la cerco nel nesting level in cui
	 * mi trovo.
	 * Se la trovo lì vuol dire che è dichiarata localmente (stesso scope). Se non
	 * la trovo
	 * guardo nel nesting level precedente (nesting--).
	 *
	 * Quando (e se) la trovo, attacco la pallina (STentry) alla foglia.
	 */
	@Override
	public Void visitNode(IdNode n) {
		if (print)
			printNode(n);
		// cerca la rispettiva dichiarazione, a partire dallo scope corrente, salendo
		STentry entry = stLookup(n.id);
		// Controllo se ho trovato o meno la pallina
		if (entry == null) {
			System.out.println("Var or Par id " + n.id + " at line " + n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry; // metto la pallina nella variabile entry, collego l'uso alla dichiarazione
			n.nl = nestingLevel; // torno al nesting level originale
		}
		return null;
	}

	@Override
	public Void visitNode(BoolNode n) {
		if (print)
			printNode(n, n.val.toString());
		return null;
	}

	@Override
	public Void visitNode(IntNode n) {
		if (print)
			printNode(n, n.val.toString());
		return null;
	}

	/***
	 * Very similar to FunNode, it differs from the fact that it even contains local
	 * declarations
	 * 
	 * @TODO add prints
	 *
	 * @param n
	 * @return
	 * @throws VoidException
	 */
	@Override
	public Void visitNode(MethodNode n) throws VoidException {
		if (print)
			printNode(n);

		// DECLARATION SCOPE

		var hm = symTable.get(nestingLevel); // prendo il fronte della lista (nesting level dello scope attuale)
		List<TypeNode> parTypes = new ArrayList<>();
		for (var par : n.parlist) {
			parTypes.add(par.getType());
		} // aggiorno la stentry con i tipi
		// recupero dalla table l'id del metodo, se c'è già, vuol dire che è override,
		// devo appurare che il tipo sia corretto
		var overriddenMethodEntry = hm.get(n.id);
		final TypeNode methodType = new MethodTypeNode(new ArrowTypeNode(parTypes, n.retType));
		STentry sTentry = null;
		// se è override, mantengo l'offset del vecchio metodo
		if (overriddenMethodEntry != null && overriddenMethodEntry.type instanceof MethodTypeNode) {
			sTentry = new STentry(nestingLevel, methodType, overriddenMethodEntry.offset);
		} else { // altrimenti incremento l'offset
			sTentry = new STentry(nestingLevel, methodType, decOffset++);
			if (overriddenMethodEntry != null) {
				System.out.println("Cannot override method id " + n.id + " with a field");
				stErrors++;
			}
		}
		n.offset = sTentry.offset; // setto l'offset in base a quello che ho calcolato sopra
		hm.put(n.id, sTentry); // inserisco nella map la entry che ho creato, con id corrispondente

		// METHOD BODY SCOPE

		nestingLevel++; // scendo nel corpo, aumentando il nesting level
		Map<String, STentry> methodsTable = new HashMap<>();
		symTable.add(methodsTable);
		var oldDecOffset = decOffset; // stores counter for offset of declarations at previous nesting level
		decOffset = -2; // resetto il mio decOffset quando entro in un nuovo livello, parametri e
						// variabili partono da -2 con il nuovo layout
		int parOffset = 1; // parOffset è 1 per via del layout scelto, cresce per ogni parametro dichiarato

		// Parameters uses
		for (var par : n.parlist) {
			if (methodsTable.put(par.id, new STentry(nestingLevel, par.getType(), parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line " + n.getLine() + " already declared");
				stErrors++;
			}
		}

		// Declarations
		for (var dec : n.declist) {
			visit(dec);
		} // visito le dichiarazioni di variabili locali

		// Uses induction to elaborate body
		visit(n.exp); // visito il corpo della funzione, prima ho elaborato tutte le dichiarazioni ed
						// i parametri,
						// poi analizzo l'espressione, che usa le dichiarazioni (si ricorda il modello
						// let/in)
		symTable.remove(nestingLevel--); // rimuovo la hashmap corrente poiche' esco dallo scope e decremento il nesting
											// level
		decOffset = oldDecOffset;
		return null;
	}

	public Void visitNode(ClassNode node) {
		if (print) {
			printNode(node);
		}
		// inizializzazione tipo, se non eredita, rimane tale
		var classType = new ClassTypeNode(new ArrayList<>(), new ArrayList<>());
		// controlla se la classe estende un'altra classe, avvalendosi della class table
		if (node.superId != null && classTable.containsKey(node.superId)) {
			// recupero la stEntry della classe da qui eredita, in modo da recuperare i
			// campi ed i metodi.
			STentry superClassEntry = symTable.get(0).get(node.superId);
			// imposto il classType con i field
			classType = new ClassTypeNode(new ArrayList<>(((ClassTypeNode) superClassEntry.type).allFields),
					new ArrayList<>(((ClassTypeNode) superClassEntry.type).allMethods));
			// aggiorno la entry del nodo, la super entry deve partire dal livello globale!
			node.superEntry = superClassEntry;
		} else if (node.superId != null) {
			System.out
					.println("Extending class id " + node.superId + " at line " + node.getLine() + " is not declared");
		}
		// il class type varia in funzione dell'ereditarietà o meno, decOffset parte da
		// -2 e decremento, parto dal livello globale!
		STentry entry = new STentry(0, classType, decOffset--);
		node.classType = classType;
		// recupero la symTable al livello globale (dove sono dichiarate le classi)
		Map<String, STentry> globalScopeTable = symTable.get(0);
		if (globalScopeTable.put(node.classId, entry) != null) {
			System.out.println("Class id " + node.classId + " at line " + node.getLine() + " already declared");
			stErrors++;
		}
		/*
		 * Add the scope table for the id of the class.
		 * Table should be added for both symbol table and class table.
		 */
		nestingLevel++;
		onClassVisitScope = new HashSet<>(); // tiene traccia dei campi e metodi che visito nella classe attuale,
												// mi consente di identificare doppie dichiarazioni (esulano
												// dall'overriding)
		Map<String, STentry> virtualTable = new HashMap<>();
		var superClassVirtualTable = classTable.get(node.superId); // se non c'è ereditarietà rimane vuota
		if (node.superId != null) {
			// aggiorno la class table, copiando la virtual table dalla classe da cui si
			// eredita (copio tutto e non solo il riferimento)
			virtualTable.putAll(superClassVirtualTable);
		}
		classTable.put(node.classId, virtualTable);
		symTable.add(virtualTable);

		// --- FIELDS ---

		/*
		 * Setting the fieldOffset for the extending class Extending class: a -> -1, b
		 * -> -2, c -> -3; Class impl: d -> -4
		 */
		int fieldOffset = -1;
		if (node.superId != null) {
			fieldOffset = -((ClassTypeNode) symTable.get(0).get(node.superId).type).allFields.size() - 1; // l'offset
																											// corrente
																											// riparte
		}
		for (var field : node.fields) { // dichiarazione di campi
			if (onClassVisitScope.contains(field.id)) { // controllo ripetizioni
				System.out.println(
						"Field with id " + field.id + " on line " + field.getLine() + " was already declared");
				stErrors++;
			}
			onClassVisitScope.add(field.id);
			// recupero il campo dalla virtual table, se è presente, significa che è
			// override
			var overriddenFieldEntry = virtualTable.get(field.id);
			STentry fieldEntry;
			// appuro che si tratti di un campo e non un metodo
			if (overriddenFieldEntry != null && !(overriddenFieldEntry.type instanceof MethodTypeNode)) {
				fieldEntry = new STentry(nestingLevel, field.getType(), overriddenFieldEntry.offset); // mantengo il
																										// vecchio
																										// offset
				classType.allFields.set(-fieldEntry.offset - 1, fieldEntry.type); // modifico il campo esistente sulla
																					// virtual table
			} else {
				// l'offset in questo caso è nuovo, quindi decremento ricordando che parte da -1
				fieldEntry = new STentry(nestingLevel, field.getType(), fieldOffset--);
				classType.allFields.add(-fieldEntry.offset - 1, fieldEntry.type); // aggiungo il nuovo campo alla
																					// virtual table
				if (overriddenFieldEntry != null) {
					System.out.println("Cannot override field id " + field.id + " with a method");
					stErrors++;
				}
			}
			/*
			 * Add field id in symbol(virtual) table
			 */
			virtualTable.put(field.id, fieldEntry);
			field.offset = fieldEntry.offset;
		}
		int currentDecOffset = decOffset; // salvo il valore del declaration offset corrente
		int previousNestingLevelDeclarationOffset = decOffset; // salvo il valore del declarationOffset del nesting
																// level precedente
		decOffset = 0; // dopo avere salvato i vecchi offset setto quello attuale, perché i metodi
						// vanno da 0 a n
		// se eredito l'offset non parte da 0, riparto da dove aveva lasciato la classe
		// da cui eredito
		if (node.superId != null) {
			decOffset = ((ClassTypeNode) symTable.get(0).get(node.superId).type).allMethods.size();
		}
		for (var method : node.methods) {
			if (onClassVisitScope.contains(method.id)) {
				System.out.println(
						"Method with id " + method.id + " on line " + method.getLine() + " was already declared");
				stErrors++;
			}
			onClassVisitScope.add(method.id);
			visit(method); // delego il tutto al metodo, che già gestisce, come nel caso dei campi, il
							// controllo sull'override
			classType.allMethods.add(
					method.offset,
					((MethodTypeNode) virtualTable.get(method.id).type).arrowTypeNode);
		}
		decOffset = currentDecOffset; // restores the previous declaration offset
		symTable.remove(nestingLevel--);
		decOffset = previousNestingLevelDeclarationOffset;
		return null;
	}

	@Override
	public Void visitNode(NewNode n) throws VoidException {
		if (print)
			printNode(n);
		if (!classTable.containsKey(n.id)) {
			System.out.println("Class id " + n.id + " at line " + n.getLine() + " not declared");
			stErrors++;
		}
		n.sTentry = symTable.get(0).get(n.id); // aggiungo informazioni al new, sulla classe da cui crea l'oggetto
		for (var arg : n.args) {
			visit(arg);
		}
		return null;
	}

	@Override
	public Void visitNode(EmptyNode n) throws VoidException {
		if (print)
			printNode(n);
		return null;
	}

	/**
	 * Contains class name that refers to class declared. Like A a = new A(). A is
	 * the ID
	 */
	@Override
	public Void visitNode(RefTypeNode n) throws VoidException {
		if (print)
			printNode(n, n.id);
		if (!classTable.containsKey(n.id)) {
			System.out.println("Class with id " + n.id + " on line " + n.getLine() + " was not declared");
			stErrors++;
		}
		return null;
	}

	// Chiamata di un metodo da fuori id1.id2();
	@Override
	public Void visitNode(ClassCallNode node) throws VoidException {
		if (print)
			printNode(node);
		// cerco la dichiarazione di id1 (che è una classe), attraverso la risalita
		// della static chain of access link
		STentry entry = stLookup(node.id1);
		if (entry == null) { // se non è dichiarata, è un errore!
			System.out.println("Var or Par id " + node.id1 + " at line " + node.getLine() + " not declared");
			stErrors++;
		} else {
			node.entry = entry;
			node.nestingLevel = nestingLevel;
			// setto la stentry di id2, cercandola nella virtual table (raggiunta tramite
			// class table) della classe del tipo RefTypeNode di id1
			node.methodEntry = classTable.get(((RefTypeNode) entry.type).id).get(node.id2);
			if (node.methodEntry == null) { // se id1 non ha il tipo corrispondente, è un errore!
				System.out.println("Object id " + node.id1 + " at line "
						+ node.getLine() + " has no method " + node.id2);
				stErrors++;
			}
		}
		for (var arg : node.args) {
			visit(arg); // visito gli argomenti della chiamata, elaborandoli tramite induzione
		}

		return null;
	}
}
