package compiler;

import java.util.*;

import compiler.exc.UnimplException;
import jdk.jshell.spi.ExecutionControl;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import compiler.AST.*;
import compiler.FOOLParser.*;
import compiler.lib.*;
import static compiler.lib.FOOLlib.*;

public class ASTGenerationSTVisitor extends FOOLBaseVisitor<Node> {

	String indent;
    public boolean print;
	
    ASTGenerationSTVisitor() {}

	ASTGenerationSTVisitor(boolean debug) { print=debug; }
        
    private void printVarAndProdName(ParserRuleContext ctx) {
        String prefix="";        
    	Class<?> ctxClass=ctx.getClass(), parentClass=ctxClass.getSuperclass();
        if (!parentClass.equals(ParserRuleContext.class)) // parentClass is the var context (and not ctxClass itself)
        	prefix=lowerizeFirstChar(extractCtxName(parentClass.getName()))+": production #";
    	System.out.println(indent+prefix+lowerizeFirstChar(extractCtxName(ctxClass.getName())));                               	
    }

	/**
	 * Posso accorgermi di figli null solo qui, perché mi vengono passati nell'argomento.
	 * Dunque se ricevo un parametro null, ritorno null.
	 * Una volta che la visita ritorna null.
	 *
	 * Risolve il problema ma ritorna ast incompleti (va dunque effettuata la gestione su EAST)
	 * */
    @Override
	public Node visit(ParseTree t) {
    	if (t==null) return null;
        String temp=indent;
        indent=(indent==null)?"":indent+"  ";
        Node result = super.visit(t);
        indent=temp;
        return result; 
	}

	/**
	 Quando visito prog devo generare un progNode. In input deve riceve il figlio (ovvero ciò che c'è dentro)

	 * */
	@Override
	public Node visitProg(ProgContext c) {
		if (print) printVarAndProdName(c);
		return visit(c.progbody());
	}

	@Override
	public Node visitLetInProg(LetInProgContext c) {
		if (print) printVarAndProdName(c);
		List<DecNode> declist = new ArrayList<>();
		//c.dec() ritorna una lista con tutti i figli dell'albero sintattico dec
		// con il for li scorro e ci chiamo la visita, che per ciascuna di essi
		// chiamerà un node. I nodi li aggiungo alla decList (lista dichiarazioni)
		for (DecContext dec : c.dec()) declist.add((DecNode) visit(dec));
		// passo la lista di dichiarazioni ed il nodo che ottengo visitando il corpo
		return new ProgLetInNode(declist, visit(c.exp()));
	}

	@Override
	public Node visitNoDecProg(NoDecProgContext c) {
		if (print) printVarAndProdName(c);
		return new ProgNode(visit(c.exp()));
	}

	@Override
	public Node visitTimesDiv(TimesDivContext ctx) {
		if (print) printVarAndProdName(ctx);

		/**
		 * Creo un timesNode, vuole l'espressione sinistra e destra, visito il figlio sinistro
		 * e destro del sintax tree.
		 * */
		if (ctx.TIMES() != null) {
			Node n = new TimesNode(visit(ctx.exp(0)), visit(ctx.exp(1)));
			n.setLine(ctx.TIMES().getSymbol().getLine());
			return n;
		}
		Node n = new DivNode(visit(ctx.exp(0)), visit(ctx.exp(1)));
		n.setLine(ctx.DIV().getSymbol().getLine());
		return n;
	}


	@Override
	public Node visitPlusMinus(PlusMinusContext ctx) {
		if (print) printVarAndProdName(ctx);
		//Checks if it's plus or minus operation
		if (ctx.PLUS() != null) {
			Node n = new PlusNode(visit(ctx.exp(0)), visit(ctx.exp(1)));
			n.setLine(ctx.PLUS().getSymbol().getLine());
			return n;
		}
		Node n = new MinusNode(visit(ctx.exp(0)), visit(ctx.exp(1)));
		n.setLine(ctx.MINUS().getSymbol().getLine());
		return n;
	}

	@Override
	public Node visitAndOr(AndOrContext ctx) {
		if (print) printVarAndProdName(ctx);
		//Checks if it's Or or and
		if (ctx.AND() != null) {
			Node n = new AndNode(visit(ctx.exp(0)), visit(ctx.exp(1)));
			n.setLine(ctx.AND().getSymbol().getLine());
			return n;
		}
		Node n = new OrNode(visit(ctx.exp(0)), visit(ctx.exp(1)));
		n.setLine(ctx.OR().getSymbol().getLine());
		return n;
	}

	@Override
	public Node visitNot(NotContext ctx) {
		if (print) printVarAndProdName(ctx);
		Node node = new NotNode(visit(ctx.exp()));
		node.setLine(ctx.NOT().getSymbol().getLine());
		return node;
	}

