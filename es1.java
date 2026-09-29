import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

// ============================================================================
// ESERCIZIO 1: Modulo di Validazione Centralina IoT
// ============================================================================

class LetturaInvalidaException extends RuntimeException {
    public LetturaInvalidaException(String message) {
        super(message);
    }
}

class LetturaSensore {
    private final Double temperatura;
    private final Integer umiditaPercentuale;
    private final Long timestampUnix;
    private final Boolean batteriaScarica;

    public LetturaSensore(Double temperatura, Integer umiditaPercentuale, Long timestampUnix, Boolean batteriaScarica) {
        this.temperatura = temperatura;
        this.umiditaPercentuale = umiditaPercentuale;
        this.timestampUnix = timestampUnix;
        this.batteriaScarica = batteriaScarica;
    }

    public Double getTemperatura() { return temperatura; }
    public Integer getUmiditaPercentuale() { return umiditaPercentuale; }
    public Long getTimestampUnix() { return timestampUnix; }
    public Boolean getBatteriaScarica() { return batteriaScarica; }

    public static Optional<LetturaSensore> parsePacchetto(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Optional.empty();
        }

        Double temp = null;
        Integer umid = null;
        Long ts = null;
        Boolean battLow = null;

        String[] tokens = raw.split(";");
        for (String token : tokens) {
            String[] kv = token.split("=");
            if (kv.length != 2) continue;

            String key = kv[0].trim();
            String value = kv[1].trim();

            try {
                switch (key) {
                    case "temp":
                        temp = Double.valueOf(value);
                        break;
                    case "umid":
                        umid = Integer.valueOf(value);
                        if (umid < 0 || umid > 100) {
                            throw new LetturaInvalidaException("Umidità fuori range [0-100]: " + umid);
                        }
                        break;
                    case "ts":
                        ts = Long.valueOf(value);
                        break;
                    case "batt_low":
                        battLow = Boolean.valueOf(value);
                        break;
                }
            } catch (NumberFormatException e) {
                System.err.println("[LOG PARSING ERROR] Campo " + key + " corrotto con valore: '" + value + "'");
            }
        }

        return Optional.of(new LetturaSensore(temp, umid, ts, battLow));
    }

    public static boolean confrontaBatteriaSbagliato(Integer b1, Integer b2) {
        return b1 == b2;
    }

    public static boolean confrontaBatteriaCorretto(Integer b1, Integer b2) {
        return Objects.equals(b1, b2);
    }

    @Override
    public String toString() {
        return String.format("Lettura[temp=%s, umid=%s, ts=%s, battLow=%s]",
                temperatura, umiditaPercentuale, timestampUnix, batteriaScarica);
    }
}

// ============================================================================
// ESERCIZIO 2: Analizzatore Log Server Web
// ============================================================================

class RichiestaHttp {
    String ip;
    String path;
    int statusCode;
    long tempoRispostaMs;
    long timestamp;

    public RichiestaHttp(String ip, String path, int statusCode, long tempoRispostaMs, long timestamp) {
        this.ip = ip;
        this.path = path;
        this.statusCode = statusCode;
        this.tempoRispostaMs = tempoRispostaMs;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("%s %s %d %dms", ip, path, statusCode, tempoRispostaMs);
    }
}

class GestoreLogWeb {
    private final List<RichiestaHttp> storicoCompleto = new ArrayList<>();
    private final LinkedList<RichiestaHttp> slidingWindow = new LinkedList<>();
    private final Set<String> ipSospetti = new HashSet<>();
    private final TreeSet<Long> tempiRispostaUnivoci = new TreeSet<>();

    public void elaboraRichiesta(RichiestaHttp req) {
        storicoCompleto.add(req);

        slidingWindow.addLast(req);
        if (slidingWindow.size() > 10) {
            slidingWindow.removeFirst();
        }

        if (req.statusCode >= 400) {
            ipSospetti.add(req.ip);
        }

        tempiRispostaUnivoci.add(req.tempoRispostaMs);
    }

    public List<RichiestaHttp> getUltimeNErroriServer(int n) {
        List<RichiestaHttp> risultato = new ArrayList<>();
        for (int i = storicoCompleto.size() - 1; i >= 0 && risultato.size() < n; i--) {
            RichiestaHttp r = storicoCompleto.get(i);
            if (r.statusCode >= 500) {
                risultato.add(r);
            }
        }
        return risultato;
    }

