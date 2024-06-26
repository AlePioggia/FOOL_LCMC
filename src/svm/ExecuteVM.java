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
    
    private int ip = 0; //instruction pointer
    private int sp = MEMSIZE;   //stack pointer
    
    private int hp = 0; //heap pointer
    private int fp = MEMSIZE; //frame pointer
    private int ra; //return address
    private int tm; //temporary storage

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
            /* devo mettere nello stack ciò che viene dopo */
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
          case SVMParser.STOREW :       //legge due cose dallo stack,
            address = pop();            //prima l'indirizzo in cui mettere il valore,
            memory[address] = pop();    //poi la seconda è il valore da mettere in quell'indirizzo
            break;
          case SVMParser.LOADW : //prende dallo stack con una pop l'indirizzo e accede alla memoria, a quell'indirizzo.
            push(memory[pop()]); // Da lì carica il valore nello stack.
            break;
          case SVMParser.BRANCH : // fa un salto incondizionato, aggiorno l'instruction pointer
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
            //Differenza JS e BRANCH -> il js è un jump di subroutine, significa che implicitamente
            // voglio tornare indietro prima o poi prendo l'indirizzo dallo stack
            case SVMParser.JS :
            address = pop();
            ra = ip;    //memorizzo l'indirizzo di ritorno in ra
            ip = address;   //torno indietro
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
         //E' comodo avere copyFp, che prende la cima attuale dello stack e lo copia dentro fp.
         case SVMParser.COPYFP : //FP punta a frame, ovvero pezzi dello stack.
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