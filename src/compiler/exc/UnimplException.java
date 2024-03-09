package compiler.exc;

/*
Estendo runtime exception invece che Exception perché questa è un'eccezione unchecked,
non è controllata dal compilatore.
Questa eccezione ci serve nel caso in cui ci siamo dimenticati di implementare una visita (tipo il PlusNode).
Perché in assenza di questa eccezione, la stampa viene male, però non mi viene segnalato l'errore.
* **/

public class UnimplException extends RuntimeException {

	private static final long serialVersionUID = 1L;

}