    public Long calcolaPercentile90() {
        if (tempiRispostaUnivoci.isEmpty()) return 0L;
        int size = tempiRispostaUnivoci.size();
        int targetIndex = (int) Math.ceil(0.90 * size) - 1;
        
        Iterator<Long> it = tempiRispostaUnivoci.iterator();
        Long val = 0L;
        for (int i = 0; i <= targetIndex && it.hasNext(); i++) {
            val = it.next();
        }
        return val;
    }

    public void generaReport() {
        long ipSospettiInFinestra = slidingWindow.stream()
                .filter(r -> ipSospetti.contains(r.ip))
                .map(r -> r.ip)
                .distinct()
                .count();

        System.out.println("\n--- REPORT LOG SERVER WEB ---");
        System.out.println("Totale richieste elaborate: " + storicoCompleto.size());
        System.out.println("IP sospetti unici (errori 4xx/5xx): " + ipSospetti.size());
        System.out.println("IP sospetti presenti nella finestra attuale (ultime 10 req): " + ipSospettiInFinestra);
        System.out.println("Tempo di risposta al 90° percentile: " + calcolaPercentile90() + " ms");
    }

    public LinkedList<RichiestaHttp> getSlidingWindow() { return slidingWindow; }
}

// ============================================================================
// ESERCIZIO 3: Scheduling Ticket Assistenza Tecnica
// ============================================================================

enum LivelloPriorita {
    CRITICO(0, 15),
    ALTO(1, 30),
    MEDIO(2, 60),
    BASSO(3, 120);

    private final int priorita;
    private final int tempoStimatoMinuti;

    LivelloPriorita(int priorita, int tempoStimatoMinuti) {
        this.priorita = priorita;
        this.tempoStimatoMinuti = tempoStimatoMinuti;
    }

    public int getPriorita() { return priorita; }
    public int getTempoStimatoMinuti() { return tempoStimatoMinuti; }

