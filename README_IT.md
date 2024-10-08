## Readme
[README: Italiano](./README_IT.md)  
[README: English](./README.md)

Jail Mod (Fabric)
=================

Il **Jail Mod** è una mod lato server che permette di imprigionare i giocatori in una prigione virtuale, impedendo loro di interagire con il mondo finché non vengono rilasciati. È perfetta per server Minecraft dove desideri imporre penalità temporanee o limitare temporaneamente il movimento di determinati giocatori.

----------------------------------------------------------------

Caratteristiche Principali
--------------------------

* **Imprigionamento Temporaneo**: Puoi imprigionare un giocatore per un periodo di tempo specifico, bloccando le interazioni con blocchi, entità e oggetti, come secchi di lava o acqua.
* **Rilascio Automatico o Manuale**: Il giocatore viene rilasciato automaticamente dopo il tempo impostato oppure un amministratore può rilasciarlo manualmente.
* **Motivo dell'Imprigionamento**: Quando imprigioni un giocatore, puoi specificare un motivo che verrà comunicato al giocatore.
* **Comando per Conoscere il Tempo Rimanente**: I giocatori imprigionati possono controllare il tempo rimanente fino al loro rilascio.

Come Usare:
----------

1. **Costruisci una Prigione** (una struttura chiusa).
2. **Imposta le Coordinate** dove desideri che il prigioniero appaia con il comando `/jail set x y z` (esempio `/jail set 0 60 0`).
3. **Ricarica la Configurazione** utilizzando `/jail reload`.
4. **Manda Qualcuno in Prigione** con `/jail nomegiocatore 120 Griefing`.
5. **Rilascio Anticipato**: Se non desideri attendere il tempo di prigionia impostato in secondi (esempio **120** secondi), puoi liberare il giocatore anticipatamente con il comando `/unjail nomegiocatore`.

Requisiti
---------

* **Minecraft 1.21 o successivo**
* **Fabric API**

Installazione
-------------

1. **Inserisci il File `.jar` della Mod** nella cartella `mods` del server Minecraft.
2. **Avvia il Server** per generare i file di configurazione.

Comandi Disponibili
-------------------

### 1. `/jail player time reason`

* **Descrizione**: Imprigiona un giocatore per un tempo specificato in secondi, specificando il motivo.
* **Chi può usarlo**: Solo amministratori o operatori del server.
* **Sintassi**: `/jail nome_giocatore tempo_in_secondi motivo`
* **Esempio**: `/jail Steve 300 Griefing`  
  Questo comando imprigiona il giocatore `Steve` per 300 secondi (5 minuti) con il motivo "Griefing".

### 2. `/unjail player`

* **Descrizione**: Rilascia manualmente un giocatore dalla prigione prima che il tempo scada.
* **Chi può usarlo**: Solo amministratori o operatori del server.
* **Sintassi**: `/unjail nome_giocatore`
* **Esempio**: `/unjail Steve`  
  Questo comando rilascerà manualmente `Steve` dalla prigione.

### 3. `/jail info`

* **Descrizione**: Permette ai giocatori incarcerati di vedere il tempo rimanente fino al rilascio e il motivo dell'incarcerazione.
* **Chi può usarlo**: Solo giocatori incarcerati.
* **Esempio**: `/jail info`  
  Questo comando restituirà un messaggio simile a: "Sei in prigione per altri 200 secondi. Motivo: Griefing."

### 4. `/jail reload`

* **Descrizione**: Ricarica la configurazione e i messaggi linguistici della mod senza riavviare il server.
* **Chi può usarlo**: Solo amministratori o operatori del server.
* **Esempio**: `/jail reload`  
  Questo comando ricarica la configurazione della mod, utile se i file di configurazione sono stati modificati.

### 5. `/jail set`

* **Descrizione**: Imposta le coordinate dove i giocatori incarcerati appariranno. Questo è il punto in cui i giocatori saranno teletrasportati quando vengono mandati in prigione.
* **Chi può usarlo**: Solo gli admin o gli operatori del server.
* **Sintassi**: `/jail set x y z`
* **Esempio**: `/jail set 0 60 0`  
  Questo comando imposta la posizione di spawn della prigione alle coordinate (0, 60, 0).

Interazioni Bloccate Durante la Detenzione
------------------------------------------

Quando un giocatore è in prigione, non può fare quanto segue:

* Utilizzare **blocchi**, come porte, leve o pulsanti.
* Interagire con **entità**, come villager o animali.
* Usare **secchi di lava o acqua**.
* Rompere o posizionare blocchi.

Rilascio Automatico
-------------------

* I giocatori verranno rilasciati automaticamente dalla prigione quando il tempo impostato sarà scaduto.
* Mentre sono in prigione, i giocatori possono controllare il tempo rimanente utilizzando il comando `/jail info`.

File di Configurazione
----------------------

### `config/jailmod/config.json`

Questo file viene generato automaticamente e ti permette di configurare la posizione della prigione e la posizione di rilascio del giocatore. Ecco le opzioni che puoi trovare:

* **`use_previous_position`**: Se impostato su `true`, i giocatori verranno rilasciati nella posizione in cui si trovavano prima di essere imprigionati. Se impostato su `false`, verranno rilasciati in una posizione specifica.
* **`release_position`**: Definisce la posizione di rilascio predefinita con le coordinate `x`, `y`, `z`, attiva se `use_previous_position` è impostato su `false`.
* **`jail_position`**: Definisce la posizione della prigione con le coordinate `x`, `y`, `z`.

