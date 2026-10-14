package com.campus.trade.entity.config;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_config")
@Getter
@Setter
@NoArgsConstructor
public class AppConfig {
    @Id
    @Column(name = "config_key", length = 80)
    private String key;

    @Column(name = "config_value", nullable = false, length = 1000)
    private String value;
}