    public static Optional<LivelloPriorita> parse(String text) {
        try {
            return Optional.of(LivelloPriorita.valueOf(text.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

class Ticket implements Comparable<Ticket> {
    private final String id;
    private final String descrizione;
    private final LivelloPriorita livello;
    private final long timestampArrivo;

    public Ticket(String id, String descrizione, LivelloPriorita livello, long timestampArrivo) {
        this.id = id;
        this.descrizione = descrizione;
        this.livello = livello;
        this.timestampArrivo = timestampArrivo;
    }

    public String getId() { return id; }
    public String getDescrizione() { return descrizione; }
    public LivelloPriorita getLivello() { return livello; }
    public long getTimestampArrivo() { return timestampArrivo; }

    @Override
    public int compareTo(Ticket o) {
        int cmp = Integer.compare(this.livello.getPriorita(), o.livello.getPriorita());
        if (cmp != 0) {
            return cmp;
        }
        return Long.compare(this.timestampArrivo, o.timestampArrivo);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s, ts: %d)", id, descrizione, livello, timestampArrivo);
    }
}

class SchedulerTicket {

    public static void esegui(String inputFile, String outputFile, String logFile) {
        PriorityQueue<Ticket> codaLavorazione = new PriorityQueue<>();
        List<Ticket> criticiInAttesa = new ArrayList<>();

        int righeErrori = 0;
        int totaleTicketLetti = 0;

        Path pathInput = Paths.get(inputFile);
        try (BufferedReader reader = Files.newBufferedReader(pathInput);
             BufferedWriter logWriter = Files.newBufferedWriter(Paths.get(logFile))) {

            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine && line.startsWith("id,")) {
                    firstLine = false;
                    continue;
                }
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 4) {
                    logWriter.write("Riga malformata (campi insufficienti): " + line + "\n");
                    righeErrori++;
                    continue;
                }

                String id = parts[0].trim();
                String desc = parts[1].trim();
                Optional<LivelloPriorita> livOpt = LivelloPriorita.parse(parts[2]);
                
                if (!livOpt.isPresent()) {
                    logWriter.write("Riga malformata (livello non valido): " + line + "\n");
                    righeErrori++;
                    continue;
                }

                try {
                    long ts = Long.parseLong(parts[3].trim());
                    Ticket t = new Ticket(id, desc, livOpt.get(), ts);
                    codaLavorazione.add(t);
                    totaleTicketLetti++;

                    if (t.getLivello() == LivelloPriorita.CRITICO) {
                        criticiInAttesa.add(t);
                        if (criticiInAttesa.size() > 2) {
                            System.out.println("ALLARME CRITICO: Presenti " + criticiInAttesa.size() + " ticket CRITICI in attesa!");
                        }
                    }

                } catch (NumberFormatException e) {
                    logWriter.write("Riga malformata (timestamp invalido): " + line + "\n");
                    righeErrori++;
                }
            }

        } catch (FileNotFoundException e) {
            System.err.println("File di input non trovato: " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("Errore I/O durante la lettura: " + e.getMessage());
            return;
        }

        int tempoCumulativoMinuti = 0;
        int consecutiviCriticiOAlti = 0;
        Map<LivelloPriorita, Integer> conteggioPerLivello = new EnumMap<>(LivelloPriorita.class);
        for (LivelloPriorita l : LivelloPriorita.values()) conteggioPerLivello.put(l, 0);

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile))) {
            writer.write("ID;Descrizione;Livello;TempoCompletamentoStimato(min)\n");

            while (!codaLavorazione.isEmpty()) {
                Ticket t = codaLavorazione.poll();

                if (t.getLivello() == LivelloPriorita.CRITICO) {
                    criticiInAttesa.remove(t);
                }

                if (t.getLivello() == LivelloPriorita.CRITICO || t.getLivello() == LivelloPriorita.ALTO) {
                    consecutiviCriticiOAlti++;
                    if (consecutiviCriticiOAlti > 5) {
                        tempoCumulativoMinuti += 10;
                        consecutiviCriticiOAlti = 1; 
                    }
                } else {
                    consecutiviCriticiOAlti = 0;
                }

                tempoCumulativoMinuti += t.getLivello().getTempoStimatoMinuti();
                conteggioPerLivello.put(t.getLivello(), conteggioPerLivello.get(t.getLivello()) + 1);

                writer.write(String.format("%s;%s;%s;%d\n",
                        t.getId(), t.getDescrizione(), t.getLivello(), tempoCumulativoMinuti));
            }

            writer.write("\n=== RIEPILOGO LAVORAZIONE ===\n");
            writer.write("Totale ticket elaborati: " + totaleTicketLetti + "\n");
            for (Map.Entry<LivelloPriorita, Integer> entry : conteggioPerLivello.entrySet()) {
                writer.write("Livello " + entry.getKey() + ": " + entry.getValue() + "\n");
            }
            writer.write("Tempo totale stimato: " + tempoCumulativoMinuti + " minuti (" + (tempoCumulativoMinuti / 60.0) + " ore)\n");

        } catch (IOException e) {
            System.err.println("Errore scrittura file output: " + e.getMessage());
        }

        System.out.println("Elaborazione completata. Log errori: " + righeErrori + " righe scartate.");
    }
}

// ============================================================================
// MAIN DI COLLAUDO
// ============================================================================

public class MainApp {

    public static void main(String[] args) {
        System.out.println("========== ESERCIZIO 1: TEST IoT ==========");
        testEsercizio1();

        System.out.println("\n========== ESERCIZIO 2: TEST LOG WEB ==========");
        testEsercizio2();

        System.out.println("\n========== ESERCIZIO 3: TEST SCHEDULER ==========");
        testEsercizio3();
    }

