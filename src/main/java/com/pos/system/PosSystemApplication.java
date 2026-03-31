package com.pos.system;

import com.pos.system.repository.TenantAwareRepositoryFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(repositoryFactoryBeanClass = TenantAwareRepositoryFactoryBean.class)
public class PosSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(PosSystemApplication.class, args);
    }
}
