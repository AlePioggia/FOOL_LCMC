package compiler;

import java.util.*;
import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;


/**
 * Utilizziamo una symbol table, come lista di hashMap, per collegare usi a dichiarazioni.
 * Gli scope sono rappresentati dunque da tabelle che stanno dentro la lista. Nella lista,
 * avremo il fronte della lista che è lo scope attuale, in cui siamo posizionati. Poi avremo
 * via via le tabelle degli scope in cui siamo chiusi, fino ad arrivare via via allo scope globale.
 * Regole di scoping statico:
 * - a use of an identifier x matches the declaration in the most closely enclosing scope (such that the declaration
 * precedes the use)
 * - inner scope identifier x declaration hides x declared in an outer scope.
 *
 * L'obiettivo di questa visita sarà verificare la correttezza del sorgente, quindi dare errori
 * in caso di:
 * - dichiarazioni multiple o identificatori non dichiarati;
 * - nel caso in cui riusciamo a stabilire un'associazione fra uso e dichiarazione,
 * dobbiamo attaccare alla foglia dell'AST che rappresenta l'uso di un identificatore x,
 * la symbol table entry (oggetto di classe STentry) che contiene le informazioni prese
 * dalla dichiarazione di x (per ora consideriamo solo il nesting level)
 *
 * L'effetto della visita è che l'AST si trasforma in un Enriched Abstract Syntax
 * Tree (EAST), dove ad alcuni nodi dell'AST sono attaccate STentry. Le STentry sono palline attaccate agli
 * usi degli identificatori.
 * E' stato possibile stabilire tale collegamento semantico grazie
 * ai nomi degli identificatori, che da ora in poi non verranno più usati.
 *
 * Si tratta di una visita, con i soliti settaggi, costruttore, metodi di visita, ecc..
 *
 * L'obiettivo della visita non è ritornare qualcosa! bensì serve per arricchire l'albero. Lo arricchisce
 * solo quando è in grado di collegare un uso ad una dichiarazione.
 *
 * Ora dobbiamo occuparci di visitare l'EAST, stampando anche la STEntry. La stEntry deve essere visitata
 * allo stesso modo di node. Devo dunque generalizzare, includendo un'interfaccia Visitable, che va bene
 * sia per nodi che stentry.
 *
 *
 * */
public class SymbolTableASTVisitor extends BaseASTVisitor<Void,VoidException> {

	/**
	 * Symble table. E' una lista di mappe, mappa nomi di identifictori alla nostra stEntry
	 * */
	private List<Map<String, STentry>> symTable = new ArrayList<>();
	private Map< String, Map<String,STentry> > classTable = new HashMap();
	private int nestingLevel=0; // current nesting level, quando entro in uno scope lo incremento
	private int decOffset=-2; // counter for offset of local declarations at current nesting level. starts with -2 due to our layout choice
	private int classOffset = -2;
	int stErrors=0; // errori che incontriamo

	SymbolTableASTVisitor() {}
	SymbolTableASTVisitor(boolean debug) {super(debug);} // enables print for debugging

	/**
	 * Idnode -> uso della variabile. La variabile la cerco nel nesting level in cui mi trovo.
	 * Se la trovo lì vuol dire che è dichiarata localmente (stesso scope). Se non la trovo
	 * guardo nel nesting level precedente (nesting--).
	 *
	 * Quando (e se) la trovo, attacco la pallina (STentry) alla foglia.
	 *
	 * Questa è la fase in cui cerco nella symtable del nesting level corrente.
	 * */
	private STentry stLookup(String id) {
		int j = nestingLevel;
		STentry entry = null;
		while (j >= 0 && entry == null) 
			entry = symTable.get(j--).get(id);	
		return entry;
	}