	@Override
	public Node visitComp(CompContext ctx) {
		if (print) printVarAndProdName(ctx);

		Node n = null;

		if (ctx.EQ() != null) {
			n = new EqualNode(visit(ctx.exp(0)), visit(ctx.exp(1)));
			n.setLine(ctx.EQ().getSymbol().getLine());
		} else if (ctx.GE() != null) {
			n = new GreaterEqualNode(visit(ctx.exp(0)), visit(ctx.exp(1)));
			n.setLine(ctx.GE().getSymbol().getLine());
		} else if (ctx.LE() != null) {
			n = new LessEqualNode(visit(ctx.exp(0)), visit(ctx.exp(1)));
			n.setLine(ctx.LE().getSymbol().getLine());
		} else {
			throw new RuntimeException("Unknown comparison operator");
		}

		return n;
	}


	/**
	 * Il varnode è usato per la dichiarazione di una variabile, controllo che sia rispettata
	 * la sintassi
	 * */
	@Override
	public Node visitVardec(VardecContext c) {
		if (print) printVarAndProdName(c);
		Node n = null;
		if (c.ID()!=null) { //non-incomplete ST
			//id, tipo (rappresentato a sua volta con un nodo), nodo
			n = new VarNode(c.ID().getText(), (TypeNode) visit(c.type()), visit(c.exp()));
			//numero di linea della variabile, per l'output
			//La recupero attraverso il token
			n.setLine(c.VAR().getSymbol().getLine());
		}
        return n;
	}

	@Override
	public Node visitFundec(FundecContext c) {
		if (print) printVarAndProdName(c);
		List<ParNode> parList = new ArrayList<>();
		for (int i = 1; i < c.ID().size(); i++) { 
			ParNode p = new ParNode(c.ID(i).getText(),(TypeNode) visit(c.type(i)));
			p.setLine(c.ID(i).getSymbol().getLine());
			parList.add(p);
		}
		// come nel caso della dichiarazione di variabile elenco le dichiarazioni e le memorizzo
		// in una lista
		List<DecNode> decList = new ArrayList<>();
		for (DecContext dec : c.dec()) decList.add((DecNode) visit(dec));
		Node n = null;
		if (c.ID().size()>0) { //non-incomplete ST
			// devo passare il nome della funzione (c.ID)
			// il tipo di ritorno
			// Mentre nelle variabili c'è solo un id, nelle funzioni ce ne sono diverse, ovviamente
			// selezioniamo il primo
			n = new FunNode(c.ID(0).getText(),(TypeNode)visit(c.type(0)),parList,decList,visit(c.exp()));
			n.setLine(c.FUN().getSymbol().getLine()); // setto il numero di linea
		}
        return n;
	}

	@Override
	public Node visitIntType(IntTypeContext c) {
		if (print) printVarAndProdName(c);
		return new IntTypeNode();
	}

	@Override
	public Node visitBoolType(BoolTypeContext c) {
		if (print) printVarAndProdName(c);
		return new BoolTypeNode();
	}

	@Override
	public Node visitInteger(IntegerContext c) {
		if (print) printVarAndProdName(c);
		int v = Integer.parseInt(c.NUM().getText());
		return new IntNode(c.MINUS()==null?v:-v);
	}

	@Override
	public Node visitTrue(TrueContext c) {
		if (print) printVarAndProdName(c);
		return new BoolNode(true);
	}

	@Override
	public Node visitFalse(FalseContext c) {
		if (print) printVarAndProdName(c);
		return new BoolNode(false);
	}

	@Override
	public Node visitIf(IfContext c) {
		if (print) printVarAndProdName(c);
		Node ifNode = visit(c.exp(0));
		Node thenNode = visit(c.exp(1));
		Node elseNode = visit(c.exp(2));
		Node n = new IfNode(ifNode, thenNode, elseNode);
		n.setLine(c.IF().getSymbol().getLine());			
        return n;		
	}

	@Override
	public Node visitPrint(PrintContext c) {
		if (print) printVarAndProdName(c);
		return new PrintNode(visit(c.exp()));
	}

	/**
	 * Ritorno direttamente il risultato della sua visita, perché non deve aggiungere oggetti java
	 * Le parentesi servono per costruire l'albero correttamente, ma le elimino, perché nell'ast non sono
	 * comprese.
	 * */
	@Override
	public Node visitPars(ParsContext c) {
		if (print) printVarAndProdName(c);
		return visit(c.exp());
	}


	/**
	 * IdNode -> uso di una variabile
	 * VarNode -> dichiarazione di una variabile
	 *
	 * Potrei avere un errore di variabile non dichiarata, quindi la uso per settare la linea.
	 * */
	@Override
	public Node visitId(IdContext c) {
		if (print) printVarAndProdName(c);
		Node n = new IdNode(c.ID().getText());
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}

	/**
	 * Chiamata di funzione senza parametri
	 * */
	@Override
	public Node visitCall(CallContext c) {
		if (print) printVarAndProdName(c);		
		List<Node> arglist = new ArrayList<>(); //elenco di argomenti
		for (ExpContext arg : c.exp()) arglist.add(visit(arg));
		Node n = new CallNode(c.ID().getText(), arglist);
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}
}
