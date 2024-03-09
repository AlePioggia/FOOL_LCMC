package svm;

/**
 * Questa classe contiene il ciclo fetch-execute delle istruzioni.
 * Usa dei registri, implementati attraverso dei campi. L'indirizzo ip (instruction pointer) contiene
 * l'indirizzo dell'istruzione da eseguire.
 * Quando salto, punta alla nuova istruzione da eseguire. Viene settato a zero, perché è da quell'indirizzo
 * che iniziamo ad eseguire le istruzioni.
 *
 * Lo stack ha uno stack pointer, è il registro sp. Generalmente punta all'indirizzo più alto della memoria.
 * Questo perché cresce verso il basso. Noi abbiamo due array di interi. Uno dove mettiamo il codice
 * (grandezza) codesize e l'altra viene usata per la memoria, usata per lo stack.
 * Quando faccio una push punto alla cima della memoria. Dunque quando pusho, prima decremento, poi aggiungo
 * il valore.
 * */

public class ExecuteVM {
    
    public static final int CODESIZE = 10000;
    public static final int MEMSIZE = 10000;
    
    private int[] code;
    private int[] memory = new int[MEMSIZE];
    
    private int ip = 0;
    private int sp = MEMSIZE;
    
    private int hp = 0;       
    private int fp = MEMSIZE; 
    private int ra;
    private int tm;

    /**
     * Il code verrà passato al costruttore, dall'assemblatore. code contiene il codice da eseguire.
     * Poi viene eseguita la cpu che esegue il ciclo fetch-execute:
     * -> fetch: prendo il codice numerico della prossima istruzione da eseguire;
     *      post-incremento ip, perché a meno che ci siano salti, passo all'istruzione successiva
     * -> execute: in funzione dell'istruzione, la eseguirà. Questo approccio viene seguito attraverso
     * l'utilizzo dello switch
     * */
    public ExecuteVM(int[] code) {
      this.code = code;
    }
    
    public void cpu() {
      while ( true ) {
        int bytecode = code[ip++]; // fetch
        int v1,v2;
        int address;
        switch ( bytecode ) {
          case SVMParser.PUSH:
            /** devo mettere nello stack cià che viene dopo */
            push( code[ip++] );
            break;
          case SVMParser.POP:
            pop();
            break;
          case SVMParser.ADD :
            v1=pop();
            v2=pop();
            push(v2 + v1);
            break;
          case SVMParser.MULT :
            v1=pop();
            v2=pop();
            push(v2 * v1);
            break;
          case SVMParser.DIV :
            v1=pop();
            v2=pop();
            push(v2 / v1);
            break;
          case SVMParser.SUB :
            v1=pop();
            v2=pop();
            push(v2 - v1);
            break;
          case SVMParser.STOREW : //
            address = pop();
            memory[address] = pop();    
            break;
          case SVMParser.LOADW : //
            push(memory[pop()]);
            break;
          case SVMParser.BRANCH : // fa un salto incondizionato
            //aggiorno l'instruction pointer
            address = code[ip];
            ip = address;
            break;
          case SVMParser.BRANCHEQ :
            address = code[ip++];  //visto che il salto è condizionato, faccio post-incremento
            v1=pop();
            v2=pop();
            if (v2 == v1) ip = address;
            break;
          case SVMParser.BRANCHLESSEQ :
            address = code[ip++];
            v1=pop();
            v2=pop();
            if (v2 <= v1) ip = address;
            break;
          case SVMParser.JS : //
            address = pop();
            ra = ip;
            ip = address;
            break;
         case SVMParser.STORERA : //
            ra=pop();
            break;
         case SVMParser.LOADRA : //
            push(ra);
            break;
         case SVMParser.STORETM : //prendo il valore che poppo dallo stack e lo metto sulla tm
            tm=pop();
            break;
         case SVMParser.LOADTM : //carico il registro TM sullo stack
            push(tm);
            break;
         case SVMParser.LOADFP : //
            push(fp);
            break;
         case SVMParser.STOREFP : //
            fp=pop();
            break;
         case SVMParser.COPYFP : //
            fp=sp;
            break;
         case SVMParser.STOREHP : //
            hp=pop();
            break;
         case SVMParser.LOADHP : //
            push(hp);
            break;
         case SVMParser.PRINT : // stampa il valore che c'è nella cima nello stack
             //Devo verificare che lo stack non sia vuoto. se sp < MEMSIZE, non è vuoto
            System.out.println((sp<MEMSIZE)?memory[sp]:"Empty stack!");
            break;
         case SVMParser.HALT :
            return;
        }
      }
    } 

    /**
     * Gli faccio tornare l'elemento di indice sp, poi con il post-incremento, lo faccio
     * puntare al prossimo valore in cima allo stack.
     * */
    private int pop() {
      return memory[sp++];
    }

    /**
     * Push di uno stack che cresce verso il basso
     *
     * 1. Decremento lo stack pointer
     * 2. Aggiungo il valore
     * */
    private void push(int v) {
      memory[--sp] = v;
    }
    
}