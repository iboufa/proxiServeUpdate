package tn.fst.proxiserve.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tn.fst.proxiserve.model.Client;
import tn.fst.proxiserve.service.ClientService;

/**
 * Contrôleur REST pour gérer les clients.
 * Fournit des endpoints sécurisés pour récupérer et gérer les clients.
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private static final Logger logger = LoggerFactory.getLogger(ClientController.class);
    private final ClientService clientService;



    /**
     * Récupérer tous les clients (accessible uniquement par un administrateur).
     * @return Liste des clients.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<Client>> getAllClients() {
        logger.info(" Récupération de tous les clients...");
        List<Client> clients = clientService.getAllClients();
        
        if (clients.isEmpty()) {
            logger.info(" Aucun client trouvé !");
            return ResponseEntity.noContent().build();
        }

        logger.info(" {} clients récupérés avec succès.", clients.size());
        return ResponseEntity.ok(clients);
    }

    /**
     * Récupérer un client par son ID.
     * @param id ID du client.
     * @return Informations du client.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_CLIENT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Client> getClientById(@PathVariable String id) {
        logger.info(" Récupération du client avec ID : {}", id);
        Client client = clientService.getClientById(id);

        if (client == null) {
            logger.warn(" Client non trouvé avec ID : {}", id);
            return ResponseEntity.notFound().build();
        }

        logger.info(" Client trouvé : {}", client.getFullName());
        return ResponseEntity.ok(client);
    }

    /**
     * Mettre à jour les informations d'un client.
     * @param id ID du client.
     * @param updatedClient Nouvelles informations du client.
     * @return Client mis à jour.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_CLIENT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Client> updateClient(@PathVariable String id, @RequestBody Client updatedClient) {
        if (updatedClient == null) {
            logger.warn(" Tentative de mise à jour avec un client null !");
            return ResponseEntity.badRequest().body(null);
        }

        logger.info(" Mise à jour du client avec ID : {}", id);
        Client client = clientService.updateClient(id, updatedClient);

        if (client == null) {
            logger.warn(" Client non trouvé pour mise à jour - ID : {}", id);
            return ResponseEntity.notFound().build();
        }

        logger.info(" Client mis à jour avec succès : {}", client.getFullName());
        return ResponseEntity.ok(client);
    }

    /**
     * Supprimer un client (réservé aux administrateurs).
     * @param id ID du client.
     * @return Confirmation de suppression.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<String> deleteClient(@PathVariable String id) {
        logger.info(" Tentative de suppression du client avec ID : {}", id);
        boolean deleted = clientService.deleteClient(id);

        if (!deleted) {
            logger.warn(" Échec de suppression : Client non trouvé - ID : {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erreur : Client non trouvé !");
        }

        logger.info(" Client supprimé avec succès - ID : {}", id);
        return ResponseEntity.ok("Client supprimé avec succès !");
    }
}
