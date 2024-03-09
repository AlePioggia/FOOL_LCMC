package compiler.lib;

/**
 * Node dichiara di implementa
 *
 * */

public interface Visitable {

	/* performs the node specific visit**/

	/*
	* S viene determinato, quando chiamo l'accept, in base a quale ast visitor riceve.
	**/
	<S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E;

}