	/**
	 * Radice, quando ho un let in nel progbody.
	 * Quando esploro l'albero la tabella cresce quando entro negli scope,
	 * decresce quando ci esco. Quando invece ho finito il programma, rimuovo tutto.
	 * La symbol table serve per fare check e decorazione dell'albero.
	 *
	 * Le dichiarazioni incontrate potranno essere, delle dichiarazioni di variabili o funzioni.
	 * */
	@Override
	public Void visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		//Creo una tabella per l'ambiente globale
		Map<String, STentry> hm = new HashMap<>();
		//la aggiungo poi alla symTable (è vuota all'inizio)
		symTable.add(hm);
	    for (Node dec : n.declist) visit(dec); //visito le dichiarazioni
		visit(n.exp); //visito il corpo, che userà le dichiarazioni
		symTable.remove(0);
		return null;
	}
	//visito e non faccio ulla
	@Override
	public Void visitNode(ProgNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	/**
	 * Quando ho una dichiarazione di funzione, come offset metto decoffset-- per far fronte alla scelta del layout delle funzioni
	 * */
	@Override
	public Void visitNode(FunNode n) {
		if (print) printNode(n);
		// prendo il fronte della lista (nesting level dello scope attuale)
		Map<String, STentry> hm = symTable.get(nestingLevel);
		// gestisco i parametri
		List<TypeNode> parTypes = new ArrayList<>();  
		for (ParNode par : n.parlist) parTypes.add(par.getType());
		//ArrowTypeNode è un tipo funzionale, parTypes è una lista di parametri, retType è invece il tipo di ritorno
		STentry entry = new STentry(nestingLevel, new ArrowTypeNode(parTypes,n.retType),decOffset--);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		} 
		// creare una nuova hashmap per la symTable, vado al nesting nevel successivo, entrando in un nuovo scope
		nestingLevel++;
		// creo una hashmap per il nuovo scope
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level 
		decOffset=-2; //resetto il mio decOffset quando entro in un nuovo livello, in quanto, sia parametri che variabili partono da -2 con il nuovo layout
		
		int parOffset=1; //parOffset è 1 per via del layout scelto, cresce per ogni parametro dichiarato
		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		for (Node dec : n.declist) visit(dec);
		//visito il corpo della funzione, prima ho elaborato tutte le dichiarazioni ed i parametri, poi analizzo l'espressione, che usa le dichiarazioni (si ricorda il modello let/in)
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope e decremento il nesting level
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}

	/**
	 * Aggiungono loro stessi alla lista, ovvero attacco i vari stentry alla mappa. Al nesting level corretto.
	 *
	 * Supponiamo di avere var x: int = x + 7;
	 * Devo fare la visita prima, perché se usa una x, dovrà trovarlo nella symbol table prima che io inserisca l'x
	 * che sto definendo adesso.
	 *
	 * Le visite del VarNode vengono chiamate dal ProgLetInNode
	 * */
	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp); 	//visito l'espressione (parte destra), prima di aggiungere la variabile nella symtable,
						// questo perché, se afferisce a variabili non dichiarate, devo subito lanciare l'eccezione (nel nostro caso, aumentare il numero di errori)
		Map<String, STentry> hm = symTable.get(nestingLevel); //recupero il fronte della tabella
		STentry entry = new STentry(nestingLevel,n.getType(),decOffset--); // creo una pallina con le informazioni prese dalla dichiarazione
		//inserimento di ID della variabile nella symtable (inserisco la pallina)
		if (hm.put(n.id, entry) != null) {
			// controllo su doppia dichiarazione
			System.out.println("Var id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		return null;
	}

	@Override
	public Void visitNode(PrintNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(IfNode n) {
		if (print) printNode(n);
		visit(n.cond);
		visit(n.th);
		visit(n.el);
		return null;
	}
	
	@Override
	public Void visitNode(EqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(TimesNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(PlusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(GreaterEqualNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(LessEqualNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(NotNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.node);
		return null;
	}

	@Override
	public Void visitNode(MinusNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(OrNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(DivNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(AndNode n) throws VoidException {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(CallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}

	/**
	 * Idnode -> uso della variabile. La variabile la cerco nel nesting level in cui mi trovo.
	 * Se la trovo lì vuol dire che è dichiarata localmente (stesso scope). Se non la trovo
	 * guardo nel nesting level precedente (nesting--).
	 *
	 * Quando (e se) la trovo, attacco la pallina (STentry) alla foglia.
	 * */
	@Override
	public Void visitNode(IdNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id); //cerca la rispettiva dichiarazione, a partire dallo scope corrente, salendo
		//Controllo se ho trovato o meno la pallina
		if (entry == null) {
			System.out.println("Var or Par id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry; // metto la pallina nella variabile entry, collego l'uso alla dichiarazione
			n.nl = nestingLevel; // torno al nesting level originale
		}
		return null;
	}

	@Override
	public Void visitNode(BoolNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	@Override
	public Void visitNode(IntNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	/***
	 * Very similar to FunNode, it differs from the fact that it even contains local declarations
	 * @TODO add prints
	 *
	 * @param n
	 * @return
	 * @throws VoidException
	 */
	@Override
	public Void visitNode(MethodNode n) throws VoidException {
		if (print) printNode(n);

		//Declaration scope

		List<TypeNode> parTypes = new ArrayList<>();
		var hm = symTable.get(nestingLevel);

		for (var par: n.parlist) {
			parTypes.add(par.getType());
		}

		STentry sTentry = new STentry(nestingLevel, new MethodTypeNode(new ArrowTypeNode(parTypes, n.getType())), decOffset--);
		if (hm.put(n.id, sTentry) != null) {
			System.out.println("Par id " + "id" + " at line " + n.getLine() + " already declared");
			stErrors++;
		}

		n.offset = sTentry.offset;
		hm.put(n.id, sTentry);

		//Method body scope
		nestingLevel++;
		Map<String, STentry> methodsTable = new HashMap<>();

		var oldDecOffset = decOffset;
		decOffset = -2; //declarationOffset per il nesting level corrente
		int parOffset = 1;

		//Declarations
		for (var dec: n.declist) {
			visit(dec);
		}

		//Parameters uses
		for (var par: n.parlist) {
			if (methodsTable.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		}
		//Uses induction to elaborate body
		visit(n.exp);

		symTable.remove(nestingLevel--);
		decOffset = oldDecOffset;
		return null;
	}

	/**
	 * @TODO implement inheritance aspects
	 * */
	@Override
	public Void visitNode(ClassNode n) {
		if (print) printNode(n);
		var classTypeNode = new ClassTypeNode(new ArrayList<>(), new ArrayList<>());
		//Class declaration, creation of a new STEntry, nesting level 0 because it's global
		STentry entry = new STentry(0, classTypeNode ,classOffset);
		//Reference to head of symbol table (global scope)
		var hm = symTable.get(0);
		if (n.superId != null) {
			//With inheritance
			entry = hm.get(n.superId); //entry of superclass
			n.classType = new ClassTypeNode(((ClassTypeNode) entry.type).allFields, ((ClassTypeNode) entry.type).allMethods);
		}
		//No inheritance
		//Insert declaration in front of table
		STentry insertedValue = hm.put(n.classId, entry);

		//if put fails, it means class was already declared, throws an error
		if (insertedValue != null) {
			System.out.println("Method id " + "id" + " at line " + n.getLine() + " already declared");
			stErrors++; // Classe già dichiarata
		}

		//After the class declaration, it's needed to go deep, inside the class scope
		nestingLevel++;

		//Virtual table for current scope
		Map<String, STentry> symbolVirtualTable = new HashMap();
		if (n.superId != null) {
			//with inheritance
			//copies of all the content of the VirtualTable into symbolVirtualTable
			symbolVirtualTable.putAll(this.classTable.get(n.superId));
		}

		//It saves the table for use
		classTable.put(n.classId, symbolVirtualTable);
		symTable.add(symbolVirtualTable);

		int fieldOffset = -1;
		final Set<String> fieldNames = new HashSet<>();

		//Gestione campi
		for (FieldNode field : n.fields) {
			// Add the field to the class table
			if (fieldNames.contains(field.id)) {
				System.out.println("Field id " + field.id + " at line " + n.getLine() + " already declared");
				stErrors++;
			} else {
				fieldNames.add(field.id);
			}

			STentry fieldEntry = null;

			var overriddenFieldEntry = symbolVirtualTable.get(field.id);
			if (overriddenFieldEntry != null && !(overriddenFieldEntry.type instanceof MethodTypeNode)) {
				fieldEntry = new STentry(nestingLevel, field.getType(), overriddenFieldEntry.offset);
				classTypeNode.allFields.set(-fieldEntry.offset - 1, fieldEntry.type);
				field.offset = fieldEntry.offset;
			} else {
				fieldEntry = new STentry(nestingLevel, field.getType(), fieldOffset--);
				classTypeNode.allFields.add(-fieldEntry.offset - 1, fieldEntry.type);
				if (overriddenFieldEntry != null) {
					System.out.println("Cannot override field id " + field.id + " with a method");
					stErrors++;
				}
			}

			symbolVirtualTable.put(field.id, fieldEntry);
		}

		var methodNames = new HashSet();
		var oldDecOffset = decOffset;
		decOffset = 0;

		//Gestione metodi
		for (var method: n.methods) {
			if (methodNames.contains(method.id)) {
				System.out.println("Method id: " + method.id + " at line "+ n.getLine() + " is already declared");
				stErrors++;
			} else {
				methodNames.add(method.id);
			}
			//need to visit cause of name conflicts (internal scope), and method signature
			visit(method);

			classTypeNode.allMethods.add(method.offset,
					((MethodTypeNode) symbolVirtualTable.get(method.id).type).arrowTypeNode);
		}

		decOffset = oldDecOffset;
		//reset SymbolTable
		symTable.remove(nestingLevel--);
		return null;
	}
/*
	@Override
	public Void visitNode(ClassNode n) {
		if (print) printNode(n);
		//creo un nuovo classTypeNode con liste vuote di campi e metodi, che sarà il tipo della classe
		ClassTypeNode ctn = new ClassTypeNode(new ArrayList<>(), new ArrayList<>());
		//creo la nuova entry per la classe
		STentry entry = new STentry(0, ctn, decOffset--);

		final Map<String, STentry> globalST = symTable.get(0);
		if (globalST.put(n.classId, entry) != null) {
			System.out.println("Class id " + n.classId + " at line " + n.getLine() + " already declared");
			stErrors++;
		}

		Map<String, STentry> virtualTable = new HashMap<>();
		// Add the class to the class table
		classTable.put(n.classId, virtualTable);
		// Add the class to the symbol table
		symTable.add(virtualTable);

		// Increase nesting level
		nestingLevel++;

		// Visit fields of the class
		int fieldOffset = -1;

		final Set<String> fieldNames = new HashSet<>();

		for (FieldNode field : n.fields) {
			// Add the field to the class table
			if (fieldNames.contains(field.id)) {
				System.out.println("Field id " + field.id + " at line " + n.getLine() + " already declared");
				stErrors++;
			} else {
				fieldNames.add(field.id);
			}
			visit(field);

			STentry fieldEntry = new STentry(nestingLevel, field.getType(), fieldOffset--);

			ctn.allFields.add(-fieldEntry.offset - 1, fieldEntry.type);

			virtualTable.put(field.id, fieldEntry);
		}

		int precOffset = decOffset;
		decOffset = 0;
		List<String> methodNames = new ArrayList<>();
		for (MethodNode method : n.methods) {
			if (methodNames.contains(method.id)) {
				System.out.println("Method id " + method.id + " at line " + n.getLine() + " already declared");
				stErrors++;
			} else {
				methodNames.add(method.id);
			}
			visit(method);

			final MethodTypeNode methodTypeNode = (MethodTypeNode) symTable.get(nestingLevel).get(method.id).type;
			ctn.allMethods.add(method.offset, methodTypeNode.arrowTypeNode);
		}
		decOffset = precOffset;
		symTable.remove(nestingLevel--);
		return null;
	}
*/



	@Override
	public Void visitNode(NewNode n) throws VoidException {
		if (print) printNode(n);
		if (!classTable.containsKey(n.id)) {
			System.out.println("Class id " + n.id + " at line " + n.getLine() + " not declared");
			stErrors++;
		}
		n.sTentry = symTable.get(0).get(n.id); //aggiungo informazioni al new, sulla classe da cui crea l'oggetto
		for (var arg: n.args) {
			visit(arg);
		}
		return null;
	}

	@Override
	public Void visitNode(EmptyNode n) throws VoidException {
		if (print) printNode(n);
		return null;
	}

	/**
	 * Contains class name that refers to class declared. Like A a = new A(). A is the ID
	 * */
	@Override
	public Void visitNode(RefTypeNode n) throws VoidException {
		if (print) printNode(n, n.id);
		if (!classTable.containsKey(n.id)) stErrors++;
		return null;
	}

	/**
	 * @TODO add prints
	 * */
	@Override
	public Void visitNode(ClassCallNode node) throws VoidException {
		if (print) printNode(node);
		STentry entry = stLookup(node.id1);
		if (entry == null) {
			System.out.println("Var or Par id " + node.id1 + " at line " + node.getLine() + " not declared");
			stErrors++;
		} else {
			node.entry = entry;
			node.nestingLevel = nestingLevel;
			if (node.methodEntry == null) {
				System.out.println("Object id " + node.id1 + " at line "
						+ node.getLine() + " has no method " + node.id2);
				stErrors++;
			}
		}
		for (var arg: node.args) {
			visit(arg);
		}

		return null;
	}
}
