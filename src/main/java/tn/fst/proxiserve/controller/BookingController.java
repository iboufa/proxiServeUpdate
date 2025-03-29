package tn.fst.proxiserve.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paypal.api.payments.Payment;
import com.paypal.api.payments.PaymentExecution;
import com.paypal.base.rest.PayPalRESTException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tn.fst.proxiserve.config.PayPalConfig;
import tn.fst.proxiserve.dto.BookingView;
import tn.fst.proxiserve.model.Artisan;
import tn.fst.proxiserve.model.Booking;
import tn.fst.proxiserve.model.Client;
import tn.fst.proxiserve.model.ServiceEntity;
import tn.fst.proxiserve.model.User;
import tn.fst.proxiserve.repository.ArtisanRepository;
import tn.fst.proxiserve.repository.BookingRepository;
import tn.fst.proxiserve.repository.ClientRepository;
import tn.fst.proxiserve.repository.ServiceRepository;
import tn.fst.proxiserve.repository.UserRepository;
import tn.fst.proxiserve.service.MailService;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    private final BookingRepository bookingRepository;
    private final ClientRepository clientRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final ArtisanRepository artisanRepository;
    private final MailService mailService;
    //appContext() appelé d'ou ?
    //temporaire pour éviter l'erreur
    private final PayPalConfig payPalConfig;


    //  Créer une réservation (par un client connecté)
    @PostMapping
    public ResponseEntity<?> createBooking(@Valid @RequestBody Booking bookingRequest,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        logger.info("[POST] /api/bookings called by {}", userDetails.getUsername());

        String email = userDetails.getUsername();

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            logger.warn("Utilisateur non trouvé pour l'email : {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé");
        }

        User user = userOpt.get();

        Optional<Client> clientOpt = clientRepository.findByUserId(user.getId());
        if (clientOpt.isEmpty()) {
            logger.warn("Client non trouvé pour l'userId : {}", user.getId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client non trouvé");
        }

        Optional<ServiceEntity> serviceOpt = serviceRepository.findById(bookingRequest.getServiceId());
        if (serviceOpt.isEmpty()) {
            logger.warn("Service non trouvé avec l'ID : {}", bookingRequest.getServiceId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Service non trouvé");
        }

        bookingRequest.setClientId(clientOpt.get().getId());
        bookingRequest.setCreatedAt(LocalDateTime.now());
        bookingRequest.setStatus("PENDING");

        // Récupérer les infos de l'artisan concerné
        Optional<Artisan> artisanOpt = artisanRepository.findById(bookingRequest.getArtisanId());
        artisanOpt.ifPresent(artisan -> {
            String artisanEmail = artisan.getEmail();
            String message = String.format("""
                    Bonjour %s,

                    Vous avez reçu une nouvelle réservation de la part d'un client.

                    📅 Date : %s
                    🛠️ Service : %s
                    

                    Connectez-vous à votre compte pour confirmer ou rejeter cette réservation.

                    -- 
                    L'équipe Proxiserve
                    """,
                    artisan.getProfession(),
                    bookingRequest.getBookingDate(),
                    bookingRequest.getServiceId()
                    //bookingRequest.getLocation()
            );

            mailService.sendEmail(
                    artisanEmail,
                    "📢 Nouvelle réservation reçue !",
                    message
            );
        });



        Booking saved = bookingRepository.save(bookingRequest);
        logger.info("Réservation créée avec ID : {}", saved.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    //  Récupérer les réservations du client connecté
    @GetMapping("/client")
    public ResponseEntity<?> getBookingsForClient(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String status) {

        logger.info("[GET] /api/bookings/client called by {}", userDetails.getUsername());

        Optional<User> userOpt = userRepository.findByEmail(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé");
        }

        Optional<Client> clientOpt = clientRepository.findByUserId(userOpt.get().getId());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client non trouvé");
        }

        String clientId = clientOpt.get().getId();

        //  Appliquer un filtre par statut si fourni
        List<Booking> bookings = (status != null && !status.isBlank())
                ? bookingRepository.findByClientIdAndStatus(clientId, status.toUpperCase())
                : bookingRepository.findByClientId(clientId);

        List<BookingView> result = bookings.stream().map(booking -> {
            ServiceEntity service = serviceRepository.findById(booking.getServiceId()).orElse(null);

            return new BookingView(
                booking.getId(),
                booking.getStatus(),
                booking.getBookingDate(),
                booking.getCreatedAt(),
                clientOpt.get().getFullName(),       // On a le client en cache
                clientOpt.get().getEmail(),
                service != null ? service.getTitle() : null,
                service != null ? service.getDescription() : null,
                service != null ? service.getPrice() : 0.0
            );
        }).toList();

        return ResponseEntity.ok(result);
    }


   

    //  Récupérer les réservations des services de l'artisan connecté
    @GetMapping("/artisan")
    public ResponseEntity<?> getBookingsForArtisan(@AuthenticationPrincipal UserDetails userDetails) {
        logger.info("[GET] /api/bookings/artisan called by {}", userDetails.getUsername());

        String email = userDetails.getUsername();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé");
        }

        Optional<Artisan> artisanOpt = artisanRepository.findByUserId(userOpt.get().getId());
        if (artisanOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Artisan non trouvé");
        }

        String artisanId = artisanOpt.get().getId(); //  le vrai ID de l'artisan

        List<ServiceEntity> services = serviceRepository.findByArtisanId(artisanId);
        if (services.isEmpty()) {
            logger.info("Aucun service trouvé pour l'artisan {}", artisanId);
            return ResponseEntity.ok(List.of()); // liste vide mais sans erreur
        }

        List<String> serviceIds = services.stream()
                                        .map(ServiceEntity::getId)
                                        .toList();

        List<Booking> bookings = bookingRepository.findByServiceIdIn(serviceIds);
        

        List<BookingView> result = bookings.stream().map(booking -> {
            String clientId = booking.getClientId();
            String serviceId = booking.getServiceId();

            // Récupérer le client
            var client = clientRepository.findById(clientId).orElse(null);
            // Récupérer le service
            var service = serviceRepository.findById(serviceId).orElse(null);

            return new BookingView(
                booking.getId(),
                booking.getStatus(),
                booking.getBookingDate(),
                booking.getCreatedAt(),
                client != null  ? client.getFullName() : null,
                client != null ? client.getEmail() : null,
                service != null ? service.getTitle() : null,
                service != null ? service.getDescription() : null,
                service != null ? service.getPrice() : 0.0
            );
        }).toList();

        return ResponseEntity.ok(result);

    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelBooking(@PathVariable String id,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        logger.info("[DELETE] /api/bookings/{} demandé par {}", id, userDetails.getUsername());

        Optional<User> userOpt = userRepository.findByEmail(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé");
        }

        Optional<Client> clientOpt = clientRepository.findByUserId(userOpt.get().getId());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client non trouvé");
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Réservation non trouvée");
        }

        Booking booking = bookingOpt.get();

        //  Sécurité : vérifier que la réservation appartient bien au client connecté
        if (!booking.getClientId().equals(clientOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Réservation non autorisée");
        }

        booking.setStatus("CANCELLED");
        bookingRepository.save(booking);

        logger.info("Réservation {} annulée avec succès", booking.getId());

        return ResponseEntity.ok("Réservation annulée avec succès");
    }

    //  Confirmer une réservation (par un artisan connecté)
    @PutMapping("/{id}/confirm")
    public ResponseEntity<?> confirmBooking(@PathVariable String id,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        logger.info("[PUT] /api/bookings/{}/confirm demandé par {}", id, userDetails.getUsername());
        System.out.println("[DEBUG] Tentative de confirmation de réservation par : " + userDetails.getUsername());

        Optional<User> userOpt = userRepository.findByEmail(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé");
        }

        Optional<Artisan> artisanOpt = artisanRepository.findByUserId(userOpt.get().getId());
        if (artisanOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Artisan non trouvé");
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Réservation non trouvée");
        }

        Booking booking = bookingOpt.get();

        // Vérifie que la réservation concerne un service appartenant à l'artisan connecté
        Optional<ServiceEntity> serviceOpt = serviceRepository.findById(booking.getServiceId());
        if (serviceOpt.isEmpty() || !serviceOpt.get().getArtisanId().equals(artisanOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Action non autorisée");
        }

        booking.setStatus("CONFIRMED");

        // Notification au client
        clientRepository.findById(booking.getClientId()).ifPresent(client -> {
            String subject = "✅ Votre réservation a été confirmée !";
            String body = String.format("""
                Bonjour %s,

                L'artisan %s a confirmé votre réservation prévue pour le %s.

                Merci pour votre confiance.

                --
                L'équipe Proxiserve
                """,
                client.getFullName(),
                artisanOpt.get().getProfession(),
                booking.getBookingDate()
            );

            mailService.sendEmail(client.getEmail(), subject, body);
        });





        
        bookingRepository.save(booking);

        logger.info("Réservation {} confirmée par l'artisan {}", id, artisanOpt.get().getId());

        return ResponseEntity.ok("Réservation confirmée avec succès");
    }

    //  Refuser une réservation (par un artisan connecté)
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectBooking(@PathVariable String id,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        logger.info("[PUT] /api/bookings/{}/reject demandé par {}", id, userDetails.getUsername());

        Optional<User> userOpt = userRepository.findByEmail(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé");
        }

        Optional<Artisan> artisanOpt = artisanRepository.findByUserId(userOpt.get().getId());
        if (artisanOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Artisan non trouvé");
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Réservation non trouvée");
        }

        Booking booking = bookingOpt.get();

        // Vérifie que la réservation concerne un service appartenant à l'artisan connecté
        Optional<ServiceEntity> serviceOpt = serviceRepository.findById(booking.getServiceId());
        if (serviceOpt.isEmpty() || !serviceOpt.get().getArtisanId().equals(artisanOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Action non autorisée");
        }
        booking.setStatus("REJECTED");

        clientRepository.findById(booking.getClientId()).ifPresent(client -> {
            userRepository.findById(client.getUserId()).ifPresent(user -> {
                String subject = "❌ Réservation rejetée";
                String body = String.format("""
                    Bonjour %s,
    
                    Nous sommes désolés, l'artisan %s a rejeté votre réservation.
    
                    Vous pouvez réserver un autre professionnel via Proxiserve.
    
                    --
                    L'équipe Proxiserve
                    """,
                    client.getFullName(),
                    artisanOpt.get().getProfession()
                );
    
                mailService.sendEmail(user.getEmail(), subject, body);
            });
        });





        
        bookingRepository.save(booking);

        logger.info("Réservation {} rejetée par l'artisan {}", id, artisanOpt.get().getId());

        return ResponseEntity.ok("Réservation rejetée avec succès");
    }

    //  Marquer une réservation comme terminée (par un artisan connecté)
    @PutMapping("/{id}/complete")
    public ResponseEntity<?> completeBooking(@PathVariable String id,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        logger.info("[PUT] /api/bookings/{}/complete demandé par {}", id, userDetails.getUsername());

        Optional<User> userOpt = userRepository.findByEmail(userDetails.getUsername());
        if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé");

        Optional<Artisan> artisanOpt = artisanRepository.findByUserId(userOpt.get().getId());
        if (artisanOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Artisan non trouvé");

        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Réservation non trouvée");

        Booking booking = bookingOpt.get();
        Optional<ServiceEntity> serviceOpt = serviceRepository.findById(booking.getServiceId());
        if (serviceOpt.isEmpty() || !serviceOpt.get().getArtisanId().equals(artisanOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Action non autorisée");
        }

        booking.setStatus("COMPLETED");
        clientRepository.findById(booking.getClientId()).ifPresent(client -> {
            userRepository.findById(client.getUserId()).ifPresent(user -> {
                String subject = "🎉 Réservation terminée avec succès";
                String body = String.format("""
                    Bonjour %s,
        
                    L'artisan %s a indiqué que votre réservation est maintenant terminée.
        
                    Nous espérons que vous êtes satisfait(e) du service.
        
                    N'hésitez pas à laisser un avis ⭐⭐⭐⭐⭐ !
        
                    --
                    L'équipe Proxiserve
                    """,
                    client.getFullName(),
                    artisanOpt.get().getProfession()
                );
        
                mailService.sendEmail(user.getEmail(), subject, body);
            });
        });
        
        bookingRepository.save(booking);

        logger.info("Réservation {} marquée comme terminée par l'artisan {}", id, artisanOpt.get().getId());
        return ResponseEntity.ok("Réservation terminée avec succès");
    }



    @GetMapping("/success")
    public ResponseEntity<?> successPayment(@RequestParam("paymentId") String paymentId,
                                            @RequestParam("PayerID") String payerId,
                                            @RequestParam("bookingId") String bookingId) {
        try {
        	//retrieve it from where : PayPalConfig ??
            Payment payment = Payment.get(payPalConfig.apiContext(), paymentId);
            PaymentExecution paymentExecution = new PaymentExecution();
            paymentExecution.setPayerId(payerId);
            Payment executedPayment = payment.execute(payPalConfig.apiContext(), paymentExecution);

            // 🔄 Mise à jour de la réservation
            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            bookingOpt.ifPresent(booking -> {
                booking.setPaymentStatus("PAID");
                booking.setPaymentMethod("paypal");
                booking.setPaymentCompleted(true);
                bookingRepository.save(booking);
            });

            return ResponseEntity.ok("Paiement effectué avec succès : " + executedPayment.getId());

        } catch (PayPalRESTException e) {
            return ResponseEntity.badRequest().body("Erreur lors de l’exécution du paiement : " + e.getMessage());
        }
    }

    




}
