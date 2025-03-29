package tn.fst.proxiserve.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import tn.fst.proxiserve.model.User;
import tn.fst.proxiserve.repository.UserRepository;

/**
 * Service personnalisé pour charger les informations de l'utilisateur depuis la base de données.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);
    private final UserRepository userRepository;

    /**
     * Constructeur avec injection du `UserRepository`.
     * @param userRepository Référentiel pour récupérer les utilisateurs.
     */
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Charge un utilisateur par son email pour l'authentification.
     * Vérifie également si le compte est verrouillé.
     * 
     * @param email Email de l'utilisateur.
     * @return Les détails de l'utilisateur sous forme de `UserDetails`.
     * @throws UsernameNotFoundException Si l'utilisateur n'existe pas ou si le compte est verrouillé.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn(" Tentative de connexion avec un email inexistant: {}", email);
                    return new UsernameNotFoundException("Utilisateur non trouve avec l'email : " + email);
                });

        // Vérifier si le compte est verrouillé
        if (!user.isAccountNonLocked()) {
            logger.warn(" Tentative de connexion sur un compte verrouille : {}", email);
            throw new UsernameNotFoundException("Compte verrouille. Veuillez contacter l'administrateur.");
        }

        logger.info(" Utilisateur authentifie avec succes : {}", email);
        return user;
    }
}
