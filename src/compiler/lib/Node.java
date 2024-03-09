package compiler.lib;

public abstract class Node implements Visitable {

	/**
	 * Line serve per la gestione degli errori, se ho un errore lessicale o sintattico, la linea dell'errore
	 * la gestisce automaticamente antlr (fa un parsing del file di testo).
	 * Il problema sono gli errori nelle fasi successive, ovviamente legati all'analisi semantica.
	 *
	 * Node dichiara di implementare visitable, la stessa cosa viene fatta per L'STEntry
	*/
	int line=-1;  // line -1 means unset
	
	public void setLine(int l) { line=l; }

	public int getLine() { return line; }

}

	  