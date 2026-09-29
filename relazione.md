# Relazione Tecnica e Documentazione di Progetto

## Esercizio 1 — Modulo di validazione per una centralina IoT (Wrapper Classes)

### 1. Motivazione dell'uso delle Classi Wrapper vs Tipi Primitivi
Nei sistemi IoT di rilevamento ambientale, l'assenza di dato è un'informazione semantica distinta da zero o da `false`.
- Se si utilizzasse `double temperatura`, un sensore guasto imporrebbe l'uso di un valore di default come `0.0`, ma `0.0 °C` è una temperatura reale.
- Se si utilizzasse `boolean batteriaScarica`, verrebbe inizializzato a `false`, mascherando un'eventuale anomalia non rilevata.
  Le classi Wrapper (`Double`, `Integer`, `Long`, `Boolean`) assumono valore `null` per rappresentare l'assenza di dato.

### 2. Differenza tra `Integer.parseInt` e `Integer.valueOf`
- `Integer.parseInt(String s)`: Restituisce un tipo primitivo `int`.
- `Integer.valueOf(String s)`: Restituisce un oggetto `Integer`, sfruttando la cache interna per [-128, 127].

### 3. Trabocchetto sull'Autoboxing e la Integer Cache
In Java, la JVM mantiene una cache per `Integer` con valori in `[-128, 127]`.
- Con `==`, i valori entro il range risultano `true` (stesso oggetto in memoria).
- Fuori da quel range, `==` restituisce `false` poiché confronta due riferimenti distinti.
  Correzione: Usare `.equals()` o `Objects.equals(a, b)`.

### 4. Pericolo della sottrazione manuale tra Wrapper
- **Underflow/Overflow:** Rischi di wrap-around.
- **Troncamento nei Double:** Il cast a `int` annulla le differenze decimali.
- **NullPointerException:** Sottraendo wrapper `null` si ottiene una NPE per unboxing.
  Soluzione: Usare `Double.compare(a, b)`.

---

## Esercizio 2 — Analizzatore di Log Web (Collezioni Java)

### Tabella Comparativa e Giustificazione delle Strutture Dati

| Struttura Scelta | Operazione Critica | Complessità (Scelta) | Alternativa Scartata | Complessità (Scartata) | Motivazione Tecnica |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`ArrayList<RichiestaHttp>`** | Inserimento in coda e ricerca per indice | **O(1)** ammortizzato | `LinkedList` | O(1) append, **O(n)** indice | Minima occupazione di memoria e locality of reference. |
| **`LinkedList<RichiestaHttp>`** | Inserimento (`addLast`) e rimozione testa (`removeFirst`) | **O(1)** | `ArrayList` | **O(n)** per `remove(0)` | Sliding window di 10 elementi: evita lo shift di tutti gli elementi. |
| **`HashSet<String>`** | Controllo unicità ed esistenza IP sospetti | **O(1)** medio | `ArrayList` | **O(n)** | Inserimento e ricerca in tempo costante. |
| **`TreeSet<Long>`** | Mantenimento dell'ordine e navigazione nei percentili | **O(log n)** | `ArrayList` + `Collections.sort()` | **O(n log n)** | Mantiene gli elementi ordinati permettendo range queries. |

---

## Esercizio 3 — Scheduling Ticket (PriorityQueue e File I/O)

### 1. Scelta di `java.nio.file.Files` / `BufferedReader`
- Efficienza I/O con buffering a blocchi.
- Chiusura automatica delle risorse via try-with-resources.

### 2. Algoritmo di Comparazione Custom (`Ticket.compareTo`)
1. Priorità per Livello: `CRITICO` (0) > `ALTO` (1) > `MEDIO` (2) > `BASSO` (3).
2. Priorità Temporale (FIFO): `timestampArrivo` minore ha precedenza.
