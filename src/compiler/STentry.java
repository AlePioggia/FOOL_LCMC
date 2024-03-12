package compiler;

import compiler.lib.*;

public class STentry implements Visitable {
	final int nl; // nesting level
	final TypeNode type; //tipo
	final int offset;
	public STentry(int n, TypeNode t, int o) { nl = n; type = t; offset=o; }

	/**
	 * Funziona come gli altri accept dell'ast.
	 * L'unica differenza è che l'accept chiamata su una st entry può accadere solo su un EASTVisitor.
	 * Quindi vado tranquillo
	 * */
	@Override
	public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {
		return ((BaseEASTVisitor<S,E>) visitor).visitSTentry(this);
	}
}
