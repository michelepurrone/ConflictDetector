# Conflict Detector

Progetto realizzato durante il corso "Network Security".

L'idea progettuale consiste in un’applicazione per il controller SDN ONOS volta a rilevare e gestire i conflitti tra regole di flusso.
Ispirandosi al framework Brew, il sistema estende la classificazione dei conflitti tipica dei firewall tradizionali alle regole del controller, automatizzando la risoluzione su più livelli per prevenire vulnerabilità e inefficienze.
L'architettura integra due moduli chiave: Flow Extraction, per l’intercettazione delle regole, e Conflict Detection, per l’analisi della compatibilità. Un elemento distintivo è il meccanismo di trustiness, che assegna punteggi di fiducia dinamici agli utenti; in presenza di anomalie o conflitti ripetuti, il sistema attiva un blocco progressivo tramite blacklist che può culminare nel ban permanente dell’IP. Il software, sviluppato in Java, è stato validato con successo in ambiente Linux tramite l’emulatore Mininet.
