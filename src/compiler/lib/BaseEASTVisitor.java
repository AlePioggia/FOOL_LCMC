package compiler.lib;

import compiler.*;
import compiler.exc.UnimplException;

/**
 * Questo ci dà in più, il metodo che ci consente di visitare l'EASTentry. Ha come base i metodi di visita dell'ast,
 * oltre al metodo per la visita degli stentry
 *
 * ie sta per incomplete exception, è stata aggiunta per gestire gli alberi incompleti. Se true lancia l'eccezione,
 * se false no.
 * */

public class BaseEASTVisitor<S,E extends Exception> extends BaseASTVisitor<S,E>  {
	
	protected BaseEASTVisitor() {}
	protected BaseEASTVisitor(boolean ie) { super(ie); } 
	protected BaseEASTVisitor(boolean ie, boolean p) { super(ie,p); } 
     
    protected void printSTentry(String s) {
    	System.out.println(indent+"STentry: "+s);
	}
	
	public S visitSTentry(STentry s) throws E {throw new UnimplException();}
}
