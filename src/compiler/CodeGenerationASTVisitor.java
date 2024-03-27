package compiler;

import compiler.AST.*;
import compiler.lib.*;
import compiler.exc.*;

import java.util.ArrayList;
import java.util.List;

import static compiler.lib.FOOLlib.*;
import static svm.ExecuteVM.MEMSIZE;

/**
 * In lab si è iniziato senza considerare le funzioni, per evitare gli scope annidati.
 * Ho dunque variabili globali e variabili locali alle funzioni.
 * Viene ritornata una intera stringa, e non ho problema di incompletezza dell'albero.
 *
 * */

public class CodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {


	private List<List<String>> dispatchTables = new ArrayList<>();

	CodeGenerationASTVisitor() {}
  	CodeGenerationASTVisitor(boolean debug) {super(false,debug);} //enables print for debugging

	/**
	 * Dobbiamo allocare le variabili rispettando l'ordine degli offset
	 * Visito le dichiarazioni di variabili, in ordine, concatenandole (con la visita poi vengono messe sullo stack).
	 * */
	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		String declCode = null;
		for (Node dec : n.declist) declCode=nlJoin(declCode,visit(dec));
		return nlJoin(
			"push 0", //address fittizio 0 per sistemare l'offset
			declCode, // generate code for declarations (allocation)			
			visit(n.exp),
			"halt",
			getCode() //elenco delle funzioni
		);
	}

	@Override
	public String visitNode(ProgNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"halt"
		);
	}

	/**
	 * Genero il codice per il corpo della funzione. Io ho una funzione putCode() che mi memorizza il codice generato
	 * del corpo della funzione, che metterò dopo l'halt. Ovvero ho una collezione di funzione alla fine, in cui saltare
	 * Questo è dunque consentito da putCode()
	 *
	 * */
	@Override
	public String visitNode(FunNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		String funl = freshFunLabel(); // per fare function0: codice_generato, così si può saltare
		putCode(
			nlJoin(
				funl+":", //function_i :
				"cfp", // set $fp to $sp value
				"lra", // load $ra value
				declCode, // generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), // generate code for function body expression
				"stm", // set $tm to popped value (function result)
				popDecl, // remove local declarations from stack
				"sra", // set $ra to popped value
				"pop", // remove Access Link from stack
				popParl, // remove parameters from stack
				"sfp", // set $fp to popped value (Control Link)
				"ltm", // load $tm value (function result)
				"lra", // load $ra value
				"js"  // jump to popped address
			)
		);
		return "push "+funl;		//indirizzo della funzione da ritornare
	}

	/**
	 * Differenza con funNode -> non ritorna il push label ma l'etichetta che genera la mette nel suo campo label.
	 * Perché verrà usata a livello di class node, per popolare la dispatch table.
	 * */
	@Override
	public String visitNode(MethodNode n) throws VoidException {
		if (print) printNode(n, n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		String generatedCode = freshFunLabel();
		putCode(
				nlJoin(
						generatedCode +":",
						"cfp", // set $fp to $sp value
						"lra", // load $ra value
						declCode, // generate code for local declarations (they use the new $fp!!!)
						visit(n.exp), // generate code for function body expression
						"stm", // set $tm to popped value (function result)
						popDecl, // remove local declarations from stack
						"sra", // set $ra to popped value
						"pop", // remove Access Link from stack
						popParl, // remove parameters from stack
						"sfp", // set $fp to popped value (Control Link)
						"ltm", // load $tm value (function result)
						"lra", // load $ra value
						"js"  // jump to popped address
				)
		);
		n.label = generatedCode;
		return null;
	}

	/**
	 * Nel caso di null, sullo stack mettiamo -1, perché non è un indirizzo (non esiste l'indirizzo -1).
	 * Vogliamo distinguere null da qualsiasi altro oggetto, se facciamo new A() == null, vogliamo che torni falso,
	 * gli id non potranno mai coincidere.
	 *
	 * */
	@Override
	public String visitNode(EmptyNode n) throws VoidException {
		if (print) printNode(n); return "push -1";
	}

	@Override
	public String visitNode(ClassNode n)  {
		if (print) printNode(n, n.classId);
		//table which contains addresses to class methods
		List<String> dispatchTable = new ArrayList<>();
		//
		if (n.superId != null) {
			//dispatch table of inherited class
			dispatchTable.addAll(dispatchTables.get(-n.superEntry.offset - 2));
		}
		this.dispatchTables.add(dispatchTable);
		//add address for each method, it's needed to visit it first
		for (var method: n.methods) {
			visit(method);
			dispatchTable.add(method.label);
		}
		String codeGeneration = "";
		String loadHp = "lhp"; //Used to put hp value in stack, it will be the dispatch pointer to return
		/**
		 * Dichiarazione Classe: codice ritornato
		 * 1. metto valore di $hp sullo stack: sarà il dispatch
		 * pointer da ritornare alla fine
		 * */
		/**
		 *2. Creo sullo heap la dispatch table,
		 *  lavorando sullo stack, ogni volta, parto dalla posizione 0
		 *  della dispatch table (dove c'è il primo metodo),
		 *  caricherò la sua etichetta sullo stack e dovrò
		 *  poi fare in modo che questa etichetta finisca nell'indirizzo hp.
		 * Uso le istruzioni della nostra vm per mettere la label dentro hp.
		 *
		 * */
		for(var label: dispatchTable) {
			codeGeneration = nlJoin(
					codeGeneration,
					"push " + label,
					"lhp",			//push(hp)
					"sw",			// 1. address = pop() -> takes hp value, then memory[address] = pop(), to put label in heap

					"lhp",			//increment hp
					"push 1",
					"add",

					"shp"			//hp = pop(), stores new hp address in stack, to setup for next cycle
			);

		}

		return nlJoin(loadHp, codeGeneration);
	}

	/** sufficiente visitarla, per mettere il risultato sullo stack*/
	@Override
	public String visitNode(VarNode n) {
		if (print) printNode(n,n.id);
		return visit(n.exp);
	}

	/**
	 * print è un'espressione della nostra vm che consente di stampare,
	 * è dunque sufficiente indicarlo
	 *
	 * */
	@Override
	public String visitNode(PrintNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"print"
		);
	}

	@Override
	public String visitNode(IfNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();		
		return nlJoin(
			visit(n.cond),
			"push 1", // aggiungo 1, ovvero true per confrontarlo con la condizione
			"beq "+l1, // se la condizione è vera salto ad l1, dunque visito e ritorno il then
			visit(n.el), // qui la condizione è falsa e ritorno l'else
			"b "+l2,
			l1+":",
			visit(n.th),
			l2+":"
		);
	}

	/**
	 * exp1 == exp2 -> boolean
	 * if (condition) return true otherwise false
	 *
	 * faccio la code generation di left e right, così li ho sullo stack
	 *
	 * se left è uguale a right
	 *
	 * */
	@Override
	public String visitNode(EqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel(); //genero la nuova etichetta a cui saltare
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"beq "+l1, //confronta due elementi, se sono uguali va ad l1, se sono diversi tira dritto
			"push 0", //ritorno false
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}

	@Override
	public String visitNode(TimesNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"
		);	
	}

	/* Genero il codice di left, genero il codice di right, poi somma.
	 Ogni visita genera codice (è la cgen dei lucidi)**/
	@Override
	public String visitNode(PlusNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"add"				
		);
	}

	@Override
	public String visitNode(LessEqualNode n) throws VoidException {
		if (print) printNode(n);
		var l1 = freshLabel();
		var l2 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"bleq " + l1, //mi chiedo se left <= right
				"push 0",
				"b " + l2,
				l1 + ":",
				"push 1",
				l2 + ":"
		);
	}

	@Override
	public String visitNode(GreaterEqualNode n) throws VoidException {
		if (print) printNode(n);
		var l1 = freshLabel();
		var l2 = freshLabel();
		return nlJoin(
				visitNode(new EqualNode(n.left, n.right)), //checks if they're equal, the result will be on top of the stack
				"push 1",
				"beq " + l2,
				visit(n.left),
				visit(n.right),
				"bleq " + l1,
				"push 1",
				"b " + l2,
				l1 + ":",
				"push 0",
				l2 + ":"
		);
	}

	@Override
	public String visitNode(NotNode n) throws VoidException {
		if (print) printNode(n);
		var l1 = freshLabel();
		var l2 = freshLabel();

		return nlJoin(
				visit(n.node),
				"push 1",
				"beq " + l1,
				"push 1",
				"b " + l2,
				l1 + ":",
				"push 0",
				l2 + ":"
		);
	}

	@Override
	public String visitNode(MinusNode n) throws VoidException {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"sub"
		);
	}

	@Override
	public String visitNode(OrNode n) throws VoidException {
		if (print) printNode(n);
		var l1 = freshLabel();
		var l2 = freshLabel();
		return nlJoin(
				visit(n.left),
				"push 1",
				"beq " + l1,
				visit(n.right),
				"push 1",
				"beq " + l1,
				"push 0",
				"b " + l2,
				l1 + ":",
				"push 1",
				l2 + ":"
		);
	}

	@Override
	public String visitNode(DivNode n) throws VoidException {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"div"
		);
	}

	@Override
	public String visitNode(AndNode n) throws VoidException {
		if (print) printNode(n);
		var l1 = freshLabel();
		var l2 = freshLabel();
		var l3 = freshLabel();
		return nlJoin(
			visit(n.left),
			"push 1",
			"beq " + l1,
			"push 0",
			"b " + l3,
			l1+":",
			visit(n.right),
			"push 1",
			"beq " + l2,
			"push 0",
			"b " + l3,
			l2+ ":",
			"push 1",
			l3+ ":"
		);
	}

	/**
	 * 1. Devo caricare sullo stack il control link. è il puntatore al frame in cui sono adesso (del chiamante).
	 * Quindi prendo il frame point sullo stack. (Puntatore al frame del chiamante della mia funzione id.
	 * Ok ma sono io il chiamante, per quello metto fp)
	 *
	 * 2. Calcolo le espressioni degli argomenti attraverso la visita, in ordine inverso, questo perché
	 * il primo parametro deve essere l'ultimo ad essere messo sullo stack, per finire ad offset 1.
	 *
	 * 3. Setto l'access link -> il valore dell'access link è il frame in cui è dichiarata la funzione (lfp).
	 * Lo stesso frame mi serve anche per recuperare l'indirizzo della funzione (dove è localizzato, per poter saltare).
	 *
	 * 4. Prendo l'indirizzo del frame che contiene la dichiarazione di "id", seguendo la static chain.
	 *
	 * 5. Il frame in cui è dichiarata la funzione lo devo usare per:
	 * - settare l'access link;
	 * - recuperare l'indirizzo effettivo;
	 * quindi mi serve duplicare il valore che ho sulla cima dello stack, lo faccio usando il registro temporaneo
	 * stm (store nel tm). Usando due volte ltm ho un valore duplicato sulla cima dello stack.
	 *
	 * 6.
	 * - Alla seconda copia, applico l'offset per trovare l'indirizzo della funzione e faccio il salto.
	 * - alla prima copia invece, carico nell'heap, l'indirizzo della funzione
	 * */
	@Override
	public String visitNode(CallNode n) {
		if (print) printNode(n,n.id);
		String result = "";
		if (n.entry.type instanceof MethodTypeNode) {
			result = methodCall(n);
		} else {
			result = functionCall(n);
		}
		return result;
	}

	private String functionCall(CallNode n) {
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw"); // static chain of access links
		return nlJoin(
				"lfp", // load Control Link (pointer to frame of function "id" caller)
				argCode, // generate code for argument expressions in reversed order
				"lfp", getAR, // retrieve address of frame containing "id" declaration
				// by following the static chain (of Access Links)
				"stm", // set $tm to popped value (with the aim of duplicating top of stack) -> tm = pop()
				"ltm", // load Access Link (pointer to frame of function "id" declaration) -> push(tm)
				"ltm", // duplicate top of stack -> push(tm)
				"push "+n.entry.offset, "add", // compute address of "id" declaration
				"lw", // load address of "id" function
				"js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);
	}

	/**
	 * A differenza dalla chiamata a funzione qui recupero il dispatch pointer dalla memoria e da lì calcolo l'offset, aggiungendolo, raggiungo l'object pointer
	 *
	 * */
	private String methodCall(CallNode n) {
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw"); // static chain of access links
		return nlJoin(
				"lfp", // load Control Link (pointer to frame of function "id" caller)
				argCode, // generate code for argument expressions in reversed order
				"lfp", getAR, // retrieve address of frame containing "id" declaration
				// by following the static chain (of Access Links)
				"stm", // set $tm to popped value (with the aim of duplicating top of stack)
				"ltm", // load Access Link (pointer to frame of function "id" declaration)
				"ltm", // duplicate top of stack
				"lw", // load dispatch pointer
				"push "+n.entry.offset, "add", // compute address of "id" declaration
				"lw", // load address of "id" method
				"js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);
	}

	// id1.id2()
	// A a = new A(); a -> A
	@Override
	public String visitNode(ClassCallNode node) {
		if (print) printNode(node, node.id1);
		String getAR = "";
		String argCode = "";
		for (int i = node.args.size() - 1; i >= 0; i--) argCode = nlJoin(argCode, visit(node.args.get(i)));
		for (int i = 0;i < node.nestingLevel - node.entry.nl;i++) getAR=nlJoin(getAR,"lw"); // static chain of access links
		return nlJoin(
				"lfp", 	//load Control link (pointer to frame of function)
				argCode,		// generate code for argument expressions in reversed order
				"lfp", getAR, // retrieve address of id1's frame pointer

				"push " + node.entry.offset, //offset, that added to id1 address, will give us the object pointer
				"add", // now I have the object pointer in the stack's top
				"lw", // load object pointer
				"stm", // set $tm to popped value (with the aim of duplicating top of stack)
				"ltm", // load Access Link (pointer to frame of function "id" declaration)
				"ltm", // duplicate top of stack
				"lw",  //load address, to join dispatch table
				"push "+ node.methodEntry.offset,
				"add", // compute address of "id" declaration
				"lw", // load address of "id" function
				"js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);
	}

	/**
	 * sullo stack abbiamo messo l'indirizzo della seconda variabile
	 * con lw prendo il valore della variabile, perché avevamo fino ad ora usato solo l'indirizzo
	 *
	 * Come faccio a capire la differenza fra uso e dichiarazione?
	 * n.entry.nl è il nesting level della dichiarazione
	 * n.nl è invece il nesting level dell'uso
	 * Lo abbiamo aggiunto nella symbol table
	 *
	 * Dopodiché, molto semplicemente aggiungo all'address, l'offset per recuperare la dichiarazione
	 *
	 * 1. Carica sullo stack il frame pointer, punta al frame locale;
	 * 2. Gestisco la risalita con getAR, è sufficiente deferenziare con lw, punta sempre all'access link
	 * 3. Applico l'offset all'indirizzo
	 * 4. Prendo il valore della variabile dichiarata (dallo heap, attraverso l'address) e lo metto sullo stack
	 *
	 * */
	@Override
	public String visitNode(IdNode n) {
		if (print) printNode(n,n.id);
		String getAR = null;
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw"); // faccio svariati load word, uno per variabile
		return nlJoin(
			"lfp", getAR, // retrieve address of frame containing "id" declaration
			              // by following the static chain (of Access Links)
			"push "+n.entry.offset,
			"add", // compute address of "id" declaration
			"lw" // load value of "id" variable
		);
	}

	@Override
	public String visitNode(NewNode n) {
		if (print) printNode(n, n.id);
		String argCode = ""; String generatedCode = "";
		// put args into stack
		for (int i = 0; i < n.args.size(); i++) argCode = nlJoin(argCode, visit(n.args.get(i)));
		for (int i = 0; i < n.args.size(); i++) {
			generatedCode = nlJoin(generatedCode,
					"lhp",			//push(hp)
					"sw",			// 1. address = pop() -> takes hp value, then memory[address] = pop(), to put label in heap

					"lhp",			//increment hp
					"push 1",
					"add",
					"shp"
					);
		}

		return nlJoin(
				argCode,
				generatedCode,

				// get dispatch pointer from heap
				"push " +  (MEMSIZE + n.sTentry.offset),
				"lw", // takes dispatch pointer

				//writes dispatch pointer at hp address (heap) -> serve per allocare spazio per il nuovo oggetto
				"lhp", // push(hp)
				"sw", // 1. address = pop() -> takes hp value, then memory[address] = pop()

				// put on stack the object pointer
				"lhp", //Object pointer, which needs to be returned

				// incremento hp
				"lhp",
				"push 1",
				"add",
				"shp"
		);
	}

	@Override
	public String visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+(n.val?1:0);
	}

	/**pusho l'intero, lo aggiungo*/
	@Override
	public String visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+n.val;
	}
}