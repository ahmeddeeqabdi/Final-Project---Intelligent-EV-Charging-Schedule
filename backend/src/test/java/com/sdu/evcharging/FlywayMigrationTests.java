package com.sdu.evcharging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flywaydb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class FlywayMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesTheSchemaThatHibernateValidates() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'",
                String.class).stream().map(String::toUpperCase).toList();

        assertThat(tables).contains(
                "APP_USERS",
                "USER_CONSTRAINTS",
                "ENERGY_PRICES",
                "CO2_INTENSITY",
                "SCHEDULE_RESULTS",
                "SCHEDULE_SLOTS",
                "FLYWAY_SCHEMA_HISTORY");
    }
}
