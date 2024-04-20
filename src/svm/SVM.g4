grammar SVM;

/**
Mette le import nelle classi che genera
*/
@parser::header {
import java.util.*;
}

/**
Quelli che dichiaro qui vengono messi come campi in svmParser e svmLexer, quindi accessibili durante
il parsing.
*/
@lexer::members {
public int lexicalErrors=0;
}

/**
Generiamo il codice oggetto dentro l'array d'interi code. Lo creiamo vuoto, usando come size una
costante, definita nella VM (e modificabile), di dimensione 10.000. In breve riempio l'array
con le varie istruzioni. Indirizzo 0 = prima istruzione*/

@parser::members { 
public int[] code = new int[ExecuteVM.CODESIZE];    
private int i = 0;
private Map<String,Integer> labelDef = new HashMap<>();
private Map<Integer,String> labelRef = new HashMap<>();
}

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

/**
Ci viene dato un file in asm e dobbiamo parsarlo in un array di numeri.

assembly = insieme di istruzioni.

Faccio un ciclo for, scandendo i label ref, ciclando in funzione degli indirizzi dei buchi e li
riempio tutti con le dichiarazioni.
*/

assembly: instruction* EOF 	{ for (Integer j: labelRef.keySet()) 
								code[j]=labelDef.get(labelRef.get(j)); 
							} ;

/**
La variabile iniziale è S.
Trasformiamo ogni istruzione in un codice numerico, poi dovremmo risolvere le etichette.

*/

/**
Ogni volta che aggiungo una nuova istruzione, incremento l'indice i.
ANTLR produce un valore numerico per ogni token, infatti nel file che genera, mostra che ogni
istruzione è associata ad un numero univoco. I token sono definiti come delle costanti.
Noi useremo queste costanti.
*/

/**
Ci sono istruzioni che necessitano di argomenti, come ad esempio la push.
Infatti aggiungo, nella posizione successiva l'argomento (intero). In particolare sto leggendo il lessema
che ha matchato con il token. n = INTEGER mi fa accedere al lessema che ha fatto match con integer.
*/

/**
LABEL:
Abbiamo un codice sequenziale, dove ogni tanto c'è una label "l1:", in cui ci possono essere salti
prima o dopo. Posso avere dichiarazioni di label o jump a label. Dobbiamo capire a che indirizzo sono.
Se incontro 'l1: ' quando ci saranno dei jump, dovremo sostituire quell'indirizzo con quello di l1.
Come facciamo a gestirlo?
Se incontriamo l1, ma ancora non è stato fatto il jump, dobbiamo modellare il fatto che sia un riferimento
irrisolto, lasciamo dunque un buco. Lo si fa elencando una lista di buchi, ovvero labelRef.
labelRef si ricorda che in quel punto c'è un buco, l'intero è la posizione nell'array del buco, mentre la
string è l'etichetta.
*/

/**
Gestione jump (branch)

1. Nella push label, lascio un buco quindi, faccio i++ e aggiungo l'indirizzo del buco in labelRef.
Stesso discorso vale sia per i salti condizionati che incondizionati;
2.In l=LABEL COL, aggiungo la dichiarazione. E devo farlo puntare alla prossima istruzione, con i, visto che
l'ho post-incrementato;
3. Riempio i buchi, nell'assembly: instruction


*/

instruction : 
        PUSH n=INTEGER   {code[i++] = PUSH; 
			              code[i++] = Integer.parseInt($n.text);}
	  | PUSH l=LABEL    {code[i++] = PUSH; 
	    		             labelRef.put(i++,$l.text);} 		     
	  | POP		    {code[i++] = POP;}	
	  | ADD		    {code[i++] = ADD;}
	  | SUB		    {code[i++] = SUB;}
	  | MULT	    {code[i++] = MULT;}
	  | DIV		    {code[i++] = DIV;}
	  | STOREW	  {code[i++] = STOREW;} //
	  | LOADW           {code[i++] = LOADW;} //
	  | l=LABEL COL     {labelDef.put($l.text,i);}
	  | BRANCH l=LABEL  {code[i++] = BRANCH;
                       labelRef.put(i++,$l.text);}
	  | BRANCHEQ l=LABEL {code[i++] = BRANCHEQ;
                        labelRef.put(i++,$l.text);}
	  | BRANCHLESSEQ l=LABEL {code[i++] = BRANCHLESSEQ;
                          labelRef.put(i++,$l.text);}
	  | JS              {code[i++] = JS;}		     //
	  | LOADRA          {code[i++] = LOADRA;}    //
	  | STORERA         {code[i++] = STORERA;}   //
	  | LOADTM          {code[i++] = LOADTM;}   
	  | STORETM         {code[i++] = STORETM;}   
	  | LOADFP          {code[i++] = LOADFP;}   //
	  | STOREFP         {code[i++] = STOREFP;}   //
	  | COPYFP          {code[i++] = COPYFP;}   //
	  | LOADHP          {code[i++] = LOADHP;}   //
	  | STOREHP         {code[i++] = STOREHP;}   //
	  | PRINT           {code[i++] = PRINT;}
	  | HALT            {code[i++] = HALT;}
	  ;
	  
/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

/**
Abbiamo un token per ogni espressione
*/


PUSH	 : 'push' ; 	
POP	 : 'pop' ; 	
ADD	 : 'add' ;  	
SUB	 : 'sub' ;	
MULT	 : 'mult' ;  	
DIV	 : 'div' ;	
STOREW	 : 'sw' ; 	
LOADW	 : 'lw' ;	
BRANCH	 : 'b' ;	
BRANCHEQ : 'beq' ;	
BRANCHLESSEQ:'bleq' ;	
JS	 : 'js' ;	
LOADRA	 : 'lra' ;	
STORERA  : 'sra' ;	 
LOADTM	 : 'ltm' ;	
STORETM  : 'stm' ;	
LOADFP	 : 'lfp' ;	
STOREFP	 : 'sfp' ;	
COPYFP   : 'cfp' ;      
LOADHP	 : 'lhp' ;	
STOREHP	 : 'shp' ;	
PRINT	 : 'print' ;	
HALT	 : 'halt' ;	

 /** : ci serve per separare l1 dall'istruzione a cui punta. Lavoreremo con numeri interi, ci sono anche i
 negativi. ? -> metto il - opzionale.*/
COL	 : ':' ;
LABEL	 : ('a'..'z'|'A'..'Z')('a'..'z' | 'A'..'Z' | '0'..'9')* ;
INTEGER	 : '0' | ('-')?(('1'..'9')('0'..'9')*) ;

//channel(HIDDEN) indica che le espressioni specificate, non producono token in uscita.
COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;

WHITESP  : (' '|'\t'|'\n'|'\r')+ -> channel(HIDDEN) ;

ERR	     : . { System.out.println("Invalid char: "+getText()+" at line "+getLine()); lexicalErrors++; } -> channel(HIDDEN); 

