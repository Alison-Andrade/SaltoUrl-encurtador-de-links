package com.alisonsfa.SaltoUrl.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.alisonsfa.SaltoUrl.domain.entity.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest 
@Testcontainers 
@AutoConfigureTestDatabase (replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    // Sobe um contêiner do Postgres com a mesma versão que usamos no docker-compose
    @Container 
    @ServiceConnection 
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Autowired
    private UserRepository userRepository;

    @Test
    void canEstablishConnection() {
        // Apenas verifica se o contêiner subiu e o Spring conseguiu conectar
        assertThat(postgres.isCreated()).isTrue();
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void shouldSaveAndFindUserByEmail() {
        // Arrange (Prepara os dados)
        User user = new User();
        user.setEmail("teste.entrevista@email.com");
        user.setPasswordHash("senha-criptografada");
        // O role e createdAt já têm valores default, mas podemos preencher se necessário
        
        userRepository.save(user);

        // Act (Executa a ação que queremos testar)
        Optional<User> foundUser = userRepository.findByEmail("teste.entrevista@email.com");

        // Assert (Verifica se o resultado é o esperado)
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("teste.entrevista@email.com");
        assertThat(foundUser.get().getId()).isNotNull();
    }
    
    @Test
    void shouldReturnEmptyWhenEmailDoesNotExist() {
        Optional<User> foundUser = userRepository.findByEmail("nao.existe@email.com");
        assertThat(foundUser).isEmpty();
    }
}