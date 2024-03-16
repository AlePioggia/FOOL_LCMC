package compiler;

import compiler.AST.*;
import compiler.lib.*;
import compiler.exc.*;
import static compiler.lib.FOOLlib.*;

/**
 * In lab si è iniziato senza considerare le funzioni, per evitare gli scope annidati.
 * Ho dunque variabili globali e variabili locali alle funzioni.
 * Viene ritornata una intera stringa, e non ho problema di incompletezza dell'albero.
 *
 * */

public class CodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {

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
			"push 0",	
			declCode, // generate code for declarations (allocation)			
			visit(n.exp),
			"halt",
			getCode()
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

	@Override
	public String visitNode(FunNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		String funl = freshFunLabel();
		putCode(
			nlJoin(
				funl+":",
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
		return "push "+funl;		
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

	@Override
	public String visitNode(CallNode n) {
		if (print) printNode(n,n.id);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
			"lfp", // load Control Link (pointer to frame of function "id" caller)
			argCode, // generate code for argument expressions in reversed order
			"lfp", getAR, // retrieve address of frame containing "id" declaration
                          // by following the static chain (of Access Links)
            "stm", // set $tm to popped value (with the aim of duplicating top of stack)
            "ltm", // load Access Link (pointer to frame of function "id" declaration)
            "ltm", // duplicate top of stack
            "push "+n.entry.offset, "add", // compute address of "id" declaration
			"lw", // load address of "id" function
            "js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);
	}

	/**
	 * sullo stack abbiamo messo l'indirizzo della seconda variabile
	 * con lw prendo il valore della variabile, perché avevamo fino ad ora usato solo l'indirizzo
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