    private static void testEsercizio1() {
        Integer b1 = 100;
        Integer b2 = 100;
        Integer b3 = 200;
        Integer b4 = 200;

        System.out.println("Test Integer Cache (100 == 100): " + LetturaSensore.confrontaBatteriaSbagliato(b1, b2));
        System.out.println("Test Integer Cache (200 == 200): " + LetturaSensore.confrontaBatteriaSbagliato(b3, b4));
        System.out.println("Test Corretto Equals (200.equals(200)): " + LetturaSensore.confrontaBatteriaCorretto(b3, b4));

        String[] pacchetti = {
            "temp=23.5;umid=61;ts=1732000000;batt_low=false",
            "temp=18.2;umid=45;ts=1732000100;batt_low=true",
            "temp=xx.xx;umid=50;ts=1732000200;batt_low=false",
            "temp=31.0;umid=150;ts=1732000300;batt_low=false",
            "umid=80;ts=1732000400",
            "temp=12.4;ts=1732000500;batt_low=false",
            "corrotto_senza_chiavi",
            "temp=26.8;umid=55;ts=1732000700;batt_low=false"
        };

        List<LetturaSensore> lettureValide = new ArrayList<>();
        int erroriParsing = 0;

        for (String p : pacchetti) {
            try {
                Optional<LetturaSensore> res = LetturaSensore.parsePacchetto(p);
                if (res.isPresent()) {
                    LetturaSensore l = res.get();
                    lettureValide.add(l);
                    System.out.println("Parsed: " + l);
                } else {
                    erroriParsing++;
                }
            } catch (LetturaInvalidaException e) {
                System.err.println("[EXCEPT] Pacchetto scartato per validazione fallita: " + e.getMessage());
                erroriParsing++;
            }
        }

        double sommaTemp = 0.0;
        int contTemp = 0;
        for (LetturaSensore l : lettureValide) {
            if (l.getTemperatura() != null) {
                sommaTemp += l.getTemperatura();
                contTemp++;
            }
        }
        double mediaTemp = contTemp > 0 ? sommaTemp / contTemp : 0.0;

        System.out.println("\n--- REPORT IOT ---");
        System.out.println("Letture valide: " + lettureValide.size());
        System.out.println("Errori parsing/validazione: " + erroriParsing);
        System.out.println("Media temperature valide: " + mediaTemp + " °C");

        lettureValide.sort((l1, l2) -> {
            if (l1.getTemperatura() == null && l2.getTemperatura() == null) return 0;
            if (l1.getTemperatura() == null) return 1;
            if (l2.getTemperatura() == null) return -1;
            return Double.compare(l2.getTemperatura(), l1.getTemperatura());
        });

        System.out.println("\n--- BONUS: Letture ordinate per Temp Decrescente ---");
        lettureValide.forEach(System::println);
    }

    private static void testEsercizio2() {
        GestoreLogWeb gestore = new GestoreLogWeb();
        Random rand = new Random(42);
        String[] ips = {"192.168.1.1", "10.0.0.5", "172.16.0.1", "192.168.1.1", "185.220.101.5"};
        String[] paths = {"/home", "/login", "/api/data", "/checkout"};
        int[] statusCodes = {200, 200, 200, 404, 500, 503};

        for (int i = 0; i < 35; i++) {
            String ip = ips[rand.nextInt(ips.length)];
            String path = paths[rand.nextInt(paths.length)];
            int status = statusCodes[rand.nextInt(statusCodes.length)];
            long tempo = 20 + rand.nextInt(480);
            gestore.elaboraRichiesta(new RichiestaHttp(ip, path, status, tempo, System.currentTimeMillis() + i));
        }

        gestore.generaReport();

        System.out.println("\nUltime 3 richieste con Status >= 500 nello storico:");
        gestore.getUltimeNErroriServer(3).forEach(System::println);
    }

    private static void testEsercizio3() {
        String inputFile = "ticket.csv";
        String outputFile = "report_lavorazione.txt";
        String logFile = "errori_parsing.log";

        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(inputFile))) {
            bw.write("id,descrizione,livello,timestampArrivo\n");
            bw.write("T001,Server di produzione irraggiungibile,CRITICO,1732000000\n");
            bw.write("T002,Richiesta nuovo account email,BASSO,1732000100\n");
            bw.write("T003,Stampante piano 2 non funziona,MEDIO,1732000050\n");
            bw.write("T004,Database aziendale bloccato,CRITICO,1732000010\n");
            bw.write("T005,Errore di battitura interfaccia,invalido_level,1732000020\n");
            bw.write("T006,Sito web non raggiungibile,CRITICO,1732000005\n");
            bw.write("T007,Attacco DDoS in corso,CRITICO,1732000002\n");
            bw.write("T008,Upgrade RAM server 3,ALTO,1732000200\n");
            bw.write("T009,Configurazione VPN,MEDIO,1732000250\n");
            bw.write("T010,Sostituzione mouse,BASSO,1732000300\n");
            bw.write("T011,Backup fallito,ALTO,1732000150\n");
            bw.write("T012,Ransomware rilevato,CRITICO,1732000001\n");
            bw.write("T013,Lentezza rete Wi-Fi,MEDIO,1732000400\n");
            bw.write("T014,Riga Senza Campi Sufficienti\n");
            bw.write("T015,Reset Password,BASSO,1732000500\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        SchedulerTicket.esegui(inputFile, outputFile, logFile);
    }
}
