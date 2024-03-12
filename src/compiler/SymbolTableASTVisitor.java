package compiler;

import java.util.*;
import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;


/**
 * Utilizziamo una symble table, come lista di hashMap, per collegare usi a dichiarazioni.
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
	private int nestingLevel=0; // current nesting level, quando entro in uno scope lo incremento
	private int decOffset=-2; // counter for offset of local declarations at current nesting level 
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


	@Override
	public Void visitNode(FunNode n) {
		if (print) printNode(n);
		// prendo il fronte della lista
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
		decOffset=-2;
		
		int parOffset=1;
		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		for (Node dec : n.declist) visit(dec);
		//visito il corpo della funzione, che usa le robe dichiarate nel let
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
	 * */
	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp);
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
		STentry entry = stLookup(n.id);
		//Ho trovato la pallina
		if (entry == null) {
			System.out.println("Var or Par id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry; // metto la pallina nella variabile entry
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
}
