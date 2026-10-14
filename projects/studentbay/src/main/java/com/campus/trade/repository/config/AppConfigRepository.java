package com.campus.trade.repository.config;

import com.campus.trade.entity.config.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfig, String> {
}
