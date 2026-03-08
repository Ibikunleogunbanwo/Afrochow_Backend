package com.afrochow.security.Services;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.security.model.CustomUserDetails;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    @Nonnull
    public UserDetails loadUserByUsername(String identifier) {
        User user = findUserByIdentifier(identifier);
        return buildSpringUser(user);
    }


    @Transactional(readOnly = true)
    public UserDetails loadUserByPublicUserId(String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with publicUserId: " + publicUserId
                ));
        return buildSpringUser(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with ID: " + id
                ));
        return buildSpringUser(user);
    }

    // Helper method for flexible identifier lookup
    private User findUserByIdentifier(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmail(identifier)
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "User not found with email: " + identifier
                    ));
        } else {
            return userRepository.findByUsername(identifier)
                    .orElseGet(() -> userRepository.findByUsername(identifier)
                            .orElseThrow(() -> new UsernameNotFoundException(
                                    "User not found with identifier: " + identifier
                            )));
        }
    }

    // --------------------------
    // Helper methods
    // --------------------------
    private UserDetails buildSpringUser(User user) {
        return new CustomUserDetails(
                user,
                getAuthorities(user)
        );
    }

    private List<SimpleGrantedAuthority> getAuthorities(User user) {
        return Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }
    

}