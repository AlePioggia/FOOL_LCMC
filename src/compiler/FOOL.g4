grammar FOOL;
 
@lexer::members {
public int lexicalErrors=0;
}
   
/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/
  
prog : progbody EOF ;

/**
Corpo del programma con almeno una dichiarazione (di variabile o di funzione)
Il let in è un tipo di funzione in cui nel let definisco tutte le variabili che la funzione utilizzerà.
L'in è invece il corpo.
*/
progbody : LET ( cldec+ dec* | dec+ ) IN exp SEMIC #letInProg
         | exp SEMIC                               #noDecProg
         ;

cldec  : CLASS ID (EXTENDS ID)?
              LPAR (ID COLON type (COMMA ID COLON type)* )? RPAR
              CLPAR
                   methdec*
              CRPAR ;

methdec : FUN ID COLON type
              LPAR (ID COLON type (COMMA ID COLON type)* )? RPAR
                   (LET dec+ IN)? exp
              SEMIC ;

/*
Qui ho la dichiarazione di
variabile ->  var identificatore : tipo = espressione;
funzione -> fun identificatore : tipo (identificatore, *);

Nel type checking:
- per var controllo che ID() non sia null.
- per la dichiarazione di funzione, controllo il numero di parametri (che so già essere non null)
**/

dec : VAR ID COLON type ASS exp SEMIC #vardec
    | FUN ID COLON type
          LPAR (ID COLON type (COMMA ID COLON type)* )? RPAR
               (LET dec+ IN)? exp
          SEMIC #fundec
    ;
/**
Non indico il return perché è implicito, nel paradigma funzionale, che un'operazione ritorni un valore
#call rappresenta la chiamata di funzione.  Caso ? -> nessun argomento. exp se ho 1 argomento.
exp (comma exp) se ho più argomenti, separati da virgola

La ricorsione a sinistra, qui viene trasformata in ricorsione a destra
*/

exp     : exp (TIMES | DIV) exp #timesDiv
        | exp (PLUS | MINUS) exp #plusMinus
        | exp (EQ | GE | LE) exp #comp
        | exp (AND | OR) exp #andOr
	    | NOT exp #not
        | LPAR exp RPAR #pars
    	| MINUS? NUM #integer
	    | TRUE #true
	    | FALSE #false
	    | NULL #null
	    | NEW ID LPAR (exp (COMMA exp)* )? RPAR #new
	    | IF exp THEN CLPAR exp CRPAR ELSE CLPAR exp CRPAR #if
	    | PRINT LPAR exp RPAR #print
        | ID #id
	    | ID LPAR (exp (COMMA exp)* )? RPAR #call
	    | ID DOT ID LPAR (exp (COMMA exp)* )? RPAR #dotCall
        ;


type    : INT #intType
        | BOOL #boolType
 	    | ID #idType
 	    ;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

PLUS  	: '+' ;
MINUS   : '-' ;
TIMES   : '*' ;
DIV 	: '/' ;
LPAR	: '(' ;
RPAR	: ')' ;
CLPAR	: '{' ;
CRPAR	: '}' ;
SEMIC 	: ';' ;
COLON   : ':' ;
COMMA	: ',' ;
DOT	    : '.' ;
OR	    : '||';
AND	    : '&&';
NOT	    : '!' ;
GE	    : '>=' ;
LE	    : '<=' ;
EQ	    : '==' ;
ASS	    : '=' ;
TRUE	: 'true' ;
FALSE	: 'false' ;
IF	    : 'if' ;
THEN	: 'then';
ELSE	: 'else' ;
PRINT	: 'print' ;
LET     : 'let' ;
IN      : 'in' ;
VAR     : 'var' ;
FUN	    : 'fun' ;
CLASS	: 'class' ;
EXTENDS : 'extends' ;
NEW 	: 'new' ;   //OO extension
NULL    : 'null' ;  //OO extension
INT	    : 'int' ;
BOOL	: 'bool' ;
NUM     : '0' | ('1'..'9')('0'..'9')* ;

ID  	: ('a'..'z'|'A'..'Z')('a'..'z' | 'A'..'Z' | '0'..'9')* ; //OO extension


WHITESP  : ( '\t' | ' ' | '\r' | '\n' )+    -> channel(HIDDEN) ;

COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;

ERR   	 : . { System.out.println("Invalid char: "+ getText() +" at line "+getLine()); lexicalErrors++; } -> channel(HIDDEN);


