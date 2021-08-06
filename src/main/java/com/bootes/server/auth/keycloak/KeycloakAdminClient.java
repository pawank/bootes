package com.bootes.server.auth.keycloak;

import com.bootes.server.auth.ApiLoginRequest;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

public class KeycloakAdminClient {
    Keycloak kc = KeycloakBuilder.builder()
            .serverUrl("http://localhost:8180/auth")
            .realm("rapidor")
            .username(ApiLoginRequest.requestedUsername())
            .password(ApiLoginRequest.requestedPassword())
            .clientId(ApiLoginRequest.clientId())
            .clientSecret(ApiLoginRequest.clientSecret())
            .resteasyClient(
                    new ResteasyClientBuilder()
                            .connectionPoolSize(10).build()
            ).build();

    public void createUser() {
        UserRepresentation user = new UserRepresentation();
        user.setUsername("testuser");
        kc.realm("rapidor").users().create(user);
    }

    public void listUsers() {
        List<UserRepresentation> users = kc.realm("rapidor").users()
                .search("testuser", 0, 10);
        users.forEach(user -> System.out.println(user.getUsername()));
    }
}