#### Esempio di Configurazione:

```
{
  "use_previous_position": true,
  "release_position": {
    "x": 100,
    "y": 65,
    "z": 100
  },
  "jail_position": {
    "x": 0,
    "y": 60,
    "z": 0
  }
}
```

### `config/jailmod/language.txt`

Questo file contiene i messaggi che vengono visualizzati nel gioco, personalizzabili per adattarsi al tono o allo stile del server. Se il file non esiste, viene automaticamente generato con i messaggi predefiniti. Ecco alcuni dei messaggi che puoi modificare:

* **`jail_player`**: Messaggio che il giocatore riceve quando viene imprigionato. Usa le variabili {time} per la durata e {reason} per il motivo.  
  Esempio: `"Sei stato imprigionato per {time} secondi! Motivo: {reason}"`
* **`jail_broadcast`**: Messaggio trasmesso a tutti i giocatori del server quando un giocatore viene imprigionato.  
  Esempio: `"{player} è stato imprigionato per {time} secondi. Motivo: {reason}"`
* **`unjail_player_manual`**: Messaggio che il giocatore riceve quando viene rilasciato manualmente dalla prigione.  
  Esempio: `"Sei stato rilasciato manualmente dalla prigione!"`
* **`unjail_broadcast_manual`**: Messaggio trasmesso a tutti i giocatori del server quando un giocatore viene rilasciato manualmente dalla prigione.  
  Esempio: `"{player} è stato rilasciato manualmente dalla prigione!"`
* **`unjail_player_auto`**: Messaggio che il giocatore riceve quando viene rilasciato automaticamente dalla prigione dopo che il tempo è scaduto.  
  Esempio: `"Sei stato rilasciato dopo aver scontato la tua pena."`
* **`unjail_broadcast_auto`**: Messaggio trasmesso a tutti i giocatori del server quando un giocatore viene rilasciato automaticamente dalla prigione dopo che il tempo è scaduto.  
  Esempio: `"{player} è stato rilasciato dopo aver scontato la sua pena."`
* **`block_interaction_denied`**: Messaggio che informa il giocatore che non può interagire con i blocchi mentre è in prigione.  
  Esempio: `"Non puoi interagire con i blocchi mentre sei in prigione!"`
* **`entity_interaction_denied`**: Messaggio che informa il giocatore che non può interagire con le entità mentre è in prigione.  
  Esempio: `"Non puoi interagire con le entità mentre sei in prigione!"`
* **`bucket_use_denied`**: Messaggio che informa il giocatore che non può usare secchi di lava o acqua mentre è in prigione.  
  Esempio: `"Non puoi usare secchi di lava o acqua mentre sei in prigione!"`
* **`item_use_denied`**: Messaggio che informa il giocatore che non può usare oggetti mentre è in prigione.  
  Esempio: `"Non puoi usare oggetti mentre sei in prigione!"`
* **`block_break_denied`**: Messaggio che informa il giocatore che non può rompere blocchi mentre è in prigione.  
  Esempio: `"Non puoi rompere blocchi mentre sei in prigione!"`
* **`jail_info_message`**: Messaggio che mostra il tempo rimanente e il motivo della pena detentiva quando il giocatore utilizza il comando `/jail info`.  
  Esempio: `"Sei in prigione per altri {time} secondi. Motivo: {reason}."`
* **`not_in_jail_message`**: Messaggio mostrato se un giocatore non è in prigione e cerca di usare `/jail info`.  
  Esempio: `"Non sei in prigione!"`

Esempio di `language.txt` di Default:
```
jail_player=Sei stato imprigionato per {time} secondi! Motivo: {reason}
jail_broadcast={player} è stato imprigionato per {time} secondi. Motivo: {reason}
unjail_player_manual=Sei stato rilasciato manualmente dalla prigione!
unjail_broadcast_manual={player} è stato rilasciato manualmente dalla prigione!
unjail_player_auto=Sei stato rilasciato dopo aver scontato la tua pena.
unjail_broadcast_auto={player} è stato rilasciato dopo aver scontato la sua pena.
block_interaction_denied=Non puoi interagire con i blocchi mentre sei in prigione!
entity_interaction_denied=Non puoi interagire con le entità mentre sei in prigione!
bucket_use_denied=Non puoi usare secchi di lava o acqua mentre sei in prigione!
item_use_denied=Non puoi usare oggetti mentre sei in prigione!
block_break_denied=Non puoi rompere blocchi mentre sei in prigione!
jail_info_message=Sei in prigione per altri {time} secondi. Motivo: {reason}.
not_in_jail_message=Non sei in prigione!
```

### Consigli per l'Uso

Usa il comando `/jail reload` dopo aver modificato la configurazione o i messaggi linguistici per applicare le modifiche senza dover riavviare il server. Specifica sempre un motivo chiaro per l'incarcerazione, in modo che il giocatore sappia perché è stato imprigionato.

Esempi di Utilizzo
------------------

**Imposta la Posizione di Generazione della Prigione:**  
`/jail set 0 60 0`

**Imprigionare un Giocatore per un'Azione Scorretta:**  
`/jail Alex 600 Offesa a un altro giocatore`  
Questo imprigiona Alex per 10 minuti con il motivo "Offesa a un altro giocatore".

**Verifica del Tempo di Detenzione:**  
`/jail info`  
Un giocatore imprigionato può usare questo comando per controllare quanto tempo gli rimane